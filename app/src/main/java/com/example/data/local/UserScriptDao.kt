package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.UserScript
import kotlinx.coroutines.flow.Flow

@Dao
interface UserScriptDao {
    @Query("SELECT * FROM user_scripts ORDER BY isEnabled DESC, createdAt DESC")
    fun getAllScripts(): Flow<List<UserScript>>

    @Query("SELECT * FROM user_scripts WHERE isEnabled = 1")
    fun getEnabledScripts(): Flow<List<UserScript>>

    @Query("SELECT * FROM user_scripts WHERE id = :id LIMIT 1")
    suspend fun getScriptById(id: String): UserScript?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScript(script: UserScript)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScripts(scripts: List<UserScript>)

    @Update
    suspend fun updateScript(script: UserScript)

    @Delete
    suspend fun deleteScript(script: UserScript)

    @Query("DELETE FROM user_scripts WHERE id = :id")
    suspend fun deleteScriptById(id: String)

    @Query("UPDATE user_scripts SET isEnabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun toggleScript(id: String, enabled: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM user_scripts")
    suspend fun getCount(): Int
}
