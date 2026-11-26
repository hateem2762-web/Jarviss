package com.example.data.local

import android.content.Context
import android.util.Log
import com.example.model.TapCoordinateItem
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "CoordinateJsonStore"
private const val FILE_NAME = "jarvis_tap_coordinates.json"

class CoordinateJsonStore(
    private val context: Context,
    private val dao: JarvisDao,
) {
    suspend fun saveCoordinate(
        profilePackageName: String,
        profileName: String,
        label: String,
        xPercent: Float,
        yPercent: Float,
        xPx: Int,
        yPx: Int,
    ): Long {
        val cleanPkg = profilePackageName.trim().ifBlank { GENERAL_PROFILE_PACKAGE }
        val cleanName = profileName.trim().ifBlank { GENERAL_PROFILE_NAME }
        val cleanLabel = label.trim()

        dao.deleteDuplicateCoordinateLabel(cleanPkg, cleanLabel)
        val id = dao.insertCoordinate(
            TapCoordinateEntity(
                profilePackageName = cleanPkg,
                profileName = cleanName,
                label = cleanLabel,
                xPercent = xPercent.coerceIn(0f, 1f),
                yPercent = yPercent.coerceIn(0f, 1f),
                xPx = xPx,
                yPx = yPx,
            )
        )
        syncJsonBackup()
        return id
    }

    suspend fun deleteCoordinate(id: Long) {
        dao.deleteCoordinate(id)
        syncJsonBackup()
    }

    suspend fun getCoordinatesForActiveOrFallback(activePackageName: String?): List<TapCoordinateItem> {
        val all = dao.getAllCoordinates().map { it.toDomain() }
        if (activePackageName.isNullOrBlank()) {
            return all
        }
        val specific = all.filter { it.profilePackageName.equals(activePackageName, ignoreCase = true) }
        return if (specific.isNotEmpty()) specific else all
    }

    suspend fun syncJsonBackup() = withContext(Dispatchers.IO) {
        try {
            val all = dao.getAllCoordinates()
            val array = JSONArray()
            for (item in all) {
                array.put(
                    JSONObject().apply {
                        put("id", item.id)
                        put("profilePackageName", item.profilePackageName)
                        put("profileName", item.profileName)
                        put("label", item.label)
                        put("xPercent", item.xPercent.toDouble())
                        put("yPercent", item.yPercent.toDouble())
                        put("xPx", item.xPx)
                        put("yPx", item.yPx)
                        put("createdAtMillis", item.createdAtMillis)
                    }
                )
            }
            File(context.filesDir, FILE_NAME).writeText(array.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync coordinate JSON backup", e)
        }
    }

    companion object {
        const val GENERAL_PROFILE_PACKAGE = "manual.general"
        const val GENERAL_PROFILE_NAME = "Genel Profil"
    }
}

fun TapCoordinateEntity.toDomain(): TapCoordinateItem = TapCoordinateItem(
    id = id,
    profilePackageName = profilePackageName,
    profileName = profileName,
    label = label,
    xPercent = xPercent,
    yPercent = yPercent,
    xPx = xPx,
    yPx = yPx,
    createdAtMillis = createdAtMillis,
)
