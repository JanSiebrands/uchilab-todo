package de.uchilab.todo.data

import de.uchilab.todo.domain.Task
import de.uchilab.todo.domain.TaskContent
import de.uchilab.todo.domain.TaskId
import de.uchilab.todo.domain.TaskReadException
import de.uchilab.todo.domain.TaskStatus
import de.uchilab.todo.domain.Validated
import java.time.Instant

private const val STATUS_OPEN = "open"
private const val STATUS_DONE = "done"

/** Explicit mapping, not `Enum.name`: R8 or a rename must not change the stored format. */
internal fun TaskStatus.toStored(): String = when (this) {
    TaskStatus.OPEN -> STATUS_OPEN
    TaskStatus.DONE -> STATUS_DONE
}

internal fun statusFromStored(value: String): TaskStatus = when (value) {
    STATUS_OPEN -> TaskStatus.OPEN
    STATUS_DONE -> TaskStatus.DONE
    else -> throw TaskReadException() // a damaged value is a read failure, never includes the value
}

internal fun TaskEntity.toDomain(): Task {
    val content = (TaskContent.of(title, note) as? Validated.Valid)?.content
        ?: throw TaskReadException() // stored values outside the rules: read failure instead of a crash
    return Task(TaskId(id), content, Instant.ofEpochMilli(createdAt), statusFromStored(status))
}
