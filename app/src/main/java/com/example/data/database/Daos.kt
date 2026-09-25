package com.example.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.MacroEntity
import com.example.data.model.RoiRegion
import com.example.data.model.ScriptEntity
import com.example.data.model.TargetPoint
import kotlinx.coroutines.flow.Flow

@Dao
interface PointDao {
    @Query("SELECT * FROM target_points ORDER BY createdAt DESC")
    fun getAllPoints(): Flow<List<TargetPoint>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoint(point: TargetPoint): Long

    @Update
    suspend fun updatePoint(point: TargetPoint)

    @Delete
    suspend fun deletePoint(point: TargetPoint)
}

@Dao
interface RoiDao {
    @Query("SELECT * FROM roi_regions ORDER BY createdAt DESC")
    fun getAllRois(): Flow<List<RoiRegion>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoi(roi: RoiRegion): Long

    @Update
    suspend fun updateRoi(roi: RoiRegion)

    @Delete
    suspend fun deleteRoi(roi: RoiRegion)
}

@Dao
interface MacroDao {
    @Query("SELECT * FROM macros ORDER BY createdAt DESC")
    fun getAllMacros(): Flow<List<MacroEntity>>

    @Query("SELECT * FROM macros WHERE id = :id")
    suspend fun getMacroById(id: Long): MacroEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMacro(macro: MacroEntity): Long

    @Update
    suspend fun updateMacro(macro: MacroEntity)

    @Delete
    suspend fun deleteMacro(macro: MacroEntity)
}

@Dao
interface ScriptDao {
    @Query("SELECT * FROM scripts ORDER BY isPreset DESC, updatedAt DESC")
    fun getAllScripts(): Flow<List<ScriptEntity>>

    @Query("SELECT * FROM scripts ORDER BY isPreset DESC, updatedAt DESC")
    suspend fun getAllScriptsList(): List<ScriptEntity>

    @Query("SELECT * FROM scripts WHERE id = :id")
    suspend fun getScriptById(id: Long): ScriptEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScript(script: ScriptEntity): Long

    @Update
    suspend fun updateScript(script: ScriptEntity)

    @Delete
    suspend fun deleteScript(script: ScriptEntity)

    @Query("SELECT COUNT(*) FROM scripts")
    suspend fun getScriptCount(): Int
}
