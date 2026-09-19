package com.example.ui.status

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.domain.model.ClientInstallation
import com.example.domain.model.Project
import com.example.mcp.ServerRuntimeState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerStatusScreen(
    viewModel: ServerStatusViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val serverState by viewModel.serverState.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val consoleOutput by viewModel.consoleOutput.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val portInput by viewModel.portInput.collectAsStateWithLifecycle()
    val fileVersions by viewModel.fileVersions.collectAsStateWithLifecycle()
    val selectedProjectId by viewModel.selectedProjectId.collectAsStateWithLifecycle()
    val selectedChunks by viewModel.selectedChunks.collectAsStateWithLifecycle()
    val vaultBlobCount by viewModel.vaultBlobCount.collectAsStateWithLifecycle()
    val vaultSizeBytes by viewModel.vaultSizeBytes.collectAsStateWithLifecycle()
    val episodes by viewModel.episodes.collectAsStateWithLifecycle()
    val selectedEpisodeId by viewModel.selectedEpisodeId.collectAsStateWithLifecycle()
    val episodeTimeline by viewModel.episodeTimeline.collectAsStateWithLifecycle()
    val serverReadOnly by viewModel.serverReadOnly.collectAsStateWithLifecycle()
    val clientInstallations by viewModel.clientInstallations.collectAsStateWithLifecycle()
    val pairingToken by viewModel.pairingToken.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(if (serverState.isRunning) Color(0xFF10B981) else Color(0xFF94A3B8))
                                .testTag("server_status_badge")
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Project Memory Provider",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Private, provider-neutral memory • v0.1.0",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            PrimaryScrollableTabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth(),
                edgePadding = 8.dp
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    text = { Text("Server", style = MaterialTheme.typography.labelMedium) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    text = { Text("Projects (${projects.size})", style = MaterialTheme.typography.labelMedium) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { viewModel.selectTab(2) },
                    text = { Text("Files", style = MaterialTheme.typography.labelMedium) }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { viewModel.selectTab(3) },
                    text = { Text("Sessions (${episodes.size})", style = MaterialTheme.typography.labelMedium) }
                )
                Tab(
                    selected = selectedTab == 4,
                    onClick = { viewModel.selectTab(4) },
                    text = { Text("Test", style = MaterialTheme.typography.labelMedium) }
                )
                Tab(
                    selected = selectedTab == 5,
                    onClick = { viewModel.selectTab(5) },
                    text = { Text("Connect", style = MaterialTheme.typography.labelMedium) }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                when (selectedTab) {
                    0 -> ServerControlTab(
                        state = serverState,
                        portInput = portInput,
                        configuredReadOnly = serverReadOnly,
                        projectsCount = projects.size,
                        onToggle = { viewModel.toggleServer(context) },
                        onPortChange = { viewModel.updatePort(it) },
                        onReadOnlyChange = { viewModel.setServerReadOnly(it) },
                        onSeedProject = { viewModel.seedSampleProject() }
                    )
                    1 -> ProjectsListTab(
                        projects = projects,
                        onAddSample = { viewModel.seedSampleProject() }
                    )
                    2 -> FilesAndVaultTab(
                        projects = projects,
                        selectedProjectId = selectedProjectId,
                        fileVersions = fileVersions,
                        selectedChunks = selectedChunks,
                        vaultBlobCount = vaultBlobCount,
                        vaultSizeBytes = vaultSizeBytes,
                        onSelectProject = { viewModel.selectProject(it) },
                        onIndexSample = { pId, path, content -> viewModel.indexSampleFile(pId, path, content) },
                        onLoadChunks = { viewModel.loadChunks(it) }
                    )
                    3 -> EpisodesTab(
                        projects = projects,
                        selectedProjectId = selectedProjectId,
                        episodes = episodes,
                        selectedEpisodeId = selectedEpisodeId,
                        timeline = episodeTimeline,
                        fileVersions = fileVersions,
                        onSelectProject = { viewModel.selectProject(it) },
                        onSelectEpisode = { viewModel.selectEpisode(it) },
                        onCreateEpisode = { pId, title, summary, tags ->
                            viewModel.createSampleEpisode(pId, title, summary, tags)
                        },
                        onAppendEvent = { epId, eventType, actor, payload, fvId ->
                            viewModel.appendSampleEvent(epId, eventType, actor, payload, fvId)
                        }
                    )
                    4 -> McpConsoleTab(
                        state = serverState,
                        output = consoleOutput,
                        onRunSystemStatus = { viewModel.runSystemStatusToolDirectly() },
                        onReadManifest = { viewModel.runReadManifestResourceDirectly() },
                        onClear = { viewModel.clearConsole() }
                    )
                    5 -> ClientsTab(
                        projects = projects,
                        installations = clientInstallations,
                        pairingToken = pairingToken,
                        serverPort = portInput,
                        onCreatePairingToken = viewModel::createPairingToken,
                        onClearPairingToken = viewModel::clearPairingToken,
                        onRefresh = viewModel::refreshClients,
                        onRevoke = viewModel::revokeClient
                    )
                }
            }
        }
    }
}

@Composable
fun ServerControlTab(
    state: ServerRuntimeState,
    portInput: String,
    configuredReadOnly: Boolean,
    projectsCount: Int,
    onToggle: () -> Unit,
    onPortChange: (String) -> Unit,
    onReadOnlyChange: (Boolean) -> Unit,
    onSeedProject: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Server Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (state.isRunning)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (state.isRunning) "MEMORY SERVER READY" else "MEMORY SERVER STOPPED",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            color = if (state.isRunning)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "http://${state.host}:${if (state.isRunning) state.port else portInput}/mcp",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(if (state.isRunning) Color(0xFF10B981) else Color(0xFFEF4444))
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TelemetryChip(label = "Connection", value = "Streamable HTTP")
                    TelemetryChip(label = "Access", value = if (state.isReadOnly) "Read only" else "Read + write")
                    TelemetryChip(label = "Projects", value = "$projectsCount")
                }

                if (state.lastError != null) {
                    Text(
                        text = "Error: ${state.lastError}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = onToggle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("server_toggle_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isRunning)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = if (state.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (state.isRunning) "Stop memory server" else "Start memory server",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Configuration card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Connection settings",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = portInput,
                        onValueChange = onPortChange,
                        label = { Text("Port") },
                        enabled = !state.isRunning,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("port_input_field"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = "127.0.0.1",
                        onValueChange = {},
                        label = { Text("Host") },
                        enabled = false,
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Text(
                    text = "Private to this phone (127.0.0.1). Local Codex, Claude Code, Cursor, Antigravity, and other MCP clients can connect after you invite them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Read-only server",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (configuredReadOnly) {
                                "Clients can retrieve authorized context but cannot mutate memory."
                            } else {
                                "Authorized clients can create sessions and append inbox items."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(
                        checked = configuredReadOnly,
                        onCheckedChange = onReadOnlyChange,
                        enabled = !state.isRunning
                    )
                }
            }
        }

        // Quick Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onSeedProject,
                modifier = Modifier
                    .weight(1f)
                    .testTag("seed_project_button")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Seed Project Record", maxLines = 1)
            }
        }
    }
}

@Composable
fun ClientsTab(
    projects: List<Project>,
    installations: List<ClientInstallation>,
    pairingToken: PairingTokenReveal?,
    serverPort: String,
    onCreatePairingToken: (String, String) -> Unit,
    onClearPairingToken: () -> Unit,
    onRefresh: () -> Unit,
    onRevoke: (String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var clientName by rememberSaveable { mutableStateOf("") }
    var selectedProjectId by rememberSaveable { mutableStateOf("") }
    var showTechnicalDetails by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(projects) {
        if (projects.none { it.id == selectedProjectId }) {
            selectedProjectId = projects.firstOrNull()?.id.orEmpty()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Connect an assistant",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Give an AI assistant secure access to one project. Each assistant gets its own connection, so you can disconnect it without affecting the others.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("1. Choose an assistant", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = clientName,
                    onValueChange = { clientName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Connection name") },
                    supportingText = { Text("For example: Codex on this phone or Claude Code laptop") },
                    singleLine = true
                )

                Text("Project", style = MaterialTheme.typography.labelLarge)
                if (projects.isEmpty()) {
                    Text(
                        "Create a project before pairing a client.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        projects.forEach { project ->
                            FilterChip(
                                selected = selectedProjectId == project.id,
                                onClick = { selectedProjectId = project.id },
                                label = { Text(project.name) }
                            )
                        }
                    }
                }

                Button(
                    onClick = { onCreatePairingToken(selectedProjectId, clientName) },
                    enabled = selectedProjectId.isNotBlank() && clientName.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(Icons.Default.Security, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Create secure invite")
                }
            }
        }

        if (pairingToken != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("2. Copy the one-time invite", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Paste this invite into the assistant you named. It expires in 10 minutes, works once, and is never stored as readable text.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Text(
                            text = pairingToken.token,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { clipboardManager.setText(AnnotatedString(pairingToken.token)) }) {
                            Text("Copy token")
                        }
                        TextButton(onClick = onClearPairingToken) {
                            Text("I saved it")
                        }
                    }
                    TextButton(onClick = { showTechnicalDetails = !showTechnicalDetails }) {
                        Text(if (showTechnicalDetails) "Hide technical details" else "Show technical details")
                    }
                    AnimatedVisibility(visible = showTechnicalDetails) {
                        Text(
                            text = "Redeem with POST http://127.0.0.1:${serverPort.ifBlank { "8080" }}/pair, Authorization: Pairing <invite>, and X-Installation-Name. Then use the returned Bearer token at http://127.0.0.1:${serverPort.ifBlank { "8080" }}/mcp with Streamable HTTP.",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("3. Connected assistants", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = onRefresh) { Text("Refresh") }
        }

        if (installations.isEmpty()) {
            Text(
                "No assistants are connected yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            installations.forEach { installation ->
                val isOwner = installation.id == "local-device-owner"
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(installation.installationName, fontWeight = FontWeight.SemiBold)
                            Text(
                                when {
                                    isOwner -> "Device owner • protected"
                                    installation.isRevoked -> "Disconnected"
                                    else -> "Connected"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (installation.isRevoked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                installation.id,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!isOwner && !installation.isRevoked) {
                            OutlinedButton(onClick = { onRevoke(installation.id) }) {
                                Text("Disconnect")
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun ProjectsListTab(
    projects: List<Project>,
    onAddSample: () -> Unit
) {
    if (projects.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No Projects in Room DB",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Room schema v2 active with tested migration. Seed a record to verify Room persistence and MCP reflection.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onAddSample) {
                Text("Seed Sample Project Record")
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tracked Project Records (${projects.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedButton(
                        onClick = onAddSample,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("+ Add Another", fontSize = 12.sp)
                    }
                }
            }

            items(projects, key = { it.id }) { project ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = project.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = project.slug,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        if (project.description.isNotBlank()) {
                            Text(
                                text = project.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = "Root: ${project.rootUri}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun McpConsoleTab(
    state: ServerRuntimeState,
    output: ConsoleOutput?,
    onRunSystemStatus: () -> Unit,
    onReadManifest: () -> Unit,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "MCP Contract Test Console",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onRunSystemStatus,
                modifier = Modifier
                    .weight(1f)
                    .testTag("system_status_tool_button")
            ) {
                Text("tool: system_status", fontSize = 12.sp, maxLines = 1)
            }

            OutlinedButton(
                onClick = onReadManifest,
                modifier = Modifier
                    .weight(1f)
                    .testTag("read_manifest_button")
            ) {
                Text("res: manifest", fontSize = 12.sp, maxLines = 1)
            }
        }

        if (output != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = output.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF38BDF8),
                            fontFamily = FontFamily.Monospace
                        )
                        OutlinedButton(
                            onClick = onClear,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Clear", fontSize = 10.sp, color = Color.White)
                        }
                    }
                    HorizontalDivider(
                        color = Color(0xFF1E293B),
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                    Text(
                        text = output.content,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFE2E8F0),
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Press a button above to run an MCP tool or inspect a static resource.\nOutput will appear here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B),
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun TermuxMigrationTab(
    serverPort: String
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Antigravity CLI on Termux & PRoot-Distro",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Follow these instructions to connect Antigravity CLI, Claude Code, or Codex running inside Termux / PRoot Ubuntu directly to this Project Memory Provider.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        CodeBlockCard(
            title = "1. Test Loopback from Termux / PRoot",
            code = "curl -i http://127.0.0.1:$serverPort/mcp"
        )

        CodeBlockCard(
            title = "2. Antigravity MCP Config (~/.config/antigravity/mcp.json)",
            code = """
{
  "mcpServers": {
    "project-memory": {
      "url": "http://127.0.0.1:$serverPort/mcp"
    }
  }
}
            """.trimIndent()
        )

        CodeBlockCard(
            title = "3. Install PRoot-Distro Ubuntu & Node",
            code = """
pkg update && pkg install proot-distro
proot-distro install ubuntu
proot-distro login ubuntu
apt update && apt install -y curl git nodejs npm
# In PRoot, localhost/127.0.0.1 connects directly to Android host
            """.trimIndent()
        )

        CodeBlockCard(
            title = "4. Antigravity Migration Command",
            code = """
# Run migration spike
npm install -g @google/antigravity-cli || npm install -g @modelcontextprotocol/inspector
mcp-inspector http://127.0.0.1:$serverPort/mcp
            """.trimIndent()
        )
    }
}

@Composable
fun CodeBlockCard(
    title: String,
    code: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = code,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color(0xFF38BDF8)
                )
            }
        }
    }
}

@Composable
fun TelemetryChip(
    label: String,
    value: String
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun FilesAndVaultTab(
    projects: List<com.example.domain.model.Project>,
    selectedProjectId: String?,
    fileVersions: List<com.example.domain.model.FileVersion>,
    selectedChunks: List<com.example.domain.model.FileChunk>,
    vaultBlobCount: Int,
    vaultSizeBytes: Long,
    onSelectProject: (String) -> Unit,
    onIndexSample: (String, String, String) -> Unit,
    onLoadChunks: (String) -> Unit
) {
    val activeProjectId = selectedProjectId ?: projects.firstOrNull()?.id
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Content-Addressed Vault Telemetry",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Zero external cloud requirement. Blobs are hashed via SHA-256 and stored in app-private storage. Identical content is deduplicated automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TelemetryChip(label = "Vault Blobs", value = "$vaultBlobCount blobs")
                    TelemetryChip(label = "Total Size", value = "${vaultSizeBytes} B")
                    TelemetryChip(label = "Tracked Versions", value = "${fileVersions.size} versions")
                }
            }
        }

        if (projects.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "No projects available to index files.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Switch to the 'Server' or 'Projects' tab to seed a sample project first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Select Active Project",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    projects.forEach { proj ->
                        val isSelected = proj.id == activeProjectId
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.testTag("project_chip_${proj.slug}")
                        ) {
                            Button(
                                onClick = { onSelectProject(proj.id) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Text(proj.name, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            if (activeProjectId != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Interactive Indexing & Chunking Pipeline",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Test SHA-256 vault storage, deterministic chunking, and deduplication verification.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    onIndexSample(
                                        activeProjectId,
                                        "src/main.rs",
                                        "fn main() {\n    println!(\"Hello, Antigravity!\");\n    let status = \"Phase 1 Vault\";\n}"
                                    )
                                },
                                modifier = Modifier.weight(1f).testTag("index_sample_btn")
                            ) {
                                Text("Index v1", style = MaterialTheme.typography.labelSmall)
                            }

                            Button(
                                onClick = {
                                    onIndexSample(
                                        activeProjectId,
                                        "src/main.rs",
                                        "fn main() {\n    println!(\"Hello, Antigravity!\");\n    let status = \"Phase 1 Vault\";\n    // Added new routine\n    init_memory_vault();\n}"
                                    )
                                },
                                modifier = Modifier.weight(1f).testTag("index_update_btn")
                            ) {
                                Text("Index v2", style = MaterialTheme.typography.labelSmall)
                            }

                            OutlinedButton(
                                onClick = {
                                    // Re-index identical text to verify deduplication
                                    onIndexSample(
                                        activeProjectId,
                                        "src/main.rs",
                                        "fn main() {\n    println!(\"Hello, Antigravity!\");\n    let status = \"Phase 1 Vault\";\n}"
                                    )
                                },
                                modifier = Modifier.weight(1f).testTag("index_dedup_btn")
                            ) {
                                Text("Dedup Test", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            Text(
                text = "Tracked File Versions (${fileVersions.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            if (fileVersions.isEmpty()) {
                Text(
                    text = "No files indexed yet for this project. Click 'Index v1' above to test.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                fileVersions.forEach { ver ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = ver.path,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "v${ver.versionNumber}",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            Text(
                                text = "SHA-256: ${ver.sha256}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${ver.sizeBytes} bytes • ${ver.mimeType}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedButton(
                                    onClick = { onLoadChunks(ver.id) },
                                    modifier = Modifier.testTag("load_chunks_btn_${ver.versionNumber}")
                                ) {
                                    Text("View Chunks", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }

            if (selectedChunks.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Chunk Breakdown (${selectedChunks.size} Chunks)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    selectedChunks.forEach { chk ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Chunk #${chk.chunkIndex} • ~${chk.tokenCountEstimate} tokens • sha256:${chk.sha256.take(12)}...",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = Color(0xFF38BDF8)
                                )
                                Text(
                                    text = chk.content,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = Color(0xFFF1F5F9)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
