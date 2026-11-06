package de.uchilab.todo.data

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * Row of the `task` table, data format version 1 (ADR 0003). Exactly these five fields (privacy V2).
 * Not a data class: [toString] shows only id and status, never title or note (security L4).
 */
@Entity(tableName = "task")
class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val title: String,
    val note: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    val status: String,
) {
    override fun toString(): String = "TaskEntity(id=$id, status=$status)"
}
