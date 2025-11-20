package com.xalies.tiktapremote

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object ProfileManager {
    private var repository: ProfileRepository? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Exposed as StateFlow for UI Consumption (converted from Room Flow)
    private val _profiles = MutableStateFlow<Set<Profile>>(emptySet())
    val profiles = _profiles.asStateFlow()

    fun initialize(context: Context) {
        if (repository == null) {
            val repo = ProfileRepository(context)
            repository = repo

            // Start observing Room database
            scope.launch {
                repo.allProfiles.collect { list ->
                    _profiles.value = list.toSet()
                }
            }
        }
    }

    fun updateProfile(profile: Profile) {
        repository?.saveProfile(profile)
    }

    fun deleteProfile(packageName: String) {
        repository?.deleteProfile(packageName)
    }

    fun checkAndEnforceLimits() {
        repository?.let { repo ->
            // FIRST: Check for backdoor expiration
            repo.checkAndEnforceBackdoorExpiration()

            // THEN: Standard limit checks (enforceFreeTierLimits runs async internally in Repo)
            repo.enforceFreeTierLimits()
        }
    }
}