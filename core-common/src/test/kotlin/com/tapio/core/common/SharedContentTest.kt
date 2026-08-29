package com.tapio.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class SharedContentTest {

    @Test
    fun `file content reports the FILE kind`() {
        val content: SharedContent = SharedContent.File(
            uri = "content://media/external/images/media/42",
            displayName = "sunset.jpg",
            mimeType = "image/jpeg",
            byteSize = 2_400_000,
        )

        assertEquals(ContentKind.FILE, content.kind)
        assertEquals(2_400_000L, content.byteSize)
    }
}
