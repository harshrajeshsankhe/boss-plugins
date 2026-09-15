package ai.rever.boss.plugin.dynamic.connectionskills

import ai.rever.boss.plugin.api.PluginContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class ConnectionRegistry(
    private val context: PluginContext,
) {
    private val storage = context.pluginStorageFactory?.createStorage("connections")
    private val states = ConcurrentHashMap<ConnectionProvider, ConnectionState>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val github = GitHubAdapter()

    private val _statuses =
        MutableStateFlow(ConnectionProvider.entries.map { status(it) })

    val statuses: StateFlow<List<ConnectionStatus>> =
        _statuses.asStateFlow()

    init {
        scope.launch {
            for (provider in ConnectionProvider.entries) {
                val saved = storage?.getString(key(provider), null)

                states[provider] = saved?.let {
                    runCatching {
                        ConnectionState.valueOf(it)
                    }.getOrNull()
                } ?: ConnectionState.NOT_AUTHENTICATED
            }

            refresh()
        }
    }

    fun status(provider: ConnectionProvider): ConnectionStatus {
        if (!available(provider.executable)) {
            return ConnectionStatus(
                provider,
                ConnectionState.MISSING_DEPENDENCY,
                "Required executable ${provider.executable} was not found on PATH.",
            )
        }

        return when (provider) {
            ConnectionProvider.GITHUB -> {
                when {
                    states[provider] == ConnectionState.DISCONNECTED ->
                        ConnectionStatus(
                            provider,
                            ConnectionState.DISCONNECTED,
                            "GitHub connection is disabled. Reconnect from Connections & Skills.",
                        )

                    !github.isAuthenticated() ->
                        ConnectionStatus(
                            provider,
                            ConnectionState.NOT_AUTHENTICATED,
                            "GitHub CLI is installed but not authenticated. Run `gh auth login`.",
                        )

                    else ->
                        ConnectionStatus(
                            provider,
                            ConnectionState.CONNECTED,
                            "GitHub CLI authentication is active.",
                        )
                }
            }

            ConnectionProvider.GOOGLE_SHEETS ->
                ConnectionStatus(
                    provider,
                    states[provider] ?: ConnectionState.NOT_AUTHENTICATED,
                    message(states[provider] ?: ConnectionState.NOT_AUTHENTICATED),
                )
        }
    }

    fun connect(provider: ConnectionProvider): ConnectionStatus {
        if (provider == ConnectionProvider.GITHUB) {
            if (!github.isInstalled()) {
                return status(provider)
            }

            if (!github.isAuthenticated()) {
                return status(provider)
            }
        }

        states[provider] = ConnectionState.CONNECTED
        persist(provider, ConnectionState.CONNECTED)
        refresh()

        return status(provider)
    }

    fun disconnect(provider: ConnectionProvider): ConnectionStatus {
        states[provider] = ConnectionState.DISCONNECTED
        persist(provider, ConnectionState.DISCONNECTED)
        refresh()

        return status(provider)
    }

    fun isConnected(provider: ConnectionProvider): Boolean =
        status(provider).state == ConnectionState.CONNECTED

    fun github(): GitHubAdapter = github

    fun dispose() {
        scope.cancel()
        states.clear()
    }

    private fun persist(
        provider: ConnectionProvider,
        state: ConnectionState,
    ) {
        scope.launch {
            storage?.putString(key(provider), state.name)
        }
    }

    private fun refresh() {
        _statuses.value =
            ConnectionProvider.entries.map { status(it) }
    }

    private fun key(provider: ConnectionProvider): String =
        "connection.${provider.id}.state"

    private fun available(name: String): Boolean {
        val dirs =
            (
                (System.getenv("PATH") ?: "").split(File.pathSeparator) +
                    listOf(
                        System.getProperty("user.home") + "/.local/bin",
                        "/opt/homebrew/bin",
                        "/usr/local/bin",
                        "/usr/bin",
                    )
                )

        return dirs.any { dir ->
            if (dir.isBlank()) {
                false
            } else {
                File(dir, name).let {
                    it.isFile && it.canExecute()
                }
            }
        }
    }

    private fun message(state: ConnectionState): String =
        when (state) {
            ConnectionState.AVAILABLE ->
                "Provider is available."

            ConnectionState.MISSING_DEPENDENCY ->
                "Required dependency is missing."

            ConnectionState.NOT_AUTHENTICATED ->
                "Authentication has not been established."

            ConnectionState.CONNECTED ->
                "Connection is active."

            ConnectionState.DISCONNECTED ->
                "Connection is explicitly disconnected."

            ConnectionState.ERROR ->
                "Connection reported an error."
        }
}
