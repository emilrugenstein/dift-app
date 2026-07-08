package app.dift.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import app.dift.system.permissions.PermissionsChecker
import app.dift.system.permissions.PermissionsState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val permissionsChecker: PermissionsChecker,
) : ViewModel() {

    val permissions: StateFlow<PermissionsState> = permissionsChecker.state

    fun refresh() = permissionsChecker.refresh()
}
