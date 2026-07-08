package app.dift.ui.screens.onboarding

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import app.dift.R

/**
 * M1 onboarding: usage access (required) + notifications (recommended).
 * Blocking-related steps (overlay, accessibility incl. restricted-settings walkthrough,
 * battery) surface later per docs/features/onboarding.md.
 */
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ -> viewModel.refresh() }

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.displaySmall,
            )
            Text(
                text = stringResource(R.string.onboarding_intro),
                style = MaterialTheme.typography.bodyLarge,
            )

            OnboardingStep(
                title = stringResource(R.string.onboarding_usage_title),
                description = stringResource(R.string.onboarding_usage_description),
                granted = permissions.usageAccess,
                buttonLabel = stringResource(R.string.onboarding_open_usage_settings),
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                },
            )

            OnboardingStep(
                title = stringResource(R.string.onboarding_notifications_title),
                description = stringResource(R.string.onboarding_notifications_description),
                granted = permissions.notifications,
                buttonLabel = stringResource(R.string.onboarding_grant_notifications),
                onClick = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onDone,
                enabled = permissions.usageAccess,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.onboarding_continue))
            }
        }
    }
}

@Composable
private fun OnboardingStep(
    title: String,
    description: String,
    granted: Boolean,
    buttonLabel: String,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (granted) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = stringResource(R.string.onboarding_step_granted),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        if (!granted) {
            OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                Text(buttonLabel)
            }
        }
    }
}
