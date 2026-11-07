package de.uchilab.todo.domain

/**
 * Reading the stored tasks failed. Deliberately without message and cause: the message of the storage
 * layer can contain stored values (security L3, privacy V5).
 */
class TaskReadException : Exception()
