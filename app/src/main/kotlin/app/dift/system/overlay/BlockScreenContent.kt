package app.dift.system.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.dift.R
import app.dift.domain.engine.Verdict
import app.dift.domain.model.GrantMethod
import app.dift.domain.model.Strictness
import app.dift.ui.theme.DiftTheme
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Full-screen blocking UI rendered inside the overlay window (ADR-0003). Per-strictness:
 * TAP_THROUGH = one button; FRICTION = wait countdown then a typed-phrase field; HARD = a
 * message plus, when known, the time the block ends.
 */
@Composable
fun BlockScreenContent(
    verdict: Verdict.Block,
    packageName: String,
    frictionPhrase: String,
    onUnblock: (GrantMethod) -> Unit,
) {
    DiftTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding()
                .imePadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.block_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = reasonText(verdict),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 16.dp),
            )

            when (verdict.strictness) {
                Strictness.TAP_THROUGH -> TapUnblock(onUnblock)
                Strictness.FRICTION -> FrictionUnblock(verdict, frictionPhrase, onUnblock)
                Strictness.HARD -> HardBlock(verdict)
            }
        }
    }
}

@Composable
private fun TapUnblock(onUnblock: (GrantMethod) -> Unit) {
    Button(
        onClick = { onUnblock(GrantMethod.TAP) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.block_unblock))
    }
}

@Composable
private fun FrictionUnblock(
    verdict: Verdict.Block,
    frictionPhrase: String,
    onUnblock: (GrantMethod) -> Unit,
) {
    var remaining by remember { mutableIntStateOf(verdict.frictionDelaySeconds) }
    var typed by remember { mutableStateOf("") }

    LaunchedEffect(verdict.ruleId) {
        remaining = verdict.frictionDelaySeconds
        while (remaining > 0) {
            delay(ONE_SECOND_MS)
            remaining -= 1
        }
    }

    if (remaining > 0) {
        Text(
            text = stringResource(R.string.block_friction_wait, remaining),
            style = MaterialTheme.typography.titleLarge,
        )
    } else {
        Text(
            text = stringResource(R.string.block_friction_type_phrase, frictionPhrase),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        OutlinedTextField(
            value = typed,
            onValueChange = { typed = it },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        )
        Button(
            onClick = { onUnblock(GrantMethod.FRICTION) },
            enabled = typed.trim() == frictionPhrase.trim(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.block_unblock))
        }
    }
}

@Composable
private fun HardBlock(verdict: Verdict.Block) {
    val until = verdict.blockedUntilMs
    val text = if (until != null) {
        val time = Instant.ofEpochMilli(until).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm"))
        stringResource(R.string.block_hard_until, time)
    } else {
        stringResource(R.string.block_hard_no_end)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun reasonText(verdict: Verdict.Block): String = when (verdict.reason) {
    app.dift.domain.model.BlockReason.ALWAYS -> stringResource(R.string.block_reason_always)
    app.dift.domain.model.BlockReason.LIMIT_EXHAUSTED -> stringResource(R.string.block_reason_limit)
    app.dift.domain.model.BlockReason.IN_SCHEDULE -> stringResource(R.string.block_reason_schedule)
    app.dift.domain.model.BlockReason.USAGE_DEBT -> stringResource(R.string.block_reason_debt)
}

private const val ONE_SECOND_MS = 1_000L
