package ru.maxrubtsoff.bankagg.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.maxrubtsoff.bankagg.domain.model.AppLock
import ru.maxrubtsoff.bankagg.domain.model.Feature
import ru.maxrubtsoff.bankagg.domain.model.Features
import ru.maxrubtsoff.bankagg.domain.repository.SettingsRepository

class SettingsViewModel(
    private val settings: SettingsRepository,
    /** Stores a new PIN sealed with the phone's key (B-32). */
    private val setNewPin: suspend (String) -> Unit
) : ViewModel() {

    val selectedPackages: StateFlow<Set<String>> =
        settings.selectedPackages.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet()
        )

    fun toggleBrandPackages(packages: Set<String>, enabled: Boolean) {
        viewModelScope.launch {
            val current = settings.currentSelectedPackages()
            val next = if (enabled) current + packages else current - packages
            settings.setSelectedPackages(next)
        }
    }

    // ---- sections (F-21) ----

    val features: StateFlow<Features> = settings.features.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Features.ALL)

    fun toggleFeature(f: Feature) = viewModelScope.launch { settings.setFeatures(settings.currentFeatures().toggle(f)) }

    fun finishOnboarding() = viewModelScope.launch { settings.setOnboarded(true) }

    // ---- privacy ----

    private val _pinSet = MutableStateFlow(false)
    /** The app lock is on (a PIN is stored). */
    val pinSet: StateFlow<Boolean> = _pinSet.asStateFlow()

    init {
        viewModelScope.launch { _pinSet.value = settings.lockPin() != null }
    }

    val biometric: StateFlow<Boolean> = settings.biometricEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val hideOnFlip: StateFlow<Boolean> = settings.hideOnFlip.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val hideBySwipe: StateFlow<Boolean> = settings.hideBySwipe.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** Stores a new PIN (hashing takes a moment — off the main thread). */
    fun setPin(pin: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            setNewPin(pin)
            _pinSet.value = true
            onDone()
        }
    }

    fun turnLockOff() {
        viewModelScope.launch {
            settings.setLockPin(null)
            _pinSet.value = false
        }
    }

    fun setBiometric(on: Boolean) = viewModelScope.launch { settings.setBiometricEnabled(on) }
    fun setHideOnFlip(on: Boolean) = viewModelScope.launch { settings.setHideOnFlip(on) }
    fun setHideBySwipe(on: Boolean) = viewModelScope.launch { settings.setHideBySwipe(on) }

    val hideInRecents: StateFlow<Boolean> = settings.hideInRecents.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    fun setHideInRecents(on: Boolean) = viewModelScope.launch { settings.setHideInRecents(on) }
}
