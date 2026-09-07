package com.example.nemebudget.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed class BudgetPeriodConfig {
    data class Weekly(val startDayOfWeek: Int) : BudgetPeriodConfig()      // Calendar.SUNDAY..SATURDAY
    data class Biweekly(val anchorMillis: Long) : BudgetPeriodConfig()
    object Monthly : BudgetPeriodConfig()
    object SemiMonthly : BudgetPeriodConfig()                              // 1st–14th, 15th–end
}

data class BudgetPeriod(
    val config: BudgetPeriodConfig,
    val startMillis: Long,
    val endMillis: Long
) {
    fun label(): String {
        return when (config) {
            is BudgetPeriodConfig.Monthly ->
                SimpleDateFormat("MMMM yyyy", Locale.US).format(Date(startMillis))
            else -> {
                val fmt = SimpleDateFormat("MMM d", Locale.US)
                "${fmt.format(Date(startMillis))} – ${fmt.format(Date(endMillis))}"
            }
        }
    }
}

private fun Calendar.setStartOfDay() {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}

private fun Calendar.setEndOfDay() {
    set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
    set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
}

/** Computes the period of the given config that contains referenceMillis. */
fun periodContaining(config: BudgetPeriodConfig, referenceMillis: Long): BudgetPeriod {
    val cal = Calendar.getInstance().apply { timeInMillis = referenceMillis }

    return when (config) {
        is BudgetPeriodConfig.Monthly -> {
            cal.set(Calendar.DAY_OF_MONTH, 1); cal.setStartOfDay()
            val start = cal.timeInMillis
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH)); cal.setEndOfDay()
            BudgetPeriod(config, start, cal.timeInMillis)
        }

        is BudgetPeriodConfig.SemiMonthly -> {
            val day = cal.get(Calendar.DAY_OF_MONTH)
            if (day < 15) {
                cal.set(Calendar.DAY_OF_MONTH, 1); cal.setStartOfDay()
                val start = cal.timeInMillis
                cal.set(Calendar.DAY_OF_MONTH, 14); cal.setEndOfDay()
                BudgetPeriod(config, start, cal.timeInMillis)
            } else {
                cal.set(Calendar.DAY_OF_MONTH, 15); cal.setStartOfDay()
                val start = cal.timeInMillis
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH)); cal.setEndOfDay()
                BudgetPeriod(config, start, cal.timeInMillis)
            }
        }

        is BudgetPeriodConfig.Weekly -> {
            while (cal.get(Calendar.DAY_OF_WEEK) != config.startDayOfWeek) {
                cal.add(Calendar.DAY_OF_MONTH, -1)
            }
            cal.setStartOfDay()
            val start = cal.timeInMillis
            cal.add(Calendar.DAY_OF_MONTH, 6); cal.setEndOfDay()
            BudgetPeriod(config, start, cal.timeInMillis)
        }

        is BudgetPeriodConfig.Biweekly -> {
            val anchor = Calendar.getInstance().apply {
                timeInMillis = config.anchorMillis
                setStartOfDay()
            }
            val daysSinceAnchor = ((cal.timeInMillis - anchor.timeInMillis) / 86_400_000L).toInt()
            val periodsElapsed = Math.floorDiv(daysSinceAnchor, 14)
            anchor.add(Calendar.DAY_OF_MONTH, periodsElapsed * 14)
            val start = anchor.timeInMillis
            anchor.add(Calendar.DAY_OF_MONTH, 13); anchor.setEndOfDay()
            BudgetPeriod(config, start, anchor.timeInMillis)
        }
    }
}

fun BudgetPeriod.next(): BudgetPeriod = periodContaining(config, endMillis + 1000)
fun BudgetPeriod.previous(): BudgetPeriod = periodContaining(config, startMillis - 1000)