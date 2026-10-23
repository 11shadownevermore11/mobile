package ru.kodnovorossii.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** Избранное хранится на устройстве: добавление и удаление работают без интернета и без входа. */
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val placeId: Int,
    val json: String,
    val addedAt: Long,
    val deleted: Boolean = false,
    val synced: Boolean = false,
)

/** Кэш офлайн-пакета и ответов API. */
@Entity(tableName = "cache")
data class CacheEntity(
    @PrimaryKey val cacheKey: String,
    val json: String,
    val updatedAt: Long,
)

/** Очередь действий, сделанных без сети (сообщения о неточностях и т.п.) — отправляются при появлении связи. */
@Entity(tableName = "pending")
data class PendingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val method: String,
    val path: String,
    val body: String,
    val createdAt: Long,
)

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites WHERE deleted = 0 ORDER BY addedAt DESC")
    fun observe(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites")
    suspend fun all(): List<FavoriteEntity>

    @Query("SELECT * FROM favorites WHERE deleted = 0")
    suspend fun active(): List<FavoriteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE placeId = :id")
    suspend fun hardDelete(id: Int)
}

@Dao
interface CacheDao {
    @Query("SELECT * FROM cache WHERE cacheKey = :key")
    suspend fun get(key: String): CacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(e: CacheEntity)
}

@Dao
interface PendingDao {
    @Query("SELECT * FROM pending ORDER BY id")
    suspend fun all(): List<PendingEntity>

    @Insert
    suspend fun add(e: PendingEntity)

    @Delete
    suspend fun remove(e: PendingEntity)

    @Query("SELECT COUNT(*) FROM pending")
    fun count(): Flow<Int>
}

@Database(entities = [FavoriteEntity::class, CacheEntity::class, PendingEntity::class], version = 1, exportSchema = false)
abstract class KodDb : RoomDatabase() {
    abstract fun favorites(): FavoriteDao
    abstract fun cache(): CacheDao
    abstract fun pending(): PendingDao

    companion object {
        @Volatile
        private var instance: KodDb? = null

        fun get(context: Context): KodDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, KodDb::class.java, "kod.db")
                .fallbackToDestructiveMigration()
                .build().also { instance = it }
        }
    }
}
