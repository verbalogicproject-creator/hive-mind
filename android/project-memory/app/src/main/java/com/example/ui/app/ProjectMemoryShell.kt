package com.example.ui.app

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.domain.model.AgentMemoryEntry
import com.example.domain.model.AgentSessionSummary
import com.example.domain.model.AgentSummary
import com.example.domain.model.MemoryEntryDetail
import com.example.domain.model.FileVersion
import com.example.domain.model.Project
import com.example.preferences.UserPreferencesRepository
import com.example.ui.graph.GraphNode
import com.example.ui.graph.GraphProjection
import com.example.ui.graph.GraphWebView
import com.example.ui.status.ServerStatusViewModel
import kotlinx.coroutines.launch

private data class Destination(val route: String, val label: String, val icon: ImageVector)
private val destinations = listOf(
  Destination("home", "Home", Icons.Default.Home),
  Destination("memory", "Memory", Icons.Default.Memory),
  Destination("agents", "Agents", Icons.Default.People),
  Destination("graph", "Graph", Icons.Default.Hub),
  Destination("settings", "Settings", Icons.Default.Settings)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectMemoryShell(
  viewModel: ServerStatusViewModel,
  preferences: UserPreferencesRepository,
  onCreateProject: (uri: Uri, suggestedName: String) -> Unit
) {
  val nav = rememberNavController()
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val preferencesState by preferences.values.collectAsState(initial = com.example.preferences.UserPreferences())
  val projects by viewModel.projects.collectAsState()
  val selectedProjectId by viewModel.selectedProjectId.collectAsState()
  val files by viewModel.fileVersions.collectAsState()
  val episodes by viewModel.episodes.collectAsState()
  val agents by viewModel.agentSummaries.collectAsState()
  val agentSessions by viewModel.selectedAgentSessions.collectAsState()
  val agentMemory by viewModel.selectedAgentMemory.collectAsState()
  val projectMemory by viewModel.projectMemory.collectAsState()
  val graphDomain by viewModel.graphProjection.collectAsState()
  val memoryEntryDetail by viewModel.memoryEntryDetail.collectAsState()
  val server by viewModel.serverState.collectAsState()
  val console by viewModel.consoleOutput.collectAsState()
  val selectedProject = projects.firstOrNull { it.id == selectedProjectId }
  var folderPermissionError by remember { mutableStateOf<String?>(null) }
  LaunchedEffect(projects, preferencesState.activeProjectId) {
    val target = preferencesState.activeProjectId?.takeIf { wanted -> projects.any { it.id == wanted } } ?: projects.firstOrNull()?.id
    if (target != selectedProjectId) viewModel.selectProject(target)
  }
  val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
    if (uri != null) {
      runCatching {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
      }.onSuccess {
        onCreateProject(uri, suggestedProjectName(uri))
      }.onFailure {
        folderPermissionError = "Project Memory could not keep access to this folder. Choose a folder that allows persistent read and write access."
      }
    }
  }
  val selectProject: (String) -> Unit = { id ->
    viewModel.selectProject(id)
    scope.launch { preferences.setActiveProject(id) }
  }
  Scaffold(
    topBar = { TopAppBar(title = { Column { Text("Project Memory"); Text("Shared memory for your AI assistants", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }) },
    bottomBar = { BottomNavigation(nav) }
  ) { padding ->
    NavHost(nav, "home", Modifier.padding(padding)) {
      composable("home") { HomeScreen(projects, selectedProject, server.isRunning, agents.size, files.firstOrNull(), selectProject, { nav.navigate("project/detail/$it") }, { folderPicker.launch(null) }) }
      composable("project/detail/{id}") { entry -> ProjectDetail(projects.firstOrNull { it.id == entry.arguments?.getString("id") }, nav::popBackStack) }
      composable("memory") { MemoryScreen(selectedProject, projectMemory, files, episodes, { viewModel.loadMemoryDetail(it); nav.navigate("memory/entry/$it") }, { nav.navigate("memory/file/$it") }, { folderPicker.launch(null) }) }
      composable("memory/entry/{id}") { MemoryEntryDetailScreen(memoryEntryDetail, nav::popBackStack) }
      composable("memory/file/{id}") { entry -> MemoryDetail(files.firstOrNull { it.id == entry.arguments?.getString("id") }, nav::popBackStack) }
      composable("agents") { AgentsScreen(selectedProject, agents, { viewModel.selectAgent(it); nav.navigate("agents/detail/$it") }, { nav.navigate("agents/connect") }) }
      composable("agents/detail/{id}") { entry -> AgentDetail(agents.firstOrNull { it.installationId == entry.arguments?.getString("id") }, agentSessions, agentMemory, nav::popBackStack) }
      composable("agents/connect") { ConnectAgentScreen(projects, selectedProjectId, viewModel, nav::popBackStack) }
      composable("graph") {
        var selectedNode by remember { mutableStateOf<GraphNode?>(null) }
        val projection = remember(graphDomain) { GraphProjection.fromDomain(graphDomain) }
        if (selectedProject == null) EmptyState("Choose a project to see its relationships.", "Choose project") { nav.navigate("home") }
        else Column(Modifier.fillMaxSize()) {
          if (projection.truncated) Text("Showing the newest ${projection.nodes.size} items. Refine the graph to see more.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
          GraphWebView(projection, { selectedNode = it }, Modifier.weight(1f))
        }
        selectedNode?.let { node -> ModalBottomSheet(onDismissRequest = { selectedNode = null }) { EvidenceDetail(node) } }
      }
      composable("settings") { SettingsScreen(server.isRunning, server.isReadOnly, selectedProjectId, preferencesState.darkTheme, viewModel, { scope.launch { preferences.setDarkTheme(it) } }, { nav.navigate("settings/connection") }, { folderPicker.launch(null) }) }
      composable("settings/connection") { ConnectionDetails(viewModel, nav::popBackStack) }
    }
  }
  folderPermissionError?.let { message ->
    ModalBottomSheet(onDismissRequest = { folderPermissionError = null }) {
      Column(Modifier.padding(24.dp)) {
        Text("Folder access needed", style = MaterialTheme.typography.titleLarge)
        Text(message, Modifier.padding(vertical = 12.dp))
        Button({ folderPermissionError = null }, Modifier.fillMaxWidth().height(48.dp)) { Text("Choose another folder") }
      }
    }
  }
  console?.takeIf { it.title == "Action could not be completed" }?.let { error ->
    ModalBottomSheet(onDismissRequest = viewModel::clearConsole) {
      Column(Modifier.padding(24.dp)) {
        Text(error.title, style = MaterialTheme.typography.titleLarge)
        Text(error.content, Modifier.padding(vertical = 12.dp))
        Button(viewModel::clearConsole, Modifier.fillMaxWidth().height(48.dp)) { Text("Close") }
      }
    }
  }
}

@Composable
private fun BottomNavigation(nav: NavHostController) {
  val route = nav.currentBackStackEntryAsState().value?.destination?.route.orEmpty()
  NavigationBar(modifier = Modifier.testTag("bottom_navigation")) {
    destinations.forEach { destination -> NavigationBarItem(
      selected = route.substringBefore('/') == destination.route,
      onClick = { nav.navigate(destination.route) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } },
      icon = { Icon(destination.icon, null) }, label = { Text(destination.label) }, modifier = Modifier.testTag("nav_${destination.route}")
    ) }
  }
}

@Composable
private fun ScreenList(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) = LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)

@Composable
private fun ProjectPicker(projects: List<Project>, selected: Project?, select: (String) -> Unit) {
  Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    projects.forEach { FilterChip(it.id == selected?.id, { select(it.id) }, { Text(it.name) }) }
  }
}

@Composable
private fun HomeScreen(projects: List<Project>, selected: Project?, serverRunning: Boolean, agentCount: Int, latest: FileVersion?, select: (String) -> Unit, detail: (String) -> Unit, create: () -> Unit) = ScreenList {
  item { Text("Your memory library", style = MaterialTheme.typography.headlineSmall) }
  if (projects.isEmpty()) item { EmptyState("Create a project to choose where private memory is stored.", "Create project", create) }
  else {
    item { ProjectPicker(projects, selected, select) }
    selected?.let { project -> item { OutlinedButton({ detail(project.id) }, Modifier.fillMaxWidth().height(48.dp)) { Text("Project details") } } }
    item { StatusCard(if (serverRunning) "Memory server running" else "Memory server stopped", if (serverRunning) "Agents on this device can connect." else "Start it in Settings when you want agents to connect.") }
    item { StatusCard("Connected agents", "$agentCount paired installation${if (agentCount == 1) "" else "s"}") }
    item { StatusCard("Latest memory", latest?.path?.substringAfterLast('/') ?: "Index a file from Developer tools to see it here.") }
  }
}

@Composable private fun StatusCard(title: String, body: String) = Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant) } }

@Composable
private fun MemoryScreen(
  project: Project?,
  memory: List<AgentMemoryEntry>,
  files: List<FileVersion>,
  episodes: List<com.example.domain.model.Episode>,
  memoryDetail: (String) -> Unit,
  fileDetail: (String) -> Unit,
  create: () -> Unit
) = ScreenList {
  item { Text("Memory", style = MaterialTheme.typography.headlineSmall) }
  if (project == null) item { EmptyState("Create a project before adding memory.", "Create project", create) }
  else if (memory.isEmpty() && files.isEmpty() && episodes.isEmpty()) item {
    EmptyState("Connect an agent or index a file to begin ${project.name}'s memory library.", "Open Settings") {}
  } else {
    items(memory, key = { it.versionId }) { entry ->
      ListItem(
        headlineContent = { Text(entry.title) },
        supportingContent = { Text("${entry.itemType.replaceFirstChar { it.uppercase() }} · immutable version ${entry.sequenceNumber}") },
        modifier = Modifier.clickable { memoryDetail(entry.versionId) }
      )
    }
    items(files, key = { it.id }) { file ->
      ListItem(
        headlineContent = { Text(file.path.substringAfterLast('/')) },
        supportingContent = { Text("File · version ${file.versionNumber}") },
        modifier = Modifier.clickable { fileDetail(file.id) }
      )
    }
    items(episodes, key = { it.id }) { episode ->
      ListItem(headlineContent = { Text(episode.title) }, supportingContent = { Text("Episode record") })
    }
  }
}

@Composable
private fun AgentsScreen(project: Project?, agents: List<AgentSummary>, detail: (String) -> Unit, connect: () -> Unit) = ScreenList {
  item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Agents", style = MaterialTheme.typography.headlineSmall); Button(connect, modifier = Modifier.height(48.dp)) { Icon(Icons.Default.Add, null); Text("Connect agent") } } }
  if (project == null) item { EmptyState("Choose a project before connecting an agent.", "Choose project") {} }
  else if (agents.isEmpty()) item { EmptyState("Connect an AI assistant to share approved memory from ${project.name}.", "Connect agent", connect) }
  else items(agents, key = { it.installationId }) { agent -> ListItem(
    headlineContent = { Text(agent.installationName) },
    supportingContent = { Text("${agent.connectionState.name.lowercase().replaceFirstChar { it.uppercase() }} · ${agent.sessionCount} sessions · ${agent.memoryCount} memories") },
    trailingContent = { Text("Details") }, modifier = Modifier.clickable { detail(agent.installationId) })
  }
}
@Composable
private fun ConnectAgentScreen(projects: List<Project>, selectedId: String?, viewModel: ServerStatusViewModel, back: () -> Unit) {
  var name by remember { mutableStateOf("Codex") }
  val pairing by viewModel.pairingToken.collectAsState()
  ScreenList {
    item { DetailHeader("Connect agent", back) }
    item { androidx.compose.material3.OutlinedTextField(name, { name = it }, label = { Text("Agent name") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
    item { Button(onClick = { selectedId?.let { viewModel.createPairingToken(it, name) } }, enabled = selectedId != null, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Create connection") } }
    pairing?.let { reveal -> item { StatusCard("Use this token once", reveal.token) } }
    if (projects.isEmpty()) item { Text("Create a project before connecting an agent.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
  }
}

@Composable
private fun SettingsScreen(running: Boolean, readOnly: Boolean, selectedProjectId: String?, dark: Boolean, viewModel: ServerStatusViewModel, setDark: (Boolean) -> Unit, details: () -> Unit, create: () -> Unit) {
  val context = LocalContext.current
  val console by viewModel.consoleOutput.collectAsState()
  var developerExpanded by remember { mutableStateOf(false) }
  ScreenList {
    item { Text("Settings", style = MaterialTheme.typography.headlineSmall) }
    item { StatusCard(if (running) "Memory server running" else "Memory server stopped", if (readOnly) "Read-only access" else "Approved write access") }
    item { StatusCard("Local Device Owner", "Private on-device owner access") }
    item { ListItem(headlineContent = { Text("Read-only access") }, supportingContent = { Text(if (running) "Stop the server to change access" else "Allow only memory reads") }, trailingContent = { Switch(readOnly, viewModel::setServerReadOnly, enabled = !running) }) }
    item { OutlinedButton(details, Modifier.fillMaxWidth().height(48.dp)) { Text("Connection details") } }
    item { Button({ viewModel.toggleServer(context) }, Modifier.fillMaxWidth().height(48.dp)) { Text(if (running) "Stop server" else "Start server") } }
    item { ListItem(headlineContent = { Text("Dark appearance") }, supportingContent = { Text("Uses the Project Memory charcoal palette") }, trailingContent = { Switch(dark, setDark) }) }
    item { OutlinedButton(create, Modifier.fillMaxWidth().height(48.dp)) { Icon(Icons.Default.Folder, null); Text("Create project") } }
    item { TextButton({ developerExpanded = !developerExpanded }, Modifier.fillMaxWidth().height(48.dp)) { Text(if (developerExpanded) "Hide Developer tools" else "Developer tools") } }
    if (developerExpanded) {
      item { HorizontalDivider() }
      item { Text("Indexing tests and MCP diagnostics are intended for development. IDs, hashes, and ports appear only here.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
      item { OutlinedButton({ viewModel.runSystemStatusToolDirectly() }, Modifier.fillMaxWidth().height(48.dp)) { Text("Run status check") } }
      item { OutlinedButton({ viewModel.runReadManifestResourceDirectly() }, Modifier.fillMaxWidth().height(48.dp)) { Text("Read manifest") } }
      item { OutlinedButton({ selectedProjectId?.let { viewModel.indexSampleFile(it, "developer/test-note.md", "Project Memory indexing check") } }, Modifier.fillMaxWidth().height(48.dp), enabled = selectedProjectId != null) { Text("Index test note") } }
      console?.let { output -> item { StatusCard(output.title, output.content) } }
    }
  }
}

@Composable private fun ProjectDetail(project: Project?, back: () -> Unit) = ScreenList {
  item { DetailHeader("Project details", back) }
  if (project == null) item { Text("This project is no longer available.") } else {
    item { Text(project.name, style = MaterialTheme.typography.titleLarge) }
    item { Text(project.description.ifBlank { "Memory stays in the selected on-device folder." }) }
    item { Identifier("Folder", project.rootUri); Identifier("Project ID", project.id) }
  }
}

@Composable private fun ConnectionDetails(viewModel: ServerStatusViewModel, back: () -> Unit) {
  val port by viewModel.portInput.collectAsState()
  val readOnly by viewModel.serverReadOnly.collectAsState()
  ScreenList {
    item { DetailHeader("Connection details", back) }
    item { Text(if (readOnly) "Read-only access" else "Approved read and write access") }
    item { Identifier("Local address", "127.0.0.1:$port/mcp") }
    item { Text("Connections stay on this device and require a paired agent credential.") }
  }
}


@Composable private fun MemoryDetail(file: FileVersion?, back: () -> Unit) = ScreenList {
  item { DetailHeader("Memory details", back) }
  if (file == null) item { Text("This memory entry is no longer available.") } else {
    item { Text(file.path.substringAfterLast('/'), style = MaterialTheme.typography.titleLarge) }
    item { Identifier("Path", file.path); Identifier("Content hash", file.sha256); Identifier("Version ID", file.id) }
  }
}

@Composable private fun MemoryEntryDetailScreen(detail: MemoryEntryDetail?, back: () -> Unit) = ScreenList {
  item { DetailHeader("Memory details", back) }
  if (detail == null) item { Text("Loading the authorized memory entry…") } else {
    item { Text(detail.title, style = MaterialTheme.typography.titleLarge) }
    item { Text(detail.payloadJson) }
    item { Identifier("Source", detail.sourceUri); Identifier("Content hash", detail.contentHash); Identifier("Session", detail.sessionId) }
  }
}

@Composable private fun AgentDetail(
  agent: AgentSummary?,
  sessions: List<AgentSessionSummary>,
  memory: List<AgentMemoryEntry>,
  back: () -> Unit
) = ScreenList {
  item { DetailHeader("Agent details", back) }
  if (agent == null) item { Text("This agent is no longer available.") } else {
    item { Text(agent.installationName, style = MaterialTheme.typography.titleLarge) }
    item { Text("${agent.connectionState.name.lowercase().replaceFirstChar { it.uppercase() }} · saved history remains available") }
    if (sessions.isEmpty() && memory.isEmpty()) item { Text("Start a session with this agent to see its memory history.") }
    items(sessions, key = { it.sessionId }) { session ->
      ListItem(
        headlineContent = { Text("Session ${session.sessionId.take(8)}") },
        supportingContent = { Text("${session.status.value} · ${session.reportedProvider ?: "Unknown provider"} · ${session.reportedModel ?: "Unknown model"}") }
      )
    }
    items(memory, key = { it.versionId }) { entry ->
      ListItem(
        headlineContent = { Text(entry.title) },
        supportingContent = { Text("${entry.itemType} · immutable #${entry.sequenceNumber}") }
      )
    }
    item { Identifier("Installation ID", agent.installationId) }
  }
}

@Composable private fun DetailHeader(title: String, back: () -> Unit) = Row(verticalAlignment = Alignment.CenterVertically) { IconButton(back, Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }; Text(title, style = MaterialTheme.typography.headlineSmall) }
@Composable private fun Identifier(label: String, value: String) { Text(label, style = MaterialTheme.typography.labelLarge); Text(value, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr), color = MaterialTheme.colorScheme.onSurfaceVariant) }
@Composable private fun EvidenceDetail(node: GraphNode) = Column(Modifier.padding(24.dp)) { Text(node.label, style = MaterialTheme.typography.titleLarge); Spacer(Modifier.height(12.dp)); Text(node.type.replaceFirstChar { it.uppercase() }); Spacer(Modifier.height(12.dp)); Identifier("Source", node.sourceUri); Spacer(Modifier.height(24.dp)) }

@Composable
private fun EmptyState(message: String, action: String, onAction: () -> Unit) = Card(Modifier.fillMaxWidth().testTag("empty_state")) { Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(message); Spacer(Modifier.height(16.dp)); Button(onAction, Modifier.height(48.dp)) { Text(action) } } }

private fun suggestedProjectName(uri: Uri): String = runCatching {
  DocumentsContract.getTreeDocumentId(uri).substringAfterLast(':').substringAfterLast('/').ifBlank { "New project" }
}.getOrDefault("New project")
