package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.domain.FakeProjectRepository
import com.example.domain.security.SecurityPolicy
import com.example.domain.usecase.GetProjectsUseCase
import com.example.domain.usecase.GetSystemStatusUseCase
import com.example.domain.usecase.ReadStaticResourceUseCase
import com.example.domain.usecase.SaveProjectUseCase
import com.example.mcp.McpServerManager
import com.example.ui.status.ServerStatusScreen
import com.example.ui.status.ServerStatusViewModel
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    val repository = FakeProjectRepository()
    val mcpServerManager = McpServerManager(
      getSystemStatusUseCase = GetSystemStatusUseCase(repository),
      readStaticResourceUseCase = ReadStaticResourceUseCase(),
      securityPolicy = SecurityPolicy()
    )
    val viewModel = ServerStatusViewModel(
      mcpServerManager = mcpServerManager,
      getProjectsUseCase = GetProjectsUseCase(repository),
      saveProjectUseCase = SaveProjectUseCase(repository)
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        ServerStatusScreen(viewModel = viewModel)
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
