package com.example.startup

import android.content.Context
import com.example.domain.repository.ClientIdentityRepository
import com.example.domain.repository.OwnerMemoryRepository
import com.example.domain.security.CallerContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.security.MessageDigest

/**
 * Completes the user-approved alpha plan lock on the first app start, then verifies
 * the same immutable version on the following process start. Idempotency prevents
 * duplicate versions if startup is interrupted.
 */
class PlanIngestionCoordinator(
    private val context: Context,
    private val identities: ClientIdentityRepository,
    private val ownerMemory: OwnerMemoryRepository
) {
    suspend fun run(): Result<Unit> = runCatching {
        val source = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        val sourceHash = sha256(source.toByteArray())
        require(sourceHash == SOURCE_SHA256) { "Bundled plan hash does not match the approved source" }
        val receiptFile = File(context.filesDir, RECEIPT_PATH)

        val priorVersionId = readVersionId(receiptFile)
        if (priorVersionId != null) {
            val detail = ownerMemory.getMemoryDetail(PROJECT_ID, priorVersionId)
            if (detail != null && detail.itemId == PLAN_ID) {
                writeReceipt(receiptFile, priorVersionId, detail.contentHash, detail.createdAtEpochMs, "persisted", "passed")
                return@runCatching
            }
        }

        val payload = buildJsonObject {
            put("planId", PLAN_ID)
            put("planVersion", PLAN_VERSION)
            put("approvalStatus", "approved")
            put("sourcePath", SOURCE_PATH)
            put("sourceSha256", sourceHash)
            put("markdown", source)
        }.toString()
        val item = identities.appendInboxItem(
            caller = CallerContext.LOCAL_OWNER,
            projectId = PROJECT_ID,
            sessionId = SESSION_ID,
            itemId = PLAN_ID,
            itemType = "plan",
            title = PLAN_TITLE,
            payloadJson = payload,
            idempotencyKey = IDEMPOTENCY_KEY
        ).getOrThrow()
        writeReceipt(
            receiptFile,
            item.versionId,
            item.contentHash,
            item.createdAtEpochMs,
            "persisted",
            "pending_restart"
        )
    }

    private fun readVersionId(file: File): String? = runCatching {
        if (!file.exists()) return null
        Json.parseToJsonElement(file.readText()).jsonObject["versionId"]?.jsonPrimitive?.content
    }.getOrNull()

    private fun writeReceipt(
        file: File,
        versionId: String,
        contentHash: String,
        createdAt: Long,
        ingestionStatus: String,
        restartVerification: String
    ) {
        file.parentFile?.mkdirs()
        val receipt = buildJsonObject {
            put("schemaVersion", "1.0")
            put("planId", PLAN_ID)
            put("planVersion", PLAN_VERSION)
            put("sourcePath", SOURCE_PATH)
            put("sourceSha256", SOURCE_SHA256)
            put("projectId", PROJECT_ID)
            put("laneId", LANE_ID)
            put("sessionId", SESSION_ID)
            put("itemId", PLAN_ID)
            put("versionId", versionId)
            put("storedItemContentHash", contentHash)
            put("createdAtEpochMs", createdAt)
            put("ingestionStatus", ingestionStatus)
            put("restartVerification", restartVerification)
            if (restartVerification == "passed") put("restartVerifiedAtEpochMs", System.currentTimeMillis())
        }
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(receipt.toString())
        check(temporary.renameTo(file)) { "Could not finalize the plan receipt" }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        private const val ASSET_PATH = "plans/V0_2_STABILITY_UX_GRAPH.md"
        private const val RECEIPT_PATH = "receipts/V0_2_STABILITY_UX_GRAPH.receipt.json"
        private const val SOURCE_PATH = "plans/V0_2_STABILITY_UX_GRAPH.md"
        private const val SOURCE_SHA256 = "e0370951d76f1b487818dd08e7a0417be4191bdcff36fee9c106de9cca07d73e"
        private const val PLAN_ID = "project-memory-v0.2-alpha1-stability-agents-graph"
        private const val PLAN_VERSION = "1.0.0"
        private const val PLAN_TITLE = "v0.2.0 Alpha 1 — Stability, Agents, and Graph"
        private const val PROJECT_ID = "proj-76346aa4"
        private const val LANE_ID = "8c139cfc-a643-4ddd-a77a-66f9fca17e0e"
        private const val SESSION_ID = "4603e1b9-e2c4-46fe-bef4-df3b07c55fba"
        private const val IDEMPOTENCY_KEY = "plan-lock-v0.2-alpha1-stability-agents-graph"
    }
}
