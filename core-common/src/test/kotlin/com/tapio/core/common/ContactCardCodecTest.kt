package com.tapio.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ContactCardCodecTest {

    @Test
    fun `round-trips a full card, separators and unicode included`() {
        val card = SharedContent.ContactCard(
            displayName = "Jean | Dupont ☎",
            phoneNumber = "+33 6 12 34 56 78",
            organization = "Café des Amis",
        )

        assertEquals(card, ContactCardCodec.decode(ContactCardCodec.encode(card)))
    }

    @Test
    fun `round-trips a card with no organization`() {
        val card = SharedContent.ContactCard("Marie", "0601020304")

        val decoded = ContactCardCodec.decode(ContactCardCodec.encode(card))

        assertEquals("Marie", decoded.displayName)
        assertEquals("0601020304", decoded.phoneNumber)
        assertNull(decoded.organization)
    }

    @Test
    fun `rejects a malformed payload`() {
        assertThrows(IllegalArgumentException::class.java) {
            ContactCardCodec.decode("NOPE|a|b|c".toByteArray())
        }
    }

    @Test
    fun `rejects the wrong field count`() {
        assertThrows(IllegalArgumentException::class.java) {
            ContactCardCodec.decode("TCARD1|only|three".toByteArray())
        }
    }
}
