package com.tapio.core.transfer

import com.tapio.core.transfer.domain.Checksum
import com.tapio.core.transfer.domain.FileHeader

/**
 * A fully-received, checksum-verified file sitting in staging, waiting for the
 * user to accept or reject it via the "Save this file?" dialog.
 *
 * @property header what the sender said it was sending.
 * @property verifiedChecksum the SHA-256 that both devices agreed on.
 */
class IncomingFile internal constructor(
    val header: FileHeader,
    val verifiedChecksum: Checksum,
    private val staged: StagedFile,
) {
    /** User tapped "Save": move the file to a permanent, visible location. */
    suspend fun save(): ReceivedFile = staged.persist()

    /** User tapped "Decline": drop the staged file. */
    suspend fun discard() {
        staged.discard()
    }
}
