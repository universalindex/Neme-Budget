package com.example.nemebudget.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RegexRuleEngineTest {

    @Test
    fun applies_first_matching_typed_rule_to_category() {
        val result = applyRuleDefinitions(
            rawNotification = "You spent \$23.40 at Chevron ExtraMile on card 1234",
            merchant = "Chevron ExtraMile",
            transaction = ExtractedTransaction(
                merchant = "Chevron ExtraMile",
                amount = 23.40,
                category = "Other",
                isVerified = false,
                verificationNotes = ""
            ),
            rules = listOf(
                com.example.nemebudget.model.RuleDefinition(
                    id = "1",
                    matchField = com.example.nemebudget.model.RuleField.MERCHANT,
                    query = "Shell",
                    targetCategory = "Gas"
                ),
                com.example.nemebudget.model.RuleDefinition(
                    id = "2",
                    matchField = com.example.nemebudget.model.RuleField.MERCHANT,
                    query = "Chevron",
                    targetCategory = "Gas"
                )
            ),
        )

        assertEquals("Gas", result.transaction.category)
        assertEquals("2", result.appliedRule?.id)
    }

    @Test
    fun ignores_non_matching_rules() {
        val result = applyRuleDefinitions(
            rawNotification = "You spent \$12.00 at Starbucks",
            merchant = "Starbucks",
            transaction = ExtractedTransaction(
                merchant = "Starbucks",
                amount = 12.0,
                category = "Dining",
                isVerified = false,
                verificationNotes = ""
            ),
            rules = listOf(
                com.example.nemebudget.model.RuleDefinition(
                    id = "1",
                    matchField = com.example.nemebudget.model.RuleField.RAW_TEXT,
                    query = "Wallet",
                    targetCategory = "Gas"
                )
            ),
        )

        assertEquals("Dining", result.transaction.category)
        assertNull(result.appliedRule)
    }
}




