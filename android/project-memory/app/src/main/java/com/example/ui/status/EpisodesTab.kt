package com.example.ui.status

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Message
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.Episode
import com.example.domain.model.EpisodeEvent
import com.example.domain.model.FileVersion
import com.example.domain.model.Project
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EpisodesTab(
    projects: List<Project>,
    selectedProjectId: String?,
    episodes: List<Episode>,
    selectedEpisodeId: String?,
    timeline: List<EpisodeEvent>,
    fileVersions: List<FileVersion>,
    onSelectProject: (String) -> Unit,
    onSelectEpisode: (String) -> Unit,
    onCreateEpisode: (projectId: String, title: String, summary: String, tags: List<String>) -> Unit,
    onAppendEvent: (episodeId: String, eventType: String, actor: String, payload: String, fileVersionId: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeProjectId = selectedProjectId ?: projects.firstOrNull()?.id
    val activeProject = projects.find { it.id == activeProjectId }
    val activeEpisode = episodes.find { it.id == selectedEpisodeId } ?: episodes.firstOrNull()

    var showSampleCreator by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Architecture Overview Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Append-Only Episode & Event Log",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Immutable interaction journals with strict monotonic sequence numbers, provenance references to content-addressed vault snapshots, and full audit integrity.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Project selector
        if (projects.isNotEmpty()) {
            Column {
                Text(
                    text = "Active Project",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    projects.forEach { proj ->
                        FilterChip(
                            selected = proj.id == activeProjectId,
                            onClick = { onSelectProject(proj.id) },
                            label = { Text(proj.name) }
                        )
                    }
                }
            }
        }

        // Episode controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Episodes (${episodes.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (activeProjectId != null) {
                Button(
                    onClick = {
                        val count = episodes.size + 1
                        onCreateEpisode(
                            activeProjectId,
                            "Interaction Episode #$count",
                            "Contextual task session for ${activeProject?.name ?: "project"}",
                            listOf("phase-2", "task", "session-$count")
                        )
                    },
                    modifier = Modifier.testTag("create_episode_btn")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Start Episode")
                }
            }
        }

        if (episodes.isEmpty()) {
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No episodes recorded yet",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Start a new interaction episode to track user messages, agent actions, and file snapshots.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Episode cards list
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                episodes.forEach { ep ->
                    val isSelected = ep.id == activeEpisode?.id
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectEpisode(ep.id) }
                            .then(
                                if (isSelected) {
                                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                } else Modifier
                            ),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = ep.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = ep.source.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            if (ep.summary.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = ep.summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    ep.tags.forEach { tag ->
                                        AssistChip(
                                            onClick = {},
                                            label = { Text(tag, style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }
                                Text(
                                    text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ep.createdAt)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }

            // Timeline of active episode
            if (activeEpisode != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Chronological Timeline",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${timeline.size} sequential events logged",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Append event button
                            Button(
                                onClick = {
                                    val nextSeq = timeline.size + 1
                                    val eventType = when (nextSeq % 4) {
                                        1 -> "message"
                                        2 -> "file_write"
                                        3 -> "tool_call"
                                        else -> "decision"
                                    }
                                    val actor = if (eventType == "message") "user" else "agent"
                                    val latestVersion = fileVersions.firstOrNull()
                                    val provenanceFv = if (eventType == "file_write") latestVersion?.id else null
                                    val payload = when (eventType) {
                                        "message" -> "User instruction for step $nextSeq"
                                        "file_write" -> "Indexed and wrote ${latestVersion?.path ?: "src/lib.rs"} with SHA-256 snapshot"
                                        "tool_call" -> "{\"tool\":\"index_file_content\",\"status\":\"success\"}"
                                        else -> "Decided architectural invariant: strict append-only semantics"
                                    }
                                    onAppendEvent(activeEpisode.id, eventType, actor, payload, provenanceFv)
                                },
                                modifier = Modifier.testTag("append_event_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Append Event", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        if (timeline.isEmpty()) {
                            Text(
                                text = "No events logged in this episode yet. Click 'Append Event' above to record an immutable interaction.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                timeline.forEach { event ->
                                    val typeColor = when (event.eventType) {
                                        "message" -> Color(0xFF2563EB)
                                        "file_write" -> Color(0xFF059669)
                                        "tool_call" -> Color(0xFFD97706)
                                        "decision" -> Color(0xFF7C3AED)
                                        else -> MaterialTheme.colorScheme.primary
                                    }

                                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    // Sequence badge
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                                        shape = CircleShape,
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Text(
                                                                text = "#${event.sequenceNumber}",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    // Event type chip
                                                    Surface(
                                                        color = typeColor.copy(alpha = 0.15f),
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(
                                                            text = event.eventType.uppercase(),
                                                            color = typeColor,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "by ${event.actor}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.outline
                                                    )
                                                }

                                                Text(
                                                    text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(event.createdAt)),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = event.payload,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 12.sp
                                                    ),
                                                    modifier = Modifier.padding(8.dp)
                                                )
                                            }

                                            // Provenance reference badge
                                            if (event.fileVersionId != null) {
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier
                                                        .background(
                                                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
                                                            RoundedCornerShape(4.dp)
                                                        )
                                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Link,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.tertiary,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "Provenance: Snapshot ${event.fileVersionId.take(8)}...",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
