package com.example.nemebudget.llm

import com.example.nemebudget.model.RuleAction
import com.example.nemebudget.model.RuleDefinition
import com.example.nemebudget.model.RuleField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuleEngineTest {

    @Test
    fun applyRuleDefinitions_setsCategoryWhenCategoryActionMatches() {
        val transaction = ExtractedTransaction(
            merchant = "Starbucks",
            amount = 9.13,
            category = "Other",
            isVerified = false,
            verificationNotes = ""
        )
        val rule = RuleDefinition(
            id = "r1",
            matchField = RuleField.RAW_TEXT,
            query = "kiwi loco",
            targetCategory = "Dining",
            action = RuleAction.SET_CATEGORY,
            forcedMerchant = null
        )

        val result = applyRuleDefinitions(
            rawNotification = "Card Guard... at SQ *KIWI LOCO FROZEN Y",
            merchant = transaction.merchant,
            transaction = transaction,
            rules = listOf(rule)
        )

        assertEquals("Dining", result.transaction.category)
        assertEquals("Starbucks", result.transaction.merchant)
        assertEquals("r1", result.appliedRule?.id)
    }

    @Test
    fun applyRuleDefinitions_setsMerchantWhenMerchantActionMatches() {
        val transaction = ExtractedTransaction(
            merchant = "SQ *KIWI LOCO FROZEN Y",
            amount = 9.13,
            category = "Dining",
            isVerified = false,
            verificationNotes = ""
        )
        val rule = RuleDefinition(
            id = "r2",
            matchField = RuleField.RAW_TEXT,
            query = "card guard",
            targetCategory = "Other",
            action = RuleAction.SET_MERCHANT,
            forcedMerchant = "Kiwi Loco Frozen Yogurt"
        )

        val result = applyRuleDefinitions(
            rawNotification = "America First Alert: Card Guard Credit Card *3320 $9.13 at SQ *KIWI LOCO FROZEN Y",
            merchant = transaction.merchant,
            transaction = transaction,
            rules = listOf(rule)
        )

        assertEquals("Kiwi Loco Frozen Yogurt", result.transaction.merchant)
        assertEquals("Dining", result.transaction.category)
        assertEquals("r2", result.appliedRule?.id)
    }

    @Test
    fun applyRuleDefinitions_returnsOriginalWhenNoRuleMatches() {
        val transaction = ExtractedTransaction(
            merchant = "Amazon",
            amount = 20.0,
            category = "Shopping",
            isVerified = false,
            verificationNotes = ""
        )

        val result = applyRuleDefinitions(
            rawNotification = "Paid 20.00 at Amazon",
            merchant = transaction.merchant,
            transaction = transaction,
            rules = emptyList()
        )

        assertEquals(transaction, result.transaction)
        assertNull(result.appliedRule)
    }
}

