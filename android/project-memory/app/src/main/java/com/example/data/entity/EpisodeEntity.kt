package com.example.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.Episode

@Entity(
    tableName = "episodes",
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
        Index(value = ["project_id", "created_at"], name = "index_episodes_project_created_at"),
        Index(value = ["lane_id"], name = "index_episodes_lane_id")
    ]
)
data class EpisodeEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "project_id")
    val projectId: String,

    @ColumnInfo(name = "lane_id")
    val laneId: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "summary")
    val summary: String,

    @ColumnInfo(name = "source")
    val source: String,

    @ColumnInfo(name = "tags_csv")
    val tagsCsv: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
) {
    fun toDomain(): Episode = Episode(
        id = id,
        projectId = projectId,
        laneId = laneId,
        title = title,
        summary = summary,
        source = source,
        tags = if (tagsCsv.isBlank()) emptyList() else tagsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() },
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(episode: Episode): EpisodeEntity = EpisodeEntity(
            id = episode.id,
            projectId = episode.projectId,
            laneId = episode.laneId,
            title = episode.title,
            summary = episode.summary,
            source = episode.source,
            tagsCsv = episode.tags.joinToString(","),
            createdAt = episode.createdAt
        )
    }
}
