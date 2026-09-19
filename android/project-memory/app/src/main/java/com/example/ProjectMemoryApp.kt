package com.example

import android.app.Application
import com.example.data.database.AppDatabase
import com.example.data.repository.FileVersionRepositoryImpl
import com.example.data.repository.ProjectRepositoryImpl
import com.example.data.repository.RoomClientIdentityRepository
import com.example.data.repository.RoomEpisodeRepository
import com.example.data.repository.RoomIndexTargetValidator
import com.example.data.repository.RoomOwnerMemoryRepository
import com.example.data.repository.RoomProjectProvisioningRepository
import com.example.data.vault.DiskVaultStorage
import com.example.domain.repository.ClientIdentityRepository
import com.example.domain.repository.EpisodeRepository
import com.example.domain.repository.FileVersionRepository
import com.example.domain.repository.OwnerMemoryRepository
import com.example.domain.repository.ProjectProvisioningRepository
import com.example.domain.repository.ProjectRepository
import com.example.domain.repository.VaultStorage
import com.example.domain.security.Authorizer
import com.example.domain.security.SecurityPolicy
import com.example.domain.usecase.AppendEpisodeEventUseCase
import com.example.domain.usecase.CalculateSha256UseCase
import com.example.domain.usecase.ChunkTextUseCase
import com.example.domain.usecase.CreateEpisodeUseCase
import com.example.domain.usecase.CreateProjectUseCase
import com.example.domain.usecase.GetEpisodeTimelineUseCase
import com.example.domain.usecase.GetEpisodesUseCase
import com.example.domain.usecase.GetFileVersionsUseCase
import com.example.domain.usecase.GetGraphProjectionUseCase
import com.example.domain.usecase.GetProjectsUseCase
import com.example.domain.usecase.GetSystemStatusUseCase
import com.example.domain.usecase.IndexFileContentUseCase
import com.example.domain.usecase.ReadStaticResourceUseCase
import com.example.domain.usecase.ReadVaultBlobUseCase
import com.example.domain.usecase.SaveProjectUseCase
import com.example.mcp.McpServerManager
import com.example.startup.PlanIngestionCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class ProjectMemoryApp : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var database: AppDatabase
        private set
    lateinit var projectRepository: ProjectRepository
        private set
    lateinit var projectProvisioningRepository: ProjectProvisioningRepository
        private set
    lateinit var ownerMemoryRepository: OwnerMemoryRepository
        private set
    lateinit var vaultStorage: VaultStorage
        private set
    lateinit var fileVersionRepository: FileVersionRepository
        private set
    lateinit var episodeRepository: EpisodeRepository
        private set
    lateinit var clientIdentityRepository: ClientIdentityRepository
        private set
    lateinit var authorizer: Authorizer
        private set
    lateinit var calculateSha256UseCase: CalculateSha256UseCase
        private set
    lateinit var chunkTextUseCase: ChunkTextUseCase
        private set
    lateinit var indexFileContentUseCase: IndexFileContentUseCase
        private set
    lateinit var getFileVersionsUseCase: GetFileVersionsUseCase
        private set
    lateinit var readVaultBlobUseCase: ReadVaultBlobUseCase
        private set
    lateinit var createEpisodeUseCase: CreateEpisodeUseCase
        private set
    lateinit var appendEpisodeEventUseCase: AppendEpisodeEventUseCase
        private set
    lateinit var getEpisodesUseCase: GetEpisodesUseCase
        private set
    lateinit var getEpisodeTimelineUseCase: GetEpisodeTimelineUseCase
        private set
    lateinit var getProjectsUseCase: GetProjectsUseCase
        private set
    lateinit var saveProjectUseCase: SaveProjectUseCase
        private set
    lateinit var createProjectUseCase: CreateProjectUseCase
        private set
    lateinit var getGraphProjectionUseCase: GetGraphProjectionUseCase
        private set
    lateinit var getSystemStatusUseCase: GetSystemStatusUseCase
        private set
    lateinit var readStaticResourceUseCase: ReadStaticResourceUseCase
        private set
    lateinit var securityPolicy: SecurityPolicy
        private set
    lateinit var mcpServerManager: McpServerManager
        private set

    override fun onCreate() {
        super.onCreate()

        database = AppDatabase.getInstance(this)
        projectRepository = ProjectRepositoryImpl(database.projectDao())
        projectProvisioningRepository = RoomProjectProvisioningRepository(database)
        ownerMemoryRepository = RoomOwnerMemoryRepository(database)

        val vaultDir = File(filesDir, "vault")
        vaultStorage = DiskVaultStorage(vaultDir)
        fileVersionRepository = FileVersionRepositoryImpl(database.fileVersionDao())
        episodeRepository = RoomEpisodeRepository(database.episodeDao())
        clientIdentityRepository = RoomClientIdentityRepository(database)
        authorizer = Authorizer(clientIdentityRepository)

        calculateSha256UseCase = CalculateSha256UseCase()
        chunkTextUseCase = ChunkTextUseCase(calculateSha256UseCase)
        indexFileContentUseCase = IndexFileContentUseCase(
            vaultStorage = vaultStorage,
            fileVersionRepository = fileVersionRepository,
            indexTargetValidator = RoomIndexTargetValidator(database),
            calculateSha256UseCase = calculateSha256UseCase,
            chunkTextUseCase = chunkTextUseCase
        )
        getFileVersionsUseCase = GetFileVersionsUseCase(fileVersionRepository)
        readVaultBlobUseCase = ReadVaultBlobUseCase(vaultStorage)

        createEpisodeUseCase = CreateEpisodeUseCase(episodeRepository)
        appendEpisodeEventUseCase = AppendEpisodeEventUseCase(episodeRepository)
        getEpisodesUseCase = GetEpisodesUseCase(episodeRepository)
        getEpisodeTimelineUseCase = GetEpisodeTimelineUseCase(episodeRepository)

        getProjectsUseCase = GetProjectsUseCase(projectRepository)
        saveProjectUseCase = SaveProjectUseCase(projectRepository)
        createProjectUseCase = CreateProjectUseCase(projectProvisioningRepository)
        getGraphProjectionUseCase = GetGraphProjectionUseCase(projectRepository, ownerMemoryRepository)
        getSystemStatusUseCase = GetSystemStatusUseCase(projectRepository)
        readStaticResourceUseCase = ReadStaticResourceUseCase()
        securityPolicy = SecurityPolicy()

        mcpServerManager = McpServerManager(
            getSystemStatusUseCase = getSystemStatusUseCase,
            readStaticResourceUseCase = readStaticResourceUseCase,
            indexFileContentUseCase = indexFileContentUseCase,
            getFileVersionsUseCase = getFileVersionsUseCase,
            readVaultBlobUseCase = readVaultBlobUseCase,
            createEpisodeUseCase = createEpisodeUseCase,
            appendEpisodeEventUseCase = appendEpisodeEventUseCase,
            getEpisodesUseCase = getEpisodesUseCase,
            getEpisodeTimelineUseCase = getEpisodeTimelineUseCase,
            clientIdentityRepository = clientIdentityRepository,
            authorizer = authorizer,
            securityPolicy = securityPolicy
        )

        applicationScope.launch {
            PlanIngestionCoordinator(
                context = this@ProjectMemoryApp,
                identities = clientIdentityRepository,
                ownerMemory = ownerMemoryRepository
            ).run()
        }
    }
}
