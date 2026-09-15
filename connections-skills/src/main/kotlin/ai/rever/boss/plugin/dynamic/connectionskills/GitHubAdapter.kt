package ai.rever.boss.plugin.dynamic.connectionskills

import java.io.File
import java.util.concurrent.TimeUnit

class GitHubAdapter {

    data class Result(
        val exitCode: Int,
        val output: String,
        val timedOut: Boolean = false,
    ) {
        val success: Boolean
            get() = !timedOut && exitCode == 0
    }

    fun isInstalled(): Boolean =
        run("gh", "--version", timeoutSec = 10).success

    fun isAuthenticated(): Boolean =
        run("gh", "auth", "status", timeoutSec = 15).success

    fun listRepositories(limit: Int): Result {
        val safeLimit = limit.coerceIn(1, 100)
        return run(
            "gh",
            "repo",
            "list",
            "--limit",
            safeLimit.toString(),
            "--json",
            "nameWithOwner,url,isPrivate",
            timeoutSec = 30,
        )
    }

    fun createIssue(
        repository: String,
        title: String,
        body: String,
    ): Result {
        if (!isValidRepository(repository)) {
            return Result(2, "Invalid repository. Expected owner/name.")
        }

        if (title.isBlank()) {
            return Result(2, "Issue title must not be blank.")
        }

        return run(
            "gh",
            "issue",
            "create",
            "--repo",
            repository,
            "--title",
            title.take(200),
            "--body",
            body.take(20_000),
            timeoutSec = 60,
        )
    }

    private fun isValidRepository(value: String): Boolean =
        Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$").matches(value)

    private fun run(
        vararg cmd: String,
        timeoutSec: Long,
    ): Result = try {
        val process = ProcessBuilder(*cmd)
            .redirectErrorStream(true)
            .apply {
                environment()["GH_PAGER"] = "cat"
                environment()["PAGER"] = "cat"
            }
            .start()

        process.outputStream.close()

        val output = process.inputStream
            .bufferedReader()
            .use { it.readText() }
            .trim()

        if (!process.waitFor(timeoutSec, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            Result(-1, "GitHub CLI timed out after ${timeoutSec}s.", timedOut = true)
        } else {
            Result(process.exitValue(), output)
        }
    } catch (e: Exception) {
        Result(-1, e.message ?: e.javaClass.simpleName)
    }
}
