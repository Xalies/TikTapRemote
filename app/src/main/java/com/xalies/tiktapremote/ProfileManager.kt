package com.xalies.tiktapremote

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object ProfileManager {
    private lateinit var repository: ProfileRepository

    private val _profiles = MutableStateFlow<Set<Profile>>(emptySet())
    val profiles = _profiles.asStateFlow()

    fun initialize(context: Context) {
        if (!::repository.isInitialized) {
            repository = ProfileRepository(context)
            _profiles.value = repository.loadProfiles()
        }
    }

    fun updateProfile(profile: Profile) {
        _profiles.update { currentProfiles ->
            val newProfiles = currentProfiles.toMutableSet()
            newProfiles.removeAll { it.packageName == profile.packageName }
            newProfiles.add(profile)
            repository.saveProfiles(newProfiles)
            newProfiles
        }
    }

    fun deleteProfile(packageName: String) {
        _profiles.update { currentProfiles ->
            val newProfiles = currentProfiles.toMutableSet()
            newProfiles.removeAll { it.packageName == packageName }
            repository.saveProfiles(newProfiles)
            newProfiles
        }
    }

    fun checkAndEnforceLimits() {
        if (::repository.isInitialized) {
            // FIRST: Check for backdoor expiration
            val backdoorEnforced = repository.checkAndEnforceBackdoorExpiration()

            // THEN: Standard limit checks (or if backdoor just forced a reset)
            if (backdoorEnforced || repository.enforceFreeTierLimits()) {
                // Reload if changes were made
                _profiles.value = repository.loadProfiles()
            }
        }
    }
}