package app.dift.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dift.BuildConfig
import app.dift.R

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val nightMode by viewModel.nightModeEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val packageUri = Uri.parse("package:${context.packageName}")

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_permissions_title),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        item {
            PermissionRow(
                label = stringResource(R.string.onboarding_usage_title),
                granted = permissions.usageAccess,
            ) { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        }
        item {
            PermissionRow(
                label = stringResource(R.string.onboarding_notifications_title),
                granted = permissions.notifications,
            ) {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            }
        }
        item {
            PermissionRow(
                label = stringResource(R.string.settings_permission_overlay),
                granted = permissions.overlay,
            ) {
                context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri))
            }
        }
        item {
            PermissionRow(
                label = stringResource(R.string.settings_permission_accessibility),
                granted = permissions.accessibility,
            ) { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        item {
            PermissionRow(
                label = stringResource(R.string.settings_permission_battery),
                granted = permissions.batteryExempt,
            ) {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
        item { HorizontalDivider() }
        item {
            Text(
                text = stringResource(R.string.settings_night_mode_title),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_night_mode_label),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.settings_night_mode_description),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = nightMode, onCheckedChange = viewModel::setNightMode)
            }
        }
        item { HorizontalDivider() }
        item {
            Text(
                text = stringResource(R.string.home_version_label, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onOpen: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (granted) Icons.Filled.Check else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (granted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        )
        OutlinedButton(onClick = onOpen) {
            Text(stringResource(R.string.settings_open))
        }
    }
}
