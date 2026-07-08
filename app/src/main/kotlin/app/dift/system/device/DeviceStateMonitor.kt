package app.dift.system.device

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks unlock/screen state for the night usage-debt mechanic (docs/features/usage-debt.md).
 * Enforcement is unlock-gated: while the keyguard is up, nothing is blocked — so SOS calls,
 * the flashlight tile, and the lockscreen camera stay available.
 */
@Singleton
class DeviceStateMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class DeviceState(val unlocked: Boolean, val screenOn: Boolean)

    private val keyguardManager = context.getSystemService(KeyguardManager::class.java)
    private val powerManager = context.getSystemService(PowerManager::class.java)

    private val mutableState = MutableStateFlow(currentState())
    val state: StateFlow<DeviceState> = mutableState.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            mutableState.value = currentState()
        }
    }

    private var registered = false

    fun start() {
        if (registered) return
        registered = true
        context.registerReceiver(
            receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
        )
        mutableState.value = currentState()
    }

    fun refresh() {
        mutableState.value = currentState()
    }

    private fun currentState() = DeviceState(
        unlocked = !keyguardManager.isKeyguardLocked,
        screenOn = powerManager.isInteractive,
    )
}
