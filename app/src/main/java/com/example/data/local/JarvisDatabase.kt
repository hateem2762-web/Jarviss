package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val isUser: Boolean,
    val text: String,
    val intentWire: String? = null,
    val target: String? = null,
    val minutes: Int? = null,
    val seconds: Int? = null,
    val actionMessage: String? = null,
    val actionStatus: String = "PENDING",
    val actionStatusDetail: String = "",
    val timestampMillis: Long = System.currentTimeMillis(),
)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val message: String,
    val triggerAtMillis: Long,
    val minutesFromNow: Int? = null,
    val secondsFromNow: Int? = null,
    val durationLabel: String = "",
    val isCompleted: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
)

@Entity(tableName = "tap_coordinates")
data class TapCoordinateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profilePackageName: String,
    val profileName: String,
    val label: String,
    val xPercent: Float,
    val yPercent: Float,
    val xPx: Int,
    val yPx: Int,
    val createdAtMillis: Long = System.currentTimeMillis(),
)

@Dao
interface JarvisDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestampMillis ASC, id ASC")
    fun observeMessages(): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(entity: ChatMessageEntity): Long

    @Query(
        "UPDATE chat_messages SET actionStatus = :status, actionStatusDetail = :detail WHERE id = :id"
    )
    suspend fun updateMessageActionStatus(id: Long, status: String, detail: String)

    @Query("DELETE FROM chat_messages")
    suspend fun clearMessages()

    @Query("SELECT * FROM reminders ORDER BY isCompleted ASC, triggerAtMillis ASC")
    fun observeReminders(): Flow<List<ReminderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(entity: ReminderEntity): Long

    @Query("UPDATE reminders SET isCompleted = :completed WHERE id = :id")
    suspend fun setReminderCompleted(id: Long, completed: Boolean)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteReminder(id: Long)

    @Query("SELECT * FROM tap_coordinates ORDER BY profileName ASC, label ASC")
    fun observeCoordinates(): Flow<List<TapCoordinateEntity>>

    @Query("SELECT * FROM tap_coordinates ORDER BY profileName ASC, label ASC")
    suspend fun getAllCoordinates(): List<TapCoordinateEntity>

    @Query("SELECT * FROM tap_coordinates WHERE profilePackageName = :packageName ORDER BY label ASC")
    suspend fun getCoordinatesForProfile(packageName: String): List<TapCoordinateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCoordinate(entity: TapCoordinateEntity): Long

    @Query("DELETE FROM tap_coordinates WHERE id = :id")
    suspend fun deleteCoordinate(id: Long)

    @Query("DELETE FROM tap_coordinates WHERE profilePackageName = :packageName AND LOWER(label) = LOWER(:label)")
    suspend fun deleteDuplicateCoordinateLabel(packageName: String, label: String)
}

@Database(
    entities = [ChatMessageEntity::class, ReminderEntity::class, TapCoordinateEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class JarvisDatabase : RoomDatabase() {
    abstract fun jarvisDao(): JarvisDao

    companion object {
        @Volatile
        private var INSTANCE: JarvisDatabase? = null

        fun getInstance(context: Context): JarvisDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    JarvisDatabase::class.java,
                    "jarvis_local.db",
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
