package com.mushroom.adventure.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mushroom.adventure.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateChecker: UpdateChecker,
) : ViewModel() {

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo = _updateInfo.asStateFlow()

    // Memory-only flag: don't re-show the same version in the same session
    private var shownVersion: String? = null

    fun checkForUpdate(forceShow: Boolean = false) {
        viewModelScope.launch {
            val info = updateChecker.checkForUpdate(
                owner = BuildConfig.UPDATE_CHECK_OWNER,
                repo = BuildConfig.UPDATE_CHECK_REPO,
                currentVersion = BuildConfig.VERSION_NAME,
                tagPrefix = BuildConfig.UPDATE_CHECK_TAG_PREFIX,
                enabled = BuildConfig.UPDATE_CHECK_ENABLED,
            ) ?: return@launch

            if (!forceShow && info.remoteVersion == shownVersion) return@launch
            shownVersion = info.remoteVersion
            _updateInfo.value = info
        }
    }

    fun dismissUpdate() {
        _updateInfo.value = null
    }
}
