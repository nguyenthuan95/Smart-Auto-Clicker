package com.example.data.database

import com.example.data.model.MacroEntity
import com.example.data.model.RoiRegion
import com.example.data.model.ScriptEntity
import com.example.data.model.TargetPoint
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow

data class BackupData(
    val points: List<TargetPoint> = emptyList(),
    val rois: List<RoiRegion> = emptyList(),
    val macros: List<MacroEntity> = emptyList(),
    val scripts: List<ScriptEntity> = emptyList(),
    val exportedAt: Long = System.currentTimeMillis()
)

class AppRepository(
    private val pointDao: PointDao,
    private val roiDao: RoiDao,
    private val macroDao: MacroDao,
    private val scriptDao: ScriptDao
) {
    val allPoints: Flow<List<TargetPoint>> = pointDao.getAllPoints()
    val allRois: Flow<List<RoiRegion>> = roiDao.getAllRois()
    val allMacros: Flow<List<MacroEntity>> = macroDao.getAllMacros()
    val allScripts: Flow<List<ScriptEntity>> = scriptDao.getAllScripts()

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    // Points
    suspend fun savePoint(point: TargetPoint): Long = pointDao.insertPoint(point)
    suspend fun updatePoint(point: TargetPoint) = pointDao.updatePoint(point)
    suspend fun deletePoint(point: TargetPoint) = pointDao.deletePoint(point)

    // ROIs
    suspend fun saveRoi(roi: RoiRegion): Long = roiDao.insertRoi(roi)
    suspend fun updateRoi(roi: RoiRegion) = roiDao.updateRoi(roi)
    suspend fun deleteRoi(roi: RoiRegion) = roiDao.deleteRoi(roi)

    // Macros
    suspend fun saveMacro(macro: MacroEntity): Long = macroDao.insertMacro(macro)
    suspend fun updateMacro(macro: MacroEntity) = macroDao.updateMacro(macro)
    suspend fun deleteMacro(macro: MacroEntity) = macroDao.deleteMacro(macro)
    suspend fun getMacro(id: Long): MacroEntity? = macroDao.getMacroById(id)

    // Scripts
    suspend fun saveScript(script: ScriptEntity): Long = scriptDao.insertScript(script)
    suspend fun updateScript(script: ScriptEntity) = scriptDao.updateScript(script)
    suspend fun deleteScript(script: ScriptEntity) = scriptDao.deleteScript(script)
    suspend fun getScript(id: Long): ScriptEntity? = scriptDao.getScriptById(id)
    suspend fun getAllScriptsList(): List<ScriptEntity> = scriptDao.getAllScriptsList()

    // Export to JSON string
    fun exportToJson(data: BackupData): String {
        return gson.toJson(data)
    }

    // Import from JSON string
    fun importFromJson(jsonString: String): BackupData? {
        return try {
            val type = object : TypeToken<BackupData>() {}.type
            gson.fromJson<BackupData>(jsonString, type)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
