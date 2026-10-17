package com.laracha.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppPreferencesTest {
    @Test
    fun unknownOrMissingThemePreferenceFallsBackToSystem() {
        assertEquals(ThemeMode.SYSTEM, themeModeFromStoredValue(null))
        assertEquals(ThemeMode.SYSTEM, themeModeFromStoredValue("unknown"))
    }

    @Test
    fun storedThemeModesRoundTripToEachSupportedChoice() {
        ThemeMode.entries.forEach { mode ->
            assertEquals(mode, themeModeFromStoredValue(mode.storedValue))
        }
    }
}
