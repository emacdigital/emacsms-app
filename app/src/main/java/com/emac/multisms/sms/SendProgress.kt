package com.emac.multisms.sms

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SendPhase { IDLE, RUNNING, PAUSED, FINISHED }

data class SendState(
    val phase: SendPhase = SendPhase.IDLE,
    val total: Int = 0,
    val processed: Int = 0,
    val sent: Int = 0,
    val failed: Int = 0,
    val currentName: String = "",
    val statusLabel: String = "Prêt à envoyer",
    val creditsLeft: Int = 0
)

/** État d'envoi partagé entre le service et l'UI. */
object SendProgress {
    private val _state = MutableStateFlow(SendState())
    val state: StateFlow<SendState> = _state.asStateFlow()

    fun set(newState: SendState) { _state.value = newState }
    fun update(block: (SendState) -> SendState) { _state.value = block(_state.value) }
    fun reset() { _state.value = SendState() }
}

/** Drapeaux de contrôle lus par la boucle d'envoi. */
object SendController {
    @Volatile var pauseRequested: Boolean = false
    @Volatile var stopRequested: Boolean = false

    fun resetFlags() {
        pauseRequested = false
        stopRequested = false
    }
}
