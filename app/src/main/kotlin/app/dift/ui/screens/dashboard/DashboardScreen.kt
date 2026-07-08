package app.dift.ui.screens.dashboard

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dift.R
import app.dift.ui.format.formatDuration
import java.time.LocalDate

@Composable
fun DashboardScreen(viewModel: DashboardViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    if (!state.usageAccessGranted) {
        UsageAccessMissing()
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.dashboard_today_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = formatDuration(state.todayTotalMs),
                style = MaterialTheme.typography.displayMedium,
            )
        }
        item { WeekBars(state.week) }
        item {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.dashboard_top_apps_title),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (state.topApps.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.dashboard_empty),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(state.topApps, key = { it.packageName }) { app -> AppUsageRow(app) }
    }
}

@Composable
private fun AppUsageRow(app: DashboardViewModel.AppUsage) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(app.label, style = MaterialTheme.typography.bodyLarge)
            Text(formatDuration(app.totalMs), style = MaterialTheme.typography.bodyMedium)
        }
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .fillMaxWidth(app.fractionOfTop.coerceIn(0.02f, 1f))
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun WeekBars(week: List<DashboardViewModel.DayBar>) {
    val max = week.maxOfOrNull { it.totalMs }?.coerceAtLeast(1L) ?: 1L
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        week.forEach { day ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val fraction = day.totalMs.toFloat() / max
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((BAR_MAX_DP * fraction).coerceAtLeast(2f).dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                )
                Text(
                    text = LocalDate.parse(day.dayLocal).dayOfWeek.name.take(1),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun UsageAccessMissing() {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.dashboard_usage_access_missing),
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(
            onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        ) {
            Text(stringResource(R.string.onboarding_open_usage_settings))
        }
    }
}

private const val BAR_MAX_DP = 64f
