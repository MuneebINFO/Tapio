package com.tapio.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

/** The rule: one transfer at a time on this phone, whatever it is and wherever it comes from. */
class ActiveTransferTest {

    private val first = UUID.randomUUID()
    private val second = UUID.randomUUID()

    @Before
    fun setUp() = ActiveTransfer.reset()

    @Test
    fun `an idle phone accepts a transfer`() {
        assertTrue(ActiveTransfer.claim(first))
    }

    @Test
    fun `the same tap delivered twice only starts one transfer`() {
        ActiveTransfer.claim(first)

        assertFalse(ActiveTransfer.claim(first))
    }

    @Test
    fun `another phone is turned away while a transfer runs`() {
        ActiveTransfer.claim(first)

        assertFalse(ActiveTransfer.claim(second))
    }

    @Test
    fun `once the session ends the next one goes through`() {
        ActiveTransfer.claim(first)
        ActiveTransfer.release(first)

        assertTrue(ActiveTransfer.claim(second))
    }

    @Test
    fun `releasing a session that is not the live one changes nothing`() {
        ActiveTransfer.claim(first)
        ActiveTransfer.release(second)

        assertFalse(ActiveTransfer.claim(second))
    }
}
