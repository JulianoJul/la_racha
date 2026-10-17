package com.laracha.app.domain

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/** Resolves calendar days in the device's current local time zone, never from UTC dates. */
object LocalDay {
    fun today(clock: Clock, zoneId: ZoneId): LocalDate = LocalDate.now(clock.withZone(zoneId))
}
