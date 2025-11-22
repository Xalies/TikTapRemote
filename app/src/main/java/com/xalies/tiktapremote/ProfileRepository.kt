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
import java.util.concurrent.TimeUnit

enum class AppTier {
    FREE,
    ESSENTIALS,
    PRO_SAVER,
    PRO
}

class ProfileRepository(context: Context) {
    private val settingsPrefs = context.getSharedPreferences("TikTapRemoteSettings", Context.MODE_PRIVATE)
    private val database = AppDatabase.getDatabase(context)
    private val profileDao = database.profileDao()

    // Timestamp for December 31, 2025 23:59:59 GMT
    private val BACKDOOR_EXPIRATION_DATE = 1767225599000L
    private val DEBUG_TIER_KEY = "debug_tier_override"

    // 10 Hours in Milliseconds (Changed from 8)
    private val AD_REWARD_DURATION = 10 * 60 * 60 * 1000L

    private val repoScope = CoroutineScope(Dispatchers.IO)

    val allProfiles: Flow<List<Profile>> = profileDao.getAllProfiles()

    suspend fun getProfileByPackage(packageName: String): Profile? {
        return profileDao.getProfileByPackage(packageName)
    }

    fun saveProfile(profile: Profile) {
        repoScope.launch {
            profileDao.insertProfile(profile)
        }
    }

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
        return settingsPrefs.getBoolean("isHapticEnabled", true)
    }

    fun setBackdoorUsed(used: Boolean) {
        settingsPrefs.edit().putBoolean("backdoor_used", used).apply()
    }

    fun isBackdoorFlagged(): Boolean {
        return settingsPrefs.getBoolean("backdoor_used", false)
    }

    fun checkAndEnforceBackdoorExpiration() {
        val now = System.currentTimeMillis()
        if (now > BACKDOOR_EXPIRATION_DATE && isBackdoorFlagged()) {
            settingsPrefs.edit().remove(DEBUG_TIER_KEY).apply()
            enforceFreeTierLimits()
        }
    }

    fun isBackdoorActive(): Boolean {
        return System.currentTimeMillis() <= BACKDOOR_EXPIRATION_DATE
    }

    fun getPurchasedTier(): AppTier {
        val savedTierName = settingsPrefs.getString("user_tier", AppTier.FREE.name)
        return try {
            AppTier.valueOf(savedTierName ?: AppTier.FREE.name)
        } catch (e: Exception) {
            AppTier.FREE
        }
    }

    fun getCurrentTier(): AppTier {
        if (isTrialActive() || isAdRewardActive()) {
            return AppTier.PRO
        }
        if (isBackdoorActive()) {
            val debugTierName = settingsPrefs.getString(DEBUG_TIER_KEY, null)
            if (debugTierName != null) {
                try {
                    return AppTier.valueOf(debugTierName)
                } catch (e: Exception) {
                    // Invalid tier
                }
            }
        }
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

    // --- AD REWARD LOGIC (10 HOURS, EXTENDABLE) ---
    fun addAdRewardTime() {
        val now = System.currentTimeMillis()
        val currentExpiry = settingsPrefs.getLong("ad_reward_expiry_time", 0)

        // Extend existing time or start new from now
        val newExpiry = if (currentExpiry > now) {
            currentExpiry + AD_REWARD_DURATION
        } else {
            now + AD_REWARD_DURATION
        }

        settingsPrefs.edit()
            .putLong("ad_reward_expiry_time", newExpiry)
            .apply()
    }

    fun isAdRewardActive(): Boolean {
        val expiryTime = settingsPrefs.getLong("ad_reward_expiry_time", 0)
        if (expiryTime == 0L) return false
        return System.currentTimeMillis() < expiryTime
    }

    fun getTrialTimeRemaining(): Long {
        val now = System.currentTimeMillis()

        // Check 10-min trial
        var tenMinRemaining = 0L
        val trialStart = settingsPrefs.getLong("trial_start_time", 0)
        if (trialStart != 0L) {
            val end = trialStart + (10 * 60 * 1000)
            if (end > now) tenMinRemaining = end - now
        }

        // Check Ad Reward
        var adRewardRemaining = 0L
        val adExpiry = settingsPrefs.getLong("ad_reward_expiry_time", 0)
        if (adExpiry > now) {
            adRewardRemaining = adExpiry - now
        }

        return maxOf(tenMinRemaining, adRewardRemaining)
    }

    fun enforceFreeTierLimits() {
        repoScope.launch {
            if (isTrialActive() || isAdRewardActive()) return@launch

            if (getCurrentTier() == AppTier.FREE) {
                val allProfiles = profileDao.getAllProfilesList()
                val profilesToUpdate = mutableListOf<Profile>()

                allProfiles.forEach { profile ->
                    if (profile.packageName != GLOBAL_PROFILE_PACKAGE_NAME && profile.isEnabled) {
                        profilesToUpdate.add(profile.copy(isEnabled = false))
                    }
                }

                if (profilesToUpdate.isNotEmpty()) {
                    profileDao.insertProfiles(profilesToUpdate)
                }

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