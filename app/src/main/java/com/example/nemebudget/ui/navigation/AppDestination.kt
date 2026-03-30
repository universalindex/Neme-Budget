package com.example.nemebudget.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class AppDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object Dashboard : AppDestination("dashboard", "Dashboard", Icons.Filled.Home)
    data object Transactions : AppDestination("transactions", "Transactions", Icons.AutoMirrored.Filled.List)
    data object Budgets : AppDestination("budgets", "Budgets", Icons.Filled.AccountCircle)
    data object Settings : AppDestination("settings", "Settings", Icons.Outlined.Settings)
    data object Lab : AppDestination("lab", "LLM Lab", Icons.Outlined.Settings)
}

val bottomDestinations = listOf(
    AppDestination.Dashboard,
    AppDestination.Transactions,
    AppDestination.Budgets,
    AppDestination.Settings
)


