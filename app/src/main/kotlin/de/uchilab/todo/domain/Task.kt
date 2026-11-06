package de.uchilab.todo.domain

import java.time.Instant

@JvmInline
value class TaskId(val value: Long)

enum class TaskStatus { OPEN, DONE }

/**
 * A stored task. Deliberately not a data class: [toString] shows only id and status,
 * never title or note (security L4, privacy V5).
 */
class Task(
    val id: TaskId,
    val content: TaskContent,
    val createdAt: Instant,
    val status: TaskStatus,
) {
    override fun equals(other: Any?): Boolean =
        other is Task && id == other.id && content == other.content &&
            createdAt == other.createdAt && status == other.status

    override fun hashCode(): Int = ((id.hashCode() * 31 + content.hashCode()) * 31 + createdAt.hashCode()) * 31 + status.hashCode()

    override fun toString(): String = "Task(id=${id.value}, status=$status)"
}
