package app.dift.system.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.dift.R
import app.dift.ui.theme.DiftTheme
import kotlinx.coroutines.delay

/**
 * The full-screen lockout shown while a usage-debt cooldown is being served (ADR-0003: overlay,
 * never an Activity). There is no unblock path — only a live countdown to [cooldownUntilMs].
 */
@Composable
fun BlockScreenContent(blockName: String?, cooldownUntilMs: Long) {
    DiftTheme {
        // Surface (not a raw background modifier) so LocalContentColor is set correctly —
        // otherwise text renders default-black on the dark background.
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.block_title),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                if (!blockName.isNullOrBlank()) {
                    Text(
                        text = blockName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.block_debt_message),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
                Countdown(cooldownUntilMs)
            }
        }
    }
}

@Composable
private fun Countdown(untilMs: Long) {
    var remainingMs by remember(untilMs) { mutableLongStateOf(untilMs - System.currentTimeMillis()) }
    LaunchedEffect(untilMs) {
        while (remainingMs > 0) {
            delay(ONE_SECOND_MS)
            remainingMs = untilMs - System.currentTimeMillis()
        }
    }
    val seconds = (remainingMs / ONE_SECOND_MS + 1).coerceAtLeast(0)
    Text(
        text = stringResource(R.string.block_seconds_remaining, seconds),
        style = MaterialTheme.typography.displayMedium,
        textAlign = TextAlign.Center,
    )
}

private const val ONE_SECOND_MS = 1_000L
