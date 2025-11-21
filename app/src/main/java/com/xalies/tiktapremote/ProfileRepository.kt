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
    private val DEBUG_TIER_KEY = "debug_tier_override"

    // 8 Hours in Milliseconds
    private val AD_REWARD_DURATION = 8 * 60 * 60 * 1000L

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

    fun setHapticEnabled(isEnabled: Boolean) {
        settingsPrefs.edit().putBoolean("isHapticEnabled", isEnabled).apply()
    }

    fun isHapticEnabled(): Boolean {
        return settingsPrefs.getBoolean("isHapticEnabled", true) // Default true for better UX
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
            // 1. Remove debug override
            settingsPrefs.edit().remove(DEBUG_TIER_KEY).apply()

            // 2. Enforce limits (in case they were using features they don't own)
            enforceFreeTierLimits()
        }
    }

    fun isBackdoorActive(): Boolean {
        return System.currentTimeMillis() <= BACKDOOR_EXPIRATION_DATE
    }

    // --- TIER LOGIC & RESTRICTIONS ---

    // Returns the tier the user has actually bought (or defaulted to FREE)
    fun getPurchasedTier(): AppTier {
        val savedTierName = settingsPrefs.getString("user_tier", AppTier.FREE.name)
        return try {
            AppTier.valueOf(savedTierName ?: AppTier.FREE.name)
        } catch (e: Exception) {
            AppTier.FREE
        }
    }

    // Returns the effective tier (including Trials, Ad Rewards, and Debug Overrides)
    fun getCurrentTier(): AppTier {
        // 1. Priority: Ad Reward OR 10-min Trial -> Grant PRO
        if (isTrialActive() || isAdRewardActive()) {
            return AppTier.PRO
        }

        // 2. Debug Override (Backdoor)
        if (isBackdoorActive()) {
            val debugTierName = settingsPrefs.getString(DEBUG_TIER_KEY, null)
            if (debugTierName != null) {
                try {
                    return AppTier.valueOf(debugTierName)
                } catch (e: Exception) {
                    // Invalid tier, fall through
                }
            }
        }

        // 3. Fallback to actual purchased tier
        return getPurchasedTier()
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

        // Save to DEBUG key, not the actual purchase key
        settingsPrefs.edit().putString(DEBUG_TIER_KEY, next.name).apply()

        return next
    }

    // --- 10 MINUTE TRIAL LOGIC ---
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

    // --- 8 HOUR AD REWARD LOGIC ---
    fun activateAdReward() {
        val now = System.currentTimeMillis()
        settingsPrefs.edit()
            .putLong("ad_reward_start_time", now)
            .apply()
    }

    fun isAdRewardActive(): Boolean {
        val startTime = settingsPrefs.getLong("ad_reward_start_time", 0)
        if (startTime == 0L) return false

        val now = System.currentTimeMillis()
        return (now - startTime) < AD_REWARD_DURATION
    }

    fun getTrialTimeRemaining(): Long {
        // Check 10-min trial
        var tenMinRemaining = 0L
        val trialStart = settingsPrefs.getLong("trial_start_time", 0)
        if (trialStart != 0L) {
            val now = System.currentTimeMillis()
            val end = trialStart + (10 * 60 * 1000)
            if (end > now) tenMinRemaining = end - now
        }

        // Check Ad Reward
        var adRewardRemaining = 0L
        val adStart = settingsPrefs.getLong("ad_reward_start_time", 0)
        if (adStart != 0L) {
            val now = System.currentTimeMillis()
            val end = adStart + AD_REWARD_DURATION
            if (end > now) adRewardRemaining = end - now
        }

        // Return whichever is greater
        return maxOf(tenMinRemaining, adRewardRemaining)
    }

    // --- CLEANUP CREW (Updated to Disable instead of Delete) ---
    fun enforceFreeTierLimits() {
        // This needs to run in a coroutine because DB ops are suspending
        repoScope.launch {
            // IMPORTANT: Do not restrict stuff if either trial is active
            if (isTrialActive() || isAdRewardActive()) return@launch

            // Note: We are now enforcing based on the *Current Tier*
            if (getCurrentTier() == AppTier.FREE) {
                // 1. Disable all non-global profiles instead of deleting
                val allProfiles = profileDao.getAllProfilesList()
                val profilesToUpdate = mutableListOf<Profile>()

                allProfiles.forEach { profile ->
                    if (profile.packageName != GLOBAL_PROFILE_PACKAGE_NAME && profile.isEnabled) {
                        // Disable it
                        profilesToUpdate.add(profile.copy(isEnabled = false))
                    }
                }

                if (profilesToUpdate.isNotEmpty()) {
                    profileDao.insertProfiles(profilesToUpdate)
                }

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