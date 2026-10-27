package com.example.nemebudget.pipeline

import java.util.Locale

object TransactionalNotificationGate {

    data class Decision(val passed: Boolean, val reason: String)

    fun evaluate(title: String, text: String): Decision {
        val combined = "$title $text".lowercase(Locale.US)
        if (combined.isBlank()) return Decision(false, "blank_notification")

        if (!MONEY_SIGNAL_REGEX.containsMatchIn(combined)) {
            return Decision(false, "no_money_signal")
        }

        if (!ACTION_REGEX.containsMatchIn(combined)) {
            return Decision(false, "no_transaction_action_signal")
        }

        val looksConversational = CONVERSATIONAL_REGEX.containsMatchIn(combined)
        val hasBankContext = BANK_CONTEXT_REGEX.containsMatchIn(combined)
        if (looksConversational && !hasBankContext) {
            return Decision(false, "conversation_or_email_without_bank_context")
        }

        return Decision(true, "passed")
    }

    private val MONEY_SIGNAL_REGEX = Regex(
        """(?i)((?:\$|\busd)\s*(?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d{1,2})?|(?<![\w*])(?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d{1,2})?\s*(?:\$(?!\s*\d)|usd\b|dollars?\b)|\b(?:amount|charge|spent|payment|balance)\b\s*(?:of|for|is)?\s*[:=]?\s*(?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d{1,2})?)"""
    )

    // 1) The word lists come first...
    private val TRANSACTION_ACTION_WORDS = setOf(
        "spent", "purchase", "purchased", "charged", "charge", "paid", "payment",
        "debit", "credit", "withdrawal", "withdrawn", "deposit", "deposited",
        "received", "sent", "transfer", "transferred", "refund", "autopay"
        // "card" removed: it matched "card balance" alerts. Add it back if real alerts get dropped.
    )

    private val BANK_CONTEXT_WORDS = setOf(
        "card", "account", "bank", "balance", "available", "ending in", "ach", "visa",
        "mastercard", "america first", "chase", "wells fargo", "american express",
        "discover", "bank of america"
    )

    private val CONVERSATIONAL_MARKERS = setOf(
        "re:", "fw:", "wrote:", "sent from my iphone", "hey", "class", "meeting",
        "assignment", "homework", "deal", "off"
    )

    // 2) ...then the helper and the regexes built from them
    private fun wordsRegex(words: Set<String>): Regex {
        val alternation = words.joinToString("|") { Regex.escape(it) }
        return Regex("""(?<!\w)(?:$alternation)(?!\w)""")
    }

    private val ACTION_REGEX = wordsRegex(TRANSACTION_ACTION_WORDS)
    private val BANK_CONTEXT_REGEX = wordsRegex(BANK_CONTEXT_WORDS)
    private val CONVERSATIONAL_REGEX = wordsRegex(CONVERSATIONAL_MARKERS)
}