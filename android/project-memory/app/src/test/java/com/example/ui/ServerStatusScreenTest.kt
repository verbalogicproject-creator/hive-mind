package com.example.ui

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.vault.DiskVaultStorage
import com.example.domain.FakeEpisodeRepository
import com.example.domain.FakeFileVersionRepository
import com.example.domain.FakeProjectRepository
import com.example.domain.repository.IndexTargetValidator
import com.example.domain.repository.ValidatedIndexTarget
import com.example.domain.security.SecurityPolicy
import com.example.domain.usecase.AppendEpisodeEventUseCase
import com.example.domain.usecase.CreateEpisodeUseCase
import com.example.domain.usecase.GetEpisodeTimelineUseCase
import com.example.domain.usecase.GetEpisodesUseCase
import com.example.domain.usecase.GetFileVersionsUseCase
import com.example.domain.usecase.GetProjectsUseCase
import com.example.domain.usecase.GetSystemStatusUseCase
import com.example.domain.usecase.IndexFileContentUseCase
import com.example.domain.usecase.ReadStaticResourceUseCase
import com.example.domain.usecase.ReadVaultBlobUseCase
import com.example.domain.usecase.SaveProjectUseCase
import com.example.mcp.McpServerManager
import com.example.preferences.UserPreferencesRepository
import com.example.ui.app.ProjectMemoryShell
import com.example.ui.status.ServerStatusViewModel
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp")
class ServerStatusScreenTest {
    @get:Rule val composeTestRule = createComposeRule()
    @get:Rule val tempFolder = TemporaryFolder()

    private lateinit var viewModel: ServerStatusViewModel
    private lateinit var preferences: UserPreferencesRepository

    @Before
    fun setup() {
        val projects = FakeProjectRepository()
        val files = FakeFileVersionRepository()
        val episodes = FakeEpisodeRepository()
        val vault = DiskVaultStorage(tempFolder.newFolder("vault_ui_test"))
        val status = GetSystemStatusUseCase(projects)
        val index = IndexFileContentUseCase(
            vault,
            files,
            IndexTargetValidator { projectId, laneId -> ValidatedIndexTarget(projectId, laneId) }
        )
        val mcp = McpServerManager(
            getSystemStatusUseCase = status,
            readStaticResourceUseCase = ReadStaticResourceUseCase(),
            indexFileContentUseCase = index,
            getFileVersionsUseCase = GetFileVersionsUseCase(files),
            readVaultBlobUseCase = ReadVaultBlobUseCase(vault),
            createEpisodeUseCase = CreateEpisodeUseCase(episodes),
            appendEpisodeEventUseCase = AppendEpisodeEventUseCase(episodes),
            getEpisodesUseCase = GetEpisodesUseCase(episodes),
            getEpisodeTimelineUseCase = GetEpisodeTimelineUseCase(episodes),
            securityPolicy = SecurityPolicy()
        )
        viewModel = ServerStatusViewModel(
            mcpServerManager = mcp,
            getProjectsUseCase = GetProjectsUseCase(projects),
            saveProjectUseCase = SaveProjectUseCase(projects),
            indexFileContentUseCase = index,
            getFileVersionsUseCase = GetFileVersionsUseCase(files),
            vaultStorage = vault,
            createEpisodeUseCase = CreateEpisodeUseCase(episodes),
            appendEpisodeEventUseCase = AppendEpisodeEventUseCase(episodes),
            getEpisodesUseCase = GetEpisodesUseCase(episodes),
            getEpisodeTimelineUseCase = GetEpisodeTimelineUseCase(episodes)
        )
        preferences = UserPreferencesRepository(ApplicationProvider.getApplicationContext<Context>())
    }

    @Test
    fun fiveDestinationsAndActionableEmptyState_fitNarrowScreen() {
        composeTestRule.setContent {
            ProjectMemoryShell(viewModel, preferences, onCreateProject = { _, _ -> })
        }

        listOf("home", "memory", "agents", "graph", "settings").forEach {
            composeTestRule.onNodeWithTag("nav_$it").assertIsDisplayed()
        }
        composeTestRule.onNodeWithText("Create a project to choose where private memory is stored.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Create project").assertIsDisplayed()
    }

    @Test
    fun largeTextAndRtl_keepNavigationUsable() {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalDensity provides Density(density = 1f, fontScale = 1.6f)
            ) {
                ProjectMemoryShell(viewModel, preferences, onCreateProject = { _, _ -> })
            }
        }

        composeTestRule.onNodeWithTag("nav_agents").performClick()
        composeTestRule.onNodeWithText("Agents").assertIsDisplayed()
        composeTestRule.onNodeWithText("Project Memory").assertIsDisplayed()
    }
}
