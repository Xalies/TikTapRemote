package com.xalies.tiktapremote

import android.content.Context
import android.view.KeyEvent
import com.xalies.tiktapremote.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

enum class AppTier {
    FREE,
    ESSENTIALS,
    PRO_SAVER,
    PRO
}

class ProfileRepository(context: Context) {
    // Keep SharedPreferences for simple settings (Tier, Service State, Flags)
    private val settingsPrefs = context.getSharedPreferences("TikTapRemoteSettings", Context.MODE_PRIVATE)

    // Use Room for Profile Data
    private val database = AppDatabase.getDatabase(context)
    private val profileDao = database.profileDao()

    // Timestamp for December 31, 2025 23:59:59 GMT
    private val BACKDOOR_EXPIRATION_DATE = 1767225599000L

    // Scope for DB operations if not suspended (helper methods)
    private val repoScope = CoroutineScope(Dispatchers.IO)

    // --- PROFILE MANAGEMENT (Now via Room) ---

    // Expose Flow directly from DAO
    val allProfiles: Flow<List<Profile>> = profileDao.getAllProfiles()

    fun saveProfile(profile: Profile) {
        repoScope.launch {
            profileDao.insertProfile(profile)
        }
    }

    // Helper to get synchronous snapshot (used in legacy limit checks)
    fun getProfilesSnapshot(): List<Profile> = runBlocking {
        try {
            allProfiles.first()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun deleteProfile(packageName: String) {
        repoScope.launch {
            profileDao.deleteProfile(packageName)
        }
    }

    // --- SETTINGS (Kept in Prefs) ---

    fun setServiceEnabled(isEnabled: Boolean) {
        settingsPrefs.edit().putBoolean("isServiceEnabled", isEnabled).apply()
    }

    fun isServiceEnabled(): Boolean {
        return settingsPrefs.getBoolean("isServiceEnabled", true)
    }

    // --- BACKDOOR PROTECTION ---
    fun setBackdoorUsed(used: Boolean) {
        settingsPrefs.edit().putBoolean("backdoor_used", used).apply()
    }

    fun isBackdoorFlagged(): Boolean {
        return settingsPrefs.getBoolean("backdoor_used", false)
    }

    fun checkAndEnforceBackdoorExpiration() {
        val now = System.currentTimeMillis()
        // Check if we are past the date AND the user was flagged
        if (now > BACKDOOR_EXPIRATION_DATE && isBackdoorFlagged()) {
            // 1. Revert to FREE
            setCurrentTier(AppTier.FREE)

            // 2. Enforce limits
            enforceFreeTierLimits()
        }
    }

    fun isBackdoorActive(): Boolean {
        return System.currentTimeMillis() <= BACKDOOR_EXPIRATION_DATE
    }

    // --- TIER LOGIC & RESTRICTIONS ---

    fun getCurrentTier(): AppTier {
        if (isTrialActive()) {
            return AppTier.PRO
        }
        val savedTierName = settingsPrefs.getString("user_tier", AppTier.FREE.name)
        return try {
            AppTier.valueOf(savedTierName ?: AppTier.FREE.name)
        } catch (e: Exception) {
            AppTier.FREE
        }
    }

    fun setCurrentTier(tier: AppTier) {
        settingsPrefs.edit().putString("user_tier", tier.name).apply()
    }

    fun cycleTier(): AppTier {
        val current = getCurrentTier()
        if (isTrialActive()) {
            settingsPrefs.edit().putLong("trial_start_time", 0).apply()
        }
        val next = when (current) {
            AppTier.FREE -> AppTier.ESSENTIALS
            AppTier.ESSENTIALS -> AppTier.PRO_SAVER
            AppTier.PRO_SAVER -> AppTier.PRO
            AppTier.PRO -> AppTier.FREE
        }
        setCurrentTier(next)
        return next
    }

    // --- TRIAL LOGIC ---
    fun hasUsedTrial(): Boolean {
        return settingsPrefs.getBoolean("trial_used", false)
    }

    fun activateTrial() {
        val now = System.currentTimeMillis()
        settingsPrefs.edit()
            .putLong("trial_start_time", now)
            .putBoolean("trial_used", true)
            .apply()
    }

    fun isTrialActive(): Boolean {
        val startTime = settingsPrefs.getLong("trial_start_time", 0)
        if (startTime == 0L) return false

        val now = System.currentTimeMillis()
        val tenMinutes = 10 * 60 * 1000
        return (now - startTime) < tenMinutes
    }

    fun getTrialTimeRemaining(): Long {
        val startTime = settingsPrefs.getLong("trial_start_time", 0)
        if (startTime == 0L) return 0
        val now = System.currentTimeMillis()
        val tenMinutes = 10 * 60 * 1000
        return (startTime + tenMinutes) - now
    }

    // --- CLEANUP CREW (Updated for Room) ---
    fun enforceFreeTierLimits() {
        // This needs to run in a coroutine because DB ops are suspending
        repoScope.launch {
            if (isTrialActive()) return@launch
            if (getCurrentTier() != AppTier.FREE) return@launch

            // 1. Delete all non-global profiles directly via SQL
            profileDao.deleteAllExcept(GLOBAL_PROFILE_PACKAGE_NAME)

            // 2. Fetch Global Profile to sanitize it
            val globalProfile = profileDao.getProfileByPackage(GLOBAL_PROFILE_PACKAGE_NAME)

            if (globalProfile != null) {
                var modifiedGlobal = globalProfile
                var globalDirty = false

                if (modifiedGlobal.keyCode != KeyEvent.KEYCODE_VOLUME_UP) {
                    modifiedGlobal = modifiedGlobal.copy(keyCode = KeyEvent.KEYCODE_VOLUME_UP)
                    globalDirty = true
                }

                if (modifiedGlobal.actions.containsKey(TriggerType.DOUBLE_PRESS)) {
                    val newActions = modifiedGlobal.actions.toMutableMap()
                    newActions.remove(TriggerType.DOUBLE_PRESS)
                    modifiedGlobal = modifiedGlobal.copy(actions = newActions)
                    globalDirty = true
                }

                val singleAction = modifiedGlobal.actions[TriggerType.SINGLE_PRESS]
                if (singleAction != null) {
                    if (singleAction.type != ActionType.TAP && singleAction.type != ActionType.SWIPE_UP) {
                        val newActions = modifiedGlobal.actions.toMutableMap()
                        newActions[TriggerType.SINGLE_PRESS] = Action(ActionType.TAP)
                        modifiedGlobal = modifiedGlobal.copy(actions = newActions)
                        globalDirty = true
                    }
                }

                if (globalDirty) {
                    profileDao.insertProfile(modifiedGlobal)
                }
            }
        }
    }

    fun showAds(): Boolean {
        val tier = getCurrentTier()
        return tier == AppTier.FREE || tier == AppTier.PRO_SAVER
    }

    fun getMaxAppProfiles(): Int {
        return when (getCurrentTier()) {
            AppTier.FREE -> 0
            AppTier.ESSENTIALS -> 2
            AppTier.PRO_SAVER, AppTier.PRO -> Int.MAX_VALUE
        }
    }

    fun canConfigureTriggerKey(): Boolean {
        return getCurrentTier() != AppTier.FREE
    }

    fun canUseDoublePress(): Boolean {
        return getCurrentTier() != AppTier.FREE
    }

    fun canUseRepeatMode(): Boolean {
        val tier = getCurrentTier()
        return tier == AppTier.PRO_SAVER || tier == AppTier.PRO
    }

    fun isActionAllowed(actionType: ActionType): Boolean {
        val tier = getCurrentTier()
        return when (actionType) {
            ActionType.TAP, ActionType.SWIPE_UP -> true
            ActionType.DOUBLE_TAP, ActionType.SWIPE_DOWN -> tier != AppTier.FREE
            ActionType.SWIPE_LEFT, ActionType.SWIPE_RIGHT, ActionType.RECORDED ->
                tier == AppTier.PRO_SAVER || tier == AppTier.PRO
            ActionType.TOGGLE_AUTO_SCROLL -> false
        }
    }

    fun getDefaultKeyCode(): Int = KeyEvent.KEYCODE_VOLUME_UP
}