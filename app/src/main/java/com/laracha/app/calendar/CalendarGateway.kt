package com.laracha.app.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.laracha.app.data.CalendarEventBindingEntity
import com.laracha.app.data.HabitCalendarScheduleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.LocalTime

data class WritableCalendar(
    val id: Long,
    val name: String,
    val accountName: String,
    val accountType: String,
    val color: Int,
    val supportsAlertReminders: Boolean,
)

data class ManagedSeries(val eventId: Long, val token: String)

sealed interface GatewayResult {
    data class Published(val series: ManagedSeries) : GatewayResult
    data object Missing : GatewayResult
    data object Ambiguous : GatewayResult
}

class CalendarUnavailableException(message: String) : IllegalStateException(message)

interface CalendarGateway {
    suspend fun listWritableGoogleCalendars(): List<WritableCalendar>
    suspend fun publish(calendarId: Long, habitName: String, schedule: HabitCalendarScheduleEntity, token: String, binding: CalendarEventBindingEntity?): GatewayResult
    suspend fun delete(calendarId: Long, eventId: Long?, token: String, binding: CalendarEventBindingEntity?): GatewayResult
    suspend fun reconcile(calendarId: Long, token: String, binding: CalendarEventBindingEntity?): GatewayResult
}

/** Direct Calendar Provider access. It never reads event titles/hours to infer ownership. */
class AndroidCalendarGateway(private val context: Context) : CalendarGateway {
    private val resolver get() = context.contentResolver

    private fun requireRead() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) throw SecurityException("Sin permiso de lectura de calendario")
    }
    private fun requireWrite() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) throw SecurityException("Sin permiso de escritura de calendario")
    }

    override suspend fun listWritableGoogleCalendars(): List<WritableCalendar> = withContext(Dispatchers.IO) {
        requireRead()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.SYNC_EVENTS,
            CalendarContract.Calendars.VISIBLE,
            CalendarContract.Calendars.ALLOWED_REMINDERS,
            CalendarContract.Calendars.MAX_REMINDERS,
        )
        (resolver.query(CalendarContract.Calendars.CONTENT_URI, projection, "${CalendarContract.Calendars.ACCOUNT_TYPE} = ? AND ${CalendarContract.Calendars.SYNC_EVENTS} = 1", arrayOf("com.google"), "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} COLLATE NOCASE")
            ?: throw CalendarUnavailableException("No se pudo leer la lista de calendarios")).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val access = cursor.getInt(5)
                    if (access >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR && cursor.getInt(7) == 1) {
                        val allowedMethods = cursor.getString(8).orEmpty().split(',').mapNotNull { it.toIntOrNull() }
                        add(WritableCalendar(cursor.getLong(0), cursor.getString(1).orEmpty(), cursor.getString(2).orEmpty(), "com.google", cursor.getInt(4), cursor.getInt(9) > 0 && CalendarContract.Reminders.METHOD_ALERT in allowedMethods))
                    }
                }
            }
        }
    }

    override suspend fun publish(calendarId: Long, habitName: String, schedule: HabitCalendarScheduleEntity, token: String, binding: CalendarEventBindingEntity?): GatewayResult = withContext(Dispatchers.IO) {
        requireRead(); requireWrite()
        ensureTrustedCalendar(calendarId, binding)
        if (schedule.reminderMinutes >= 0) {
            val destination = listWritableGoogleCalendars().firstOrNull { it.id == calendarId }
            check(destination?.supportsAlertReminders == true) { "Este calendario no admite recordatorios de alerta" }
        }
        val event = findOwnedEvent(calendarId, token)
        if (event == GatewayResult.Ambiguous) return@withContext event
        val oldId = (event as? GatewayResult.Published)?.series?.eventId
            ?: binding?.eventId?.takeIf { verifyOwned(calendarId, it, token) }
        if (binding?.eventId != null && oldId == null && event == GatewayResult.Missing) return@withContext GatewayResult.Missing

        val start = CalendarOccurrenceTime.resolveStart(
            LocalDate.parse(schedule.startDate),
            LocalTime.parse(schedule.localTime),
            ZoneId.of(schedule.zoneId),
        ).toInstant().toEpochMilli()
        val marker = marker(token)
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, habitName)
            put(CalendarContract.Events.DESCRIPTION, "Planificación diaria de La Racha.\n$marker")
            put(CalendarContract.Events.DTSTART, start)
            put(CalendarContract.Events.EVENT_TIMEZONE, schedule.zoneId)
            put(CalendarContract.Events.DURATION, "PT${schedule.durationMinutes}M")
            put(CalendarContract.Events.RRULE, "FREQ=DAILY")
            put(CalendarContract.Events.ALL_DAY, 0)
            when {
                schedule.reminderMinutes >= 0 -> put(CalendarContract.Events.HAS_ALARM, 1)
                binding?.managedReminderMinutes != null -> put(CalendarContract.Events.HAS_ALARM, 0)
            }
        }
        val eventId = if (oldId != null) {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, oldId)
            if (resolver.update(uri, values, null, null) != 1) return@withContext GatewayResult.Missing
            oldId
        } else {
            val inserted = resolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: error("El proveedor no devolvió URI al crear el evento")
            inserted.lastPathSegment?.toLongOrNull() ?: error("URI del evento inválida")
        }
        replaceReminder(eventId, binding?.managedReminderMinutes, schedule.reminderMinutes)
        GatewayResult.Published(ManagedSeries(eventId, token))
    }

    override suspend fun delete(calendarId: Long, eventId: Long?, token: String, binding: CalendarEventBindingEntity?): GatewayResult = withContext(Dispatchers.IO) {
        requireRead(); requireWrite()
        ensureTrustedCalendar(calendarId, binding)
        val found = findOwnedEvent(calendarId, token)
        if (found == GatewayResult.Ambiguous) return@withContext found
        val verifiedId = (found as? GatewayResult.Published)?.series?.eventId
            ?: eventId?.takeIf { verifyOwned(calendarId, it, token) }
            ?: return@withContext GatewayResult.Missing
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, verifiedId)
        val removed = resolver.delete(uri, null, null)
        if (removed == 1) GatewayResult.Published(ManagedSeries(verifiedId, token)) else GatewayResult.Missing
    }

    override suspend fun reconcile(calendarId: Long, token: String, binding: CalendarEventBindingEntity?): GatewayResult = withContext(Dispatchers.IO) {
        requireRead()
        ensureTrustedCalendar(calendarId, binding)
        findOwnedEvent(calendarId, token)
    }

    private fun replaceReminder(eventId: Long, previouslyManagedMinutes: Int?, minutes: Int) {
        val existing = (resolver.query(CalendarContract.Reminders.CONTENT_URI, arrayOf(CalendarContract.Reminders._ID, CalendarContract.Reminders.MINUTES, CalendarContract.Reminders.METHOD), "${CalendarContract.Reminders.EVENT_ID} = ?", arrayOf(eventId.toString()), null)
            ?: throw CalendarUnavailableException("No se pudieron leer los recordatorios")).use { cursor ->
            buildList { while (cursor.moveToNext()) add(ReminderRow(cursor.getLong(0), cursor.getInt(1), cursor.getInt(2))) }
        }
        val change = reminderChange(existing, previouslyManagedMinutes, minutes, CalendarContract.Reminders.METHOD_ALERT)
        change.deleteIds.forEach { id -> resolver.delete(ContentUris.withAppendedId(CalendarContract.Reminders.CONTENT_URI, id), null, null) }
        if (change.insertMinutes != null) {
            val values = ContentValues().apply { put(CalendarContract.Reminders.EVENT_ID, eventId); put(CalendarContract.Reminders.MINUTES, change.insertMinutes); put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT) }
            resolver.insert(CalendarContract.Reminders.CONTENT_URI, values) ?: error("El proveedor rechazó el recordatorio")
        }
    }

    private fun findOwnedEvent(calendarId: Long, token: String): GatewayResult {
        val projection = arrayOf(CalendarContract.Events._ID, CalendarContract.Events.DESCRIPTION, CalendarContract.Events.CALENDAR_ID)
        val hits = (resolver.query(CalendarContract.Events.CONTENT_URI, projection, "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DESCRIPTION} LIKE ? AND ${CalendarContract.Events.DELETED} = 0", arrayOf(calendarId.toString(), "%${marker(token)}%"), null)
            ?: throw CalendarUnavailableException("No se pudo comprobar la serie en Calendar")).use { cursor ->
            buildList { while (cursor.moveToNext()) if (cursor.getString(1).orEmpty().contains(marker(token))) add(cursor.getLong(0)) }
        }
        return when (hits.size) { 0 -> GatewayResult.Missing; 1 -> GatewayResult.Published(ManagedSeries(hits.single(), token)); else -> GatewayResult.Ambiguous }
    }

    private fun verifyOwned(calendarId: Long, eventId: Long, token: String): Boolean {
        val projection = arrayOf(CalendarContract.Events._ID, CalendarContract.Events.CALENDAR_ID, CalendarContract.Events.DESCRIPTION, CalendarContract.Events.DELETED)
        return (resolver.query(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), projection, null, null, null)
            ?: throw CalendarUnavailableException("No se pudo verificar la identidad de la serie")).use { cursor ->
            cursor.moveToFirst() && cursor.getLong(1) == calendarId && cursor.getInt(3) == 0 && cursor.getString(2).orEmpty().contains(marker(token))
        }
    }

    private fun marker(token: String) = "[LaRacha:$token]"

    private fun ensureTrustedCalendar(calendarId: Long, binding: CalendarEventBindingEntity?) {
        val projection = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.SYNC_EVENTS)
        val trusted = (resolver.query(ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendarId), projection, null, null, null)
            ?: throw CalendarUnavailableException("No se pudo verificar el calendario de destino")).use { cursor ->
            cursor.moveToFirst() && cursor.getString(2) == "com.google" && cursor.getInt(3) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR &&
                cursor.getInt(4) == 1 && (binding?.accountName.isNullOrEmpty() || cursor.getString(1) == binding?.accountName) &&
                (binding?.accountType.isNullOrEmpty() || cursor.getString(2) == binding?.accountType)
        }
        if (!trusted) throw CalendarUnavailableException("El calendario Google seleccionado ya no está disponible o no permite escribir")
    }

}
