package com.example.bookswap.ui.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.bookswap.R
import com.example.bookswap.data.model.BookCondition
import com.example.bookswap.data.model.BookOptions

/**
 * Filter sheet (Epic 5): category, language, condition, author + a "Reset Discovery"
 * action. Keyword search lives in the Discovery top bar. Distance radius is deferred
 * (decision #6). Edits are local until Apply, so a cancel discards them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryFilterSheet(
    filter: DiscoveryFilter,
    onApply: (DiscoveryFilter) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var category by remember { mutableStateOf(filter.category) }
    var language by remember { mutableStateOf(filter.language) }
    var condition by remember { mutableStateOf(filter.condition) }
    var author by remember { mutableStateOf(filter.author) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.filter_title),
                style = MaterialTheme.typography.titleLarge,
            )

            FilterDropdown(
                label = stringResource(R.string.book_category),
                options = BookOptions.categories,
                selected = category,
                onSelected = { category = it },
            )
            FilterDropdown(
                label = stringResource(R.string.book_language),
                options = BookOptions.languages,
                selected = language,
                onSelected = { language = it },
            )
            FilterDropdown(
                label = stringResource(R.string.book_condition),
                options = BookCondition.entries.map { it.name },
                selected = condition,
                display = { BookCondition.labelOf(it) },
                onSelected = { condition = it },
            )
            OutlinedTextField(
                value = author,
                onValueChange = { author = it },
                label = { Text(stringResource(R.string.book_author)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        category = null; language = null; condition = null; author = ""
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.filter_clear)) }
                Button(
                    onClick = {
                        onApply(
                            filter.copy(
                                category = category,
                                language = language,
                                condition = condition,
                                author = author.trim(),
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.filter_apply)) }
            }

            TextButton(
                onClick = onReset,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.filter_reset_discovery)) }
        }
    }
}

/** Dropdown whose first entry ("Any") maps the selection back to null. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDropdown(
    label: String,
    options: List<String>,
    selected: String?,
    onSelected: (String?) -> Unit,
    display: (String) -> String = { it },
) {
    var expanded by remember { mutableStateOf(false) }
    val anyLabel = stringResource(R.string.filter_any)
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selected?.let(display) ?: anyLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(anyLabel) },
                onClick = { onSelected(null); expanded = false },
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(display(option)) },
                    onClick = { onSelected(option); expanded = false },
                )
            }
        }
    }
}
