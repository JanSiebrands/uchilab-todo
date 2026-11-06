package de.uchilab.todo.data

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

/** Only annotated, parameterised statements (security D6). Update statements never touch `created_at`. */
@Dao
interface TaskDao {
    @Query("SELECT * FROM task ORDER BY id DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Insert
    suspend fun insert(entity: TaskEntity): Long

    @Query("UPDATE task SET title = :title, note = :note WHERE id = :id")
    suspend fun updateContent(id: Long, title: String, note: String?): Int

    @Query("UPDATE task SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String): Int

    @Query("DELETE FROM task WHERE id = :id")
    suspend fun delete(id: Long): Int
}
