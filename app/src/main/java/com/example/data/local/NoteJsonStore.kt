package com.example.data.local

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.model.NoteItem
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "NoteJsonStore"
private const val NOTES_JSON_FILE = "jarvis_notes.json"
private const val SCREENSHOTS_DIR = "note_screenshots"

class NoteJsonStore(
    private val context: Context,
    private val dao: JarvisDao,
) {
    suspend fun saveNote(
        title: String,
        content: String,
        sourcePackage: String,
        sourceAppName: String,
        screenshotBitmap: Bitmap? = null,
    ): Long {
        val now = System.currentTimeMillis()
        val screenshotPath = if (screenshotBitmap != null) {
            saveScreenshotFile(screenshotBitmap, now)
        } else {
            ""
        }

        val id = dao.insertNote(
            NoteEntity(
                title = title.trim().ifBlank { "Ekran Notu" },
                content = content.trim(),
                sourcePackage = sourcePackage.trim(),
                sourceAppName = sourceAppName.trim().ifBlank { "Sistem" },
                screenshotPath = screenshotPath,
                createdAtMillis = now,
                updatedAtMillis = now,
            )
        )
        syncJsonBackup()
        return id
    }

    suspend fun updateNote(id: Long, title: String, content: String) {
        dao.updateNote(
            id = id,
            title = title.trim().ifBlank { "Not" },
            content = content.trim(),
            updatedAtMillis = System.currentTimeMillis(),
        )
        syncJsonBackup()
    }

    suspend fun deleteNote(id: Long) {
        val existing = dao.getNoteById(id)
        dao.deleteNote(id)
        if (!existing?.screenshotPath.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    File(existing!!.screenshotPath).delete()
                } catch (_: Exception) {
                }
            }
        }
        syncJsonBackup()
    }

    private suspend fun saveScreenshotFile(bitmap: Bitmap, timestamp: Long): String =
        withContext(Dispatchers.IO) {
            try {
                val dir = File(context.filesDir, SCREENSHOTS_DIR).apply {
                    if (!exists()) mkdirs()
                }
                val file = File(dir, "note_$timestamp.png")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 92, out)
                }
                file.absolutePath
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save note screenshot PNG", e)
                ""
            }
        }

    suspend fun syncJsonBackup() = withContext(Dispatchers.IO) {
        try {
            val all = dao.getAllNotes()
            val array = JSONArray()
            for (note in all) {
                array.put(
                    JSONObject().apply {
                        put("id", note.id)
                        put("title", note.title)
                        put("content", note.content)
                        put("sourcePackage", note.sourcePackage)
                        put("sourceAppName", note.sourceAppName)
                        put("screenshotPath", note.screenshotPath)
                        put("createdAtMillis", note.createdAtMillis)
                        put("updatedAtMillis", note.updatedAtMillis)
                    }
                )
            }
            File(context.filesDir, NOTES_JSON_FILE).writeText(array.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync notes JSON backup", e)
        }
    }
}

fun NoteEntity.toDomain(): NoteItem = NoteItem(
    id = id,
    title = title,
    content = content,
    sourcePackage = sourcePackage,
    sourceAppName = sourceAppName,
    screenshotPath = screenshotPath,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
)
