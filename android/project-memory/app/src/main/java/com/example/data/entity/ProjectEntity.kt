package com.example.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.Project

@Entity(
    tableName = "projects",
    indices = [
        Index(value = ["slug"], unique = true)
    ]
)
data class ProjectEntity(
    @PrimaryKey
    val id: String,
    val slug: String,
    val name: String,
    @ColumnInfo(name = "description", defaultValue = "''")
    val description: String = "",
    @ColumnInfo(name = "root_uri")
    val rootUri: String,
    @ColumnInfo(name = "created_at")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAtEpochMs: Long
) {
    fun toDomain(): Project = Project(
        id = id,
        slug = slug,
        name = name,
        description = description,
        rootUri = rootUri,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs
    )

    companion object {
        fun fromDomain(project: Project): ProjectEntity = ProjectEntity(
            id = project.id,
            slug = project.slug,
            name = project.name,
            description = project.description,
            rootUri = project.rootUri,
            createdAtEpochMs = project.createdAtEpochMs,
            updatedAtEpochMs = project.updatedAtEpochMs
        )
    }
}
