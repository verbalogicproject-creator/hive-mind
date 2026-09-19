package com.example.domain.usecase

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ManifestResource(
    val schemaVersion: String = "1.0",
    val provider: String = "Project Memory Provider",
    val capabilities: List<String> = listOf(
        "system_status",
        "static_resources",
        "project_records",
        "offline_local_storage"
    ),
    val mcpVersion: String = "2024-11-05",
    val license: String = "Apache-2.0"
)

class ReadStaticResourceUseCase {
    companion object {
        val MANIFEST_JSON: String = """
            {
              "schemaVersion": "1.0",
              "provider": "Project Memory Provider",
              "capabilities": [
                "system_status",
                "static_resources",
                "project_records",
                "offline_local_storage"
              ],
              "mcpVersion": "2024-11-05",
              "license": "Apache-2.0"
            }
        """.trimIndent()
    }

    operator fun invoke(uri: String): Result<String> {
        return when (uri) {
            "project://manifest" -> Result.success(MANIFEST_JSON)
            "project://status" -> Result.success("""{"status":"active","mcpReady":true}""")
            else -> Result.failure(IllegalArgumentException("Resource URI not recognized: $uri"))
        }
    }
}
