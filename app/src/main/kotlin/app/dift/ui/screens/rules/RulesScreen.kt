package app.dift.ui.screens.rules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dift.R
import app.dift.domain.model.Rule
import app.dift.domain.model.RuleType

@Composable
fun RulesScreen(
    onOpenHistory: () -> Unit,
    onAddRule: () -> Unit,
    onEditRule: (Long) -> Unit,
    viewModel: RulesViewModel = hiltViewModel(),
) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.rules_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = onOpenHistory) {
                    Text(stringResource(R.string.rules_open_history))
                }
            }
        }
        item {
            Button(onClick = onAddRule, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.rules_add))
            }
        }
        if (rules.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.rules_empty),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(rules, key = { it.id }) { rule ->
            RuleRow(
                rule = rule,
                onEdit = { onEditRule(rule.id) },
                onToggle = { viewModel.setEnabled(rule.id, it) },
                onDelete = { viewModel.delete(rule.id) },
            )
        }
    }
}

@Composable
private fun RuleRow(
    rule: Rule,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = rule.type != RuleType.USAGE_DEBT, onClick = onEdit),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(rule.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = ruleSubtitle(rule),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        TextButton(onClick = onDelete) { Text(stringResource(R.string.rules_delete)) }
        Switch(checked = rule.enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun ruleSubtitle(rule: Rule): String = when (rule.type) {
    RuleType.ALWAYS -> stringResource(R.string.rule_subtitle_always)
    RuleType.DAILY_LIMIT -> stringResource(R.string.rule_subtitle_limit, rule.limitMinutes ?: 0)
    RuleType.SCHEDULE -> stringResource(R.string.rule_subtitle_schedule)
    RuleType.USAGE_DEBT -> stringResource(R.string.rule_subtitle_debt)
}
