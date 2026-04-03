package com.example.nemebudget

import com.example.nemebudget.pipeline.TransactionalNotificationGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionalNotificationGateTest {

    @Test
    fun passes_for_typical_transaction_alert() {
        val decision = TransactionalNotificationGate.evaluate(
            title = "Transaction Alert",
            text = "You spent $45.99 at Starbucks with your card ending in 1234"
        )

        assertTrue(decision.passed)
        assertEquals("passed", decision.reason)
    }

    @Test
    fun blocks_when_no_money_signal() {
        val decision = TransactionalNotificationGate.evaluate(
            title = "Card Alert",
            text = "Your card was charged at Starbucks"
        )

        assertFalse(decision.passed)
        assertEquals("no_money_signal", decision.reason)
    }

    @Test
    fun blocks_conversation_without_bank_context() {
        val decision = TransactionalNotificationGate.evaluate(
            title = "Re: Class cancellation",
            text = "Hey can you pay me $5? Sent from my iPhone"
        )

        assertFalse(decision.passed)
        assertEquals("conversation_or_email_without_bank_context", decision.reason)
    }

    @Test
    fun passes_for_keyword_amount_pattern_without_currency_symbol() {
        val decision = TransactionalNotificationGate.evaluate(
            title = "Payment Update",
            text = "payment amount: 245.75 posted to your account"
        )

        assertTrue(decision.passed)
        assertEquals("passed", decision.reason)
    }

    @Test
    fun passes_for_suffix_currency_word_pattern() {
        val decision = TransactionalNotificationGate.evaluate(
            title = "Card Alert",
            text = "Your card was charged 1,234.50 dollars at Hilton"
        )

        assertTrue(decision.passed)
        assertEquals("passed", decision.reason)
    }
}

