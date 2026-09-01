package com.tapio.core.transfer

import com.tapio.core.common.SharedContent
import com.tapio.core.transfer.domain.Checksum
import com.tapio.core.transfer.domain.ContentHeader

/**
 * A fully-received, checksum-verified item, waiting for the user to accept it.
 *
 * The transfer flow always stops here: nothing is written to the gallery or the
 * address book until the user says yes.
 */
sealed interface IncomingContent {

    /** What the sender said they were sending. */
    val header: ContentHeader

    /**
     * A file sitting in staging. [save] promotes it to a visible location; [discard]
     * drops it.
     */
    class File internal constructor(
        override val header: ContentHeader,
        val verifiedChecksum: Checksum,
        private val staged: StagedFile,
    ) : IncomingContent {
        suspend fun save(): ReceivedFile = staged.persist()

        suspend fun discard() {
            staged.discard()
        }
    }

    /**
     * A contact card. The bytes are already fully in memory and verified; the app
     * hands [card] to the system "add contact" screen if the user accepts.
     */
    class Contact internal constructor(
        override val header: ContentHeader,
        val card: SharedContent.ContactCard,
    ) : IncomingContent
}
