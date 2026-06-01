package br.com.redesurftank.havalshisuku.managers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayAppLauncherUsbStateTest {
    @Test
    fun disconnectedStateIsNotReady() {
        assertFalse(DisplayAppLauncher.isProjectionUsbStateReady("DISCONNECTED"))
    }

    @Test
    fun configuredStateIsReady() {
        assertTrue(DisplayAppLauncher.isProjectionUsbStateReady("CONFIGURED"))
    }

    @Test
    fun connectedStateIsReady() {
        assertTrue(DisplayAppLauncher.isProjectionUsbStateReady("CONNECTED"))
    }

    @Test
    fun multilineOutputOnlyAcceptsExactReadyStates() {
        assertFalse(
            DisplayAppLauncher.isProjectionUsbStateReady(
                """
                command echo
                DISCONNECTED
                """.trimIndent()
            )
        )
        assertTrue(
            DisplayAppLauncher.isProjectionUsbStateReady(
                """
                command echo
                CONFIGURED
                """.trimIndent()
            )
        )
    }
}
