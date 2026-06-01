package br.com.redesurftank.havalshisuku.managers

import org.junit.Assert.assertEquals
import org.junit.Test

class CarPlayDisplayOrchestratorTest {
    @Test
    fun disconnectedWhenNoVisualTaskExists() {
        assertEquals(
            CarPlayDisplayState.DISCONNECTED,
            CarPlayDisplayOrchestrator.resolveObservedState(
                carPlayOnD0 = false,
                carPlayOnD3 = false
            )
        )
    }

    @Test
    fun connectedOnD0WhenOnlyMainDisplayHasCarPlay() {
        assertEquals(
            CarPlayDisplayState.CONNECTED_ON_D0,
            CarPlayDisplayOrchestrator.resolveObservedState(
                carPlayOnD0 = true,
                carPlayOnD3 = false
            )
        )
    }

    @Test
    fun mirroredOnD3TakesPriorityOverDuplicateD0Task() {
        assertEquals(
            CarPlayDisplayState.MIRRORED_ON_D3,
            CarPlayDisplayOrchestrator.resolveObservedState(
                carPlayOnD0 = true,
                carPlayOnD3 = true
            )
        )
    }
}
