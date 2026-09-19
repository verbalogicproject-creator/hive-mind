package com.example.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.FileChunk

@Entity(
    tableName = "file_chunks",
    foreignKeys = [
        ForeignKey(
            entity = FileVersionEntity::class,
            parentColumns = ["id"],
            childColumns = ["file_version_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["file_version_id"], name = "index_file_chunks_file_version_id")
    ]
)
data class FileChunkEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "file_version_id")
    val fileVersionId: String,

    @ColumnInfo(name = "chunk_index")
    val chunkIndex: Int,

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "token_count_estimate")
    val tokenCountEstimate: Int,

    @ColumnInfo(name = "sha256")
    val sha256: String
) {
    fun toDomain(): FileChunk = FileChunk(
        id = id,
        fileVersionId = fileVersionId,
        chunkIndex = chunkIndex,
        content = content,
        tokenCountEstimate = tokenCountEstimate,
        sha256 = sha256
    )

    companion object {
        fun fromDomain(model: FileChunk): FileChunkEntity = FileChunkEntity(
            id = model.id,
            fileVersionId = model.fileVersionId,
            chunkIndex = model.chunkIndex,
            content = model.content,
            tokenCountEstimate = model.tokenCountEstimate,
            sha256 = model.sha256
        )
    }
}
