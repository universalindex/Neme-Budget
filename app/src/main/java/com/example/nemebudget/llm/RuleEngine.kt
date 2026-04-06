package com.example.nemebudget.llm

import com.example.nemebudget.model.RuleDefinition
import com.example.nemebudget.model.RuleField

internal data class RuleApplicationResult(
    val transaction: ExtractedTransaction,
    val appliedRule: RuleDefinition? = null
)

internal fun applyRuleDefinitions(
    rawNotification: String,
    merchant: String,
    transaction: ExtractedTransaction,
    rules: List<RuleDefinition>
): RuleApplicationResult {
    val match = rules.firstOrNull { rule ->
        ruleMatches(rule, rawNotification, merchant)
    } ?: return RuleApplicationResult(transaction)

    return RuleApplicationResult(
        transaction = transaction.copy(category = match.targetCategory),
        appliedRule = match
    )
}

private fun ruleMatches(rule: RuleDefinition, rawNotification: String, merchant: String): Boolean {
    val target = when (rule.matchField) {
        RuleField.MERCHANT -> merchant
        RuleField.RAW_TEXT -> rawNotification
    }
    return target.contains(rule.query, ignoreCase = true)
}



