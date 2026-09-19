package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.example.preferences.UserPreferences
import com.example.preferences.UserPreferencesRepository
import com.example.ui.app.ProjectMemoryShell
import com.example.ui.status.ServerStatusViewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val preferences by lazy { UserPreferencesRepository(applicationContext) }

    private val viewModel: ServerStatusViewModel by viewModels {
        val app = application as ProjectMemoryApp
        ServerStatusViewModel.provideFactory(
            mcpServerManager = app.mcpServerManager,
            getProjectsUseCase = app.getProjectsUseCase,
            saveProjectUseCase = app.saveProjectUseCase,
            indexFileContentUseCase = app.indexFileContentUseCase,
            getFileVersionsUseCase = app.getFileVersionsUseCase,
            vaultStorage = app.vaultStorage,
            createEpisodeUseCase = app.createEpisodeUseCase,
            appendEpisodeEventUseCase = app.appendEpisodeEventUseCase,
            getEpisodesUseCase = app.getEpisodesUseCase,
            getEpisodeTimelineUseCase = app.getEpisodeTimelineUseCase,
            clientIdentityRepository = app.clientIdentityRepository,
            ownerMemoryRepository = app.ownerMemoryRepository,
            getGraphProjectionUseCase = app.getGraphProjectionUseCase
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val userPreferences by preferences.values.collectAsState(initial = UserPreferences())
            MyApplicationTheme(darkTheme = userPreferences.darkTheme) {
                ProjectMemoryShell(
                    viewModel = viewModel,
                    preferences = preferences,
                    onCreateProject = { uri, suggestedName ->
                        lifecycleScope.launch {
                            (application as ProjectMemoryApp).createProjectUseCase(
                                name = suggestedName,
                                rootUri = uri.toString()
                            ).fold(
                                onSuccess = { provisioned ->
                                    preferences.setActiveProject(provisioned.project.id)
                                    viewModel.selectProject(provisioned.project.id)
                                },
                                onFailure = viewModel::reportUiError
                            )
                        }
                    }
                )
            }
        }
    }
}
