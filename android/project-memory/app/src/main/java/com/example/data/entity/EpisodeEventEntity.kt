package com.example.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.EpisodeEvent

@Entity(
    tableName = "episode_events",
    foreignKeys = [
        ForeignKey(
            entity = EpisodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["episode_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = FileVersionEntity::class,
            parentColumns = ["id"],
            childColumns = ["file_version_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["episode_id", "sequence_number"], name = "index_episode_events_seq"),
        Index(value = ["file_version_id"], name = "index_episode_events_file_version")
    ]
)
data class EpisodeEventEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "episode_id")
    val episodeId: String,

    @ColumnInfo(name = "sequence_number")
    val sequenceNumber: Long,

    @ColumnInfo(name = "event_type")
    val eventType: String,

    @ColumnInfo(name = "actor")
    val actor: String,

    @ColumnInfo(name = "payload")
    val payload: String,

    @ColumnInfo(name = "file_version_id")
    val fileVersionId: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
) {
    fun toDomain(): EpisodeEvent = EpisodeEvent(
        id = id,
        episodeId = episodeId,
        sequenceNumber = sequenceNumber,
        eventType = eventType,
        actor = actor,
        payload = payload,
        fileVersionId = fileVersionId,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(event: EpisodeEvent): EpisodeEventEntity = EpisodeEventEntity(
            id = event.id,
            episodeId = event.episodeId,
            sequenceNumber = event.sequenceNumber,
            eventType = event.eventType,
            actor = event.actor,
            payload = event.payload,
            fileVersionId = event.fileVersionId,
            createdAt = event.createdAt
        )
    }
}
