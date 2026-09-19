package com.example.domain.repository

data class ValidatedIndexTarget(
    val projectId: String,
    val laneId: String
)

/** Validates a project/lane pair before any vault mutation occurs. */
fun interface IndexTargetValidator {
    suspend fun validate(projectId: String, laneId: String): ValidatedIndexTarget
}
