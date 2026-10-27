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
    private const val SYMBOL = """[\$\u20A9\u20AC\u00A3\u00A5\u20B9]"""   // $ ₩ € £ ¥ ₹
    private const val CODE   = """(?:usd|krw|eur|gbp|jpy|inr|cad|aud|chf)"""
    private const val WORD   = """(?:dollars?|euros?|pounds?|won|yen|rupees?)"""
    private const val NUM    = """(?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d{1,2})?"""

    val MONEY_SIGNAL_REGEX = Regex(
        """((?:${SYMBOL}|\b${CODE}(?![a-z]))\s*${NUM}| (?<![\w*])${NUM}\s*(?:${SYMBOL}(?!\s*\d)|${CODE}\b|${WORD}\b)| \b(?:amount|charge|spent|payment|balance)\b\s*(?:of|for|is)?\s*[:=]?\s*${NUM})""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.COMMENTS)
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