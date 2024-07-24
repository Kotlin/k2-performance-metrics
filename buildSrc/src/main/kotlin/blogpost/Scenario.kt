package org.jetbrains.kotlin.k2.blogpost

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import java.io.BufferedWriter
import java.io.File

data class Scenario(
    @get:Input
    val name: String,
    @get:Input
    val kotlinVersion: String,
    @get:Input
    @get:Optional
    val cleanTasks: List<String>? = null,
    @get:Input
    val tasks: List<String> = listOf("assemble"),
    @get:InputDirectory
    val projectDir: File,
    @get:Input
    val warmUpRounds: Int = 10,
    @get:Input
    val executionRounds: Int = 10,
    @get:Input
    val additionGradleArguments: List<String> = emptyList(),
    @get:Input
    val runUsing: String,
    @get:Input
    @get:Optional
    val abiChanges: String? = null,
    @get:Nested
    @get:Optional
    val gitCheckout: GitCheckout? = null,
    @get:Input
    @get:Optional
    val nonAbiChanges: List<String>? = null,
) {
    fun printScenario(writer: BufferedWriter) {
        writer.append("$name {\n")
        cleanTasks?.let { tasks ->
            writer.append(tasks.joinToString(
                separator = ", ",
                prefix = "cleanup-tasks = [",
                postfix = "]\n",
                transform = { "\"$it\"" }))
        }
        writer.append(
                tasks.joinToString(
                    separator = ", ",
                    prefix = "tasks = [",
                    postfix = "]\n",
                    transform = { "\"$it\"" })
            )
        writer.append("warm-ups = $warmUpRounds\n")
        writer.append("iterations = $executionRounds\n")
        writer.append("clear-build-cache-before = SCENARIO\n")
        writer.append("clear-transform-cache-before = SCENARIO\n")
        abiChanges?.let {
            writer.append("apply-abi-change-to = \"$it\"\n")
        }
        nonAbiChanges?.let { changes ->
            writer.append(changes.joinToString(
                separator = ", ",
                prefix = "apply-non-abi-change-to = [",
                postfix = "]\n",
                transform = { "\"$it\"" }))
        }
        gitCheckout?.let { (cleanup, build) ->
            writer.append("git-checkout = {\ncleanup = \"$cleanup\"\nbuild = \"$build\"\n}\n")
        }

        writer.append("run-using = $runUsing\n")

        writer.append("gradle-args = [\"--no-build-cache\", \"-Pkotlin.build.report.output=JSON,FILE\", \"-Pkotlin.daemon.jvmargs=-Xmx=4G\", " +
                "\"-Pkotlin.build.report.json.directory=${escapeSymbolsForWindows(projectDir.resolve("reports/$kotlinVersion/$name").path)}\", " +
                "\"-Pkotlin.build.report.file.output_dir=${escapeSymbolsForWindows(projectDir.resolve("reports/$kotlinVersion/$name").path)}\", " +
                "\"-Pkotlin_version=$kotlinVersion\"")
        writer.append(additionGradleArguments.joinToString(separator = ", ", prefix = ", ", transform = { "\"$it\"" }))
        writer.append("]\n")

        writer.append("}\n")
    }

    private fun escapeSymbolsForWindows(path: String) = path.replace("\\", "\\\\")
}

data class GitCheckout(
    @get:Input
    val cleanup: String,
    @get:Input
    val build: String,
)
