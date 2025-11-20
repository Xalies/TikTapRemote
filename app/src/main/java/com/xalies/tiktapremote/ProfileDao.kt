package com.xalies.tiktapremote.data

import androidx.room.*
import com.xalies.tiktapremote.Profile
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles")
    fun getAllProfiles(): Flow<List<Profile>>

    @Query("SELECT * FROM profiles WHERE packageName = :packageName LIMIT 1")
    suspend fun getProfileByPackage(packageName: String): Profile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: Profile)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfiles(profiles: List<Profile>)

    @Query("DELETE FROM profiles WHERE packageName = :packageName")
    suspend fun deleteProfile(packageName: String)

    @Query("DELETE FROM profiles WHERE packageName != :excludePackage")
    suspend fun deleteAllExcept(excludePackage: String)

    @Query("DELETE FROM profiles")
    suspend fun clearAll()
}