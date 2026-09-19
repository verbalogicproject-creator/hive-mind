package com.example.domain

import com.example.domain.model.ServerConfig
import com.example.domain.security.SecurityPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityPolicyTest {

    private val policy = SecurityPolicy()

    @Test
    fun defaultLoopbackConfig_isValid() {
        val config = ServerConfig(host = "127.0.0.1", port = 8080, isReadOnly = true)
        val result = policy.validateConfig(config)
        assertTrue(result.isSuccess)
    }

    @Test
    fun nonLoopbackWithoutLan_isRejected() {
        val config = ServerConfig(host = "192.168.1.100", port = 8080, isLanEnabled = false)
        val result = policy.validateConfig(config)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }

    @Test
    fun lanExplicitlyEnabled_isAccepted() {
        val config = ServerConfig(
            host = "192.168.1.100",
            port = 8080,
            isLanEnabled = true
        )
        val result = policy.validateConfig(config)
        assertTrue(result.isSuccess)
    }

    @Test
    fun privilegedPort_isRejected() {
        val config = ServerConfig(host = "127.0.0.1", port = 80)
        val result = policy.validateConfig(config)
        assertTrue(result.isFailure)
    }

    @Test
    fun readOnlyMutationGate_enforced() {
        assertTrue(policy.canExecuteMutation(ServerConfig(isReadOnly = false)))
        assertFalse(policy.canExecuteMutation(ServerConfig(isReadOnly = true)))
    }

    @Test
    fun forbiddenToolExecution_isBlocked() {
        assertFalse(policy.isToolPermitted("shell_exec"))
        assertFalse(policy.isToolPermitted("exec_command"))
        assertFalse(policy.isToolPermitted("raw_sql_query"))
        assertFalse(policy.isToolPermitted("url_fetch_arbitrary"))
        assertTrue(policy.isToolPermitted("system_status"))
        assertTrue(policy.isToolPermitted("get_project_memory"))
    }
}
