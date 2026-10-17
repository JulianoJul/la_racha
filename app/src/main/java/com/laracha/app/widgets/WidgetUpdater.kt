package com.laracha.app.widgets

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CancellationException

object WidgetUpdater {
    suspend fun updateAll(context: Context) {
        try {
            SingleHabitWidget().updateAll(context)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w("WidgetUpdater", "No se pudieron actualizar los widgets individuales", error)
        }

        try {
            HabitListWidget().updateAll(context)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w("WidgetUpdater", "No se pudieron actualizar los widgets de lista", error)
        }
    }
}
