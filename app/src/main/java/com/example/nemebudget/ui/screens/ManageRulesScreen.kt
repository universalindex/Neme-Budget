package com.example.nemebudget.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nemebudget.model.CategoryDefinition
import com.example.nemebudget.model.RuleDefinition
import com.example.nemebudget.model.RuleField
import com.example.nemebudget.viewmodel.SettingsViewModel
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageRulesScreen(
	viewModel: SettingsViewModel,
	categoryOptions: List<CategoryDefinition>,
	onBack: () -> Unit
) {
	val settings by viewModel.settings.collectAsStateWithLifecycle()
	var searchQuery by remember { mutableStateOf("") }
	var showAddSheet by remember { mutableStateOf(false) }
	var editingRule by remember { mutableStateOf<RuleDefinition?>(null) }

	val filteredRules = remember(settings.customRules, searchQuery) {
		val q = searchQuery.trim()
		if (q.isBlank()) settings.customRules else settings.customRules.filter { rule ->
			rule.displayLabel().contains(q, ignoreCase = true) ||
				rule.query.contains(q, ignoreCase = true) ||
				rule.targetCategory.contains(q, ignoreCase = true)
		}
	}

	Scaffold(
		floatingActionButton = {
			FloatingActionButton(onClick = { showAddSheet = true }) {
				Icon(Icons.Default.Add, contentDescription = "Add rule")
			}
		}
	) { innerPadding ->
		LazyColumn(
			modifier = Modifier
				.fillMaxSize()
				.padding(innerPadding)
				.padding(16.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp)
		) {
			item {
				Row(verticalAlignment = Alignment.CenterVertically) {
					TextButton(onClick = onBack) {
						Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
						Spacer(modifier = Modifier.padding(horizontal = 4.dp))
						Text("Back")
					}
				}
			}

			item { Text("Manage Rules", style = MaterialTheme.typography.headlineSmall) }
			item { Text("Rules are typed: pick a field, enter text to match, then choose the category.", style = MaterialTheme.typography.bodySmall) }

			item {
				OutlinedTextField(
					value = searchQuery,
					onValueChange = { searchQuery = it },
					modifier = Modifier.fillMaxWidth(),
					label = { Text("Search rules") }
				)
			}

			items(filteredRules, key = { it.id }) { rule ->
				Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.padding(12.dp),
						verticalAlignment = Alignment.CenterVertically
					) {
						Column(modifier = Modifier.weight(1f)) {
							Text(rule.displayLabel(), style = MaterialTheme.typography.titleMedium)
							Text("Matches ${rule.matchField.label.lowercase()} containing \"${rule.query}\"", style = MaterialTheme.typography.bodySmall)
						}
						TextButton(onClick = { editingRule = rule }) { Text("Edit") }
						IconButton(onClick = { viewModel.removeCustomRule(rule) }) {
							Icon(Icons.Default.Delete, contentDescription = "Delete rule")
						}
					}
				}
			}
		}
	}

	if (showAddSheet) {
		AddRuleSheet(
			categoryOptions = categoryOptions,
			onDismiss = { showAddSheet = false },
			onCreate = { rule ->
				viewModel.addCustomRule(rule)
				showAddSheet = false
			}
		)
	}

	editingRule?.let { current ->
		AddRuleSheet(
			categoryOptions = categoryOptions,
			initialRule = current,
			onDismiss = { editingRule = null },
			onCreate = { updated ->
				viewModel.removeCustomRule(current)
				viewModel.addCustomRule(updated)
				editingRule = null
			}
		)
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRuleSheet(
	categoryOptions: List<CategoryDefinition>,
	initialRule: RuleDefinition? = null,
	onDismiss: () -> Unit,
	onCreate: (RuleDefinition) -> Unit
) {
	val focusManager = LocalFocusManager.current
	var matchField by remember(initialRule) { mutableStateOf(initialRule?.matchField ?: RuleField.MERCHANT) }
	var query by remember(initialRule) { mutableStateOf(initialRule?.query.orEmpty()) }
	var selectedCategory by remember(initialRule) { mutableStateOf(initialRule?.targetCategory ?: categoryOptions.firstOrNull()?.label.orEmpty()) }

	ModalBottomSheet(
		onDismissRequest = onDismiss,
		sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(24.dp),
			verticalArrangement = Arrangement.spacedBy(16.dp)
		) {
			Text(if (initialRule == null) "Add Rule" else "Edit Rule", style = MaterialTheme.typography.titleLarge)
			Text("Choose the field to match, enter the text, then pick the category.", style = MaterialTheme.typography.bodySmall)

			Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
				Text("Match field", style = MaterialTheme.typography.labelLarge)
				Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					RuleField.entries.forEach { field ->
						InputChip(
							selected = matchField == field,
							onClick = { matchField = field },
							label = { Text(field.label) }
						)
					}
				}
			}

			OutlinedTextField(
				value = query,
				onValueChange = { query = it },
				modifier = Modifier.fillMaxWidth(),
				label = { Text("Match text") },
				keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
				keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
			)

			Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
				Text("Category", style = MaterialTheme.typography.labelLarge)
				categoryOptions.chunked(2).forEach { rowCategories ->
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
						rowCategories.forEach { category ->
							InputChip(
								selected = selectedCategory == category.label,
								onClick = { selectedCategory = category.label },
								label = { Text(category.label) }
							)
						}
					}
				}
			}

			Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
				TextButton(
					onClick = {
						onCreate(
							RuleDefinition(
								id = initialRule?.id ?: "rule_${System.currentTimeMillis()}_${UUID.randomUUID()}",
								matchField = matchField,
								query = query.trim(),
								targetCategory = selectedCategory
							)
						)
					},
					enabled = query.trim().isNotBlank() && selectedCategory.isNotBlank(),
					modifier = Modifier.weight(1f)
				) { Text("Save") }
			}
		}
	}
}






