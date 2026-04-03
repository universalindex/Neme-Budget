package com.example.nemebudget.pipeline

import java.util.Locale

/**
 * Shared deterministic gate for deciding whether a notification looks transactional
 * enough to send to the LLM extractor.
 */
object TransactionalNotificationGate {

    data class Decision(val passed: Boolean, val reason: String)

    fun evaluate(title: String, text: String): Decision {
        val combined = "$title $text".lowercase(Locale.US)
        if (combined.isBlank()) return Decision(false, "blank_notification")

        if (!MONEY_SIGNAL_REGEX.containsMatchIn(combined)) {
            return Decision(false, "no_money_signal")
        }

        if (TRANSACTION_ACTION_WORDS.none { combined.contains(it) }) {
            return Decision(false, "no_transaction_action_signal")
        }

        val looksConversational = CONVERSATIONAL_MARKERS.any { combined.contains(it) }
        val hasBankContext = BANK_CONTEXT_WORDS.any { combined.contains(it) }
        if (looksConversational && !hasBankContext) {
            return Decision(false, "conversation_or_email_without_bank_context")
        }

        return Decision(true, "passed")
    }

    private val MONEY_SIGNAL_REGEX = Regex(
        """((\$|usd\s?)\s?(?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d{1,2})?|(?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d{1,2})?\s?(?:\$|usd|dollars?)|(?:amount|charge|spent|payment|balance)\s?[:=]?\s?(?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d{1,2})?)"""
    )

    private val TRANSACTION_ACTION_WORDS = setOf(
        "spent", "purchase", "purchased", "charged", "charge", "paid", "payment",
        "debit", "credit", "withdrawal", "withdrawn", "deposit", "deposited",
        "received", "sent", "transfer", "transferred", "refund", "autopay"
    )

    private val BANK_CONTEXT_WORDS = setOf(
        "card", "account", "bank", "balance", "available", "ending in", "ach", "visa", "mastercard"
    )

    private val CONVERSATIONAL_MARKERS = setOf(
        "re:", "fw:", "wrote:", "sent from my iphone", "hey", "class", "meeting", "assignment", "homework"
    )
}

