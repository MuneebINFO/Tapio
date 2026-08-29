package com.tapio.core.nfc.domain

/** Result of a single tap: either a usable [SessionToken] or a typed [HandshakeError]. */
sealed interface HandshakeOutcome {

    data class Success(val token: SessionToken) : HandshakeOutcome

    data class Failure(val error: HandshakeError) : HandshakeOutcome
}
