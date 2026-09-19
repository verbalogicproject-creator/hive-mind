package com.example.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.FileVersion

@Entity(
    tableName = "file_versions",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = MemoryLaneEntity::class,
            parentColumns = ["id"],
            childColumns = ["lane_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["project_id", "path"], name = "index_file_versions_project_path"),
        Index(value = ["sha256"], name = "index_file_versions_sha256"),
        Index(value = ["lane_id"], name = "index_file_versions_lane_id")
    ]
)
data class FileVersionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "project_id")
    val projectId: String,

    @ColumnInfo(name = "lane_id")
    val laneId: String,

    @ColumnInfo(name = "path")
    val path: String,

    @ColumnInfo(name = "sha256")
    val sha256: String,

    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long,

    @ColumnInfo(name = "mime_type")
    val mimeType: String,

    @ColumnInfo(name = "version_number")
    val versionNumber: Int,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
) {
    fun toDomain(): FileVersion = FileVersion(
        id = id,
        projectId = projectId,
        laneId = laneId,
        path = path,
        sha256 = sha256,
        sizeBytes = sizeBytes,
        mimeType = mimeType,
        versionNumber = versionNumber,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(model: FileVersion): FileVersionEntity = FileVersionEntity(
            id = model.id,
            projectId = model.projectId,
            laneId = model.laneId,
            path = model.path,
            sha256 = model.sha256,
            sizeBytes = model.sizeBytes,
            mimeType = model.mimeType,
            versionNumber = model.versionNumber,
            createdAt = model.createdAt
        )
    }
}
