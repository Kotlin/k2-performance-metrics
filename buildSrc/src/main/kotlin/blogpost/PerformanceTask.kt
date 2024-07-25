package org.jetbrains.kotlin.k2.blogpost

import org.gradle.api.DefaultTask
import java.io.File
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.register
import javax.inject.Inject
import kotlin.concurrent.thread

abstract class PerformanceTask @Inject constructor(
    providerFactory: ProviderFactory,
    objectFactory: ObjectFactory,
    private val projectLayout: ProjectLayout,
) : DefaultTask() {

    @get:Input
    abstract val kotlinVersion: Property<String>

    private val gradleTask = providerFactory.gradleProperty("scenario.task").orElse("assemble")

    @Nested
    val scenarios: ListProperty<Scenario> = objectFactory.listProperty<Scenario>().value(
        gradleTask.zip(kotlinVersion) { gradleTask, kotlinVersion ->
            val runUsing = project.providers.gradleProperty("scenario.run.using").orNull ?: "cli"
            listOf(
                Scenario(
                    "clean_build",
                    cleanTasks = listOf("clean"),
                    tasks = listOf(gradleTask),
                    projectDir = project.projectDir,
                    kotlinVersion = kotlinVersion,
                    runUsing = runUsing,
                ),
                Scenario(
                    "incremental_non_abi_build",
                    tasks = listOf(gradleTask),
                    projectDir = project.projectDir,
                    nonAbiChanges = project.providers.gradleProperty("scenario.non.abi.changes").orNull?.split(",")
                        ?: throw IllegalArgumentException(
                            createErrorMessageForMissedProperty(
                                propertyName = "scenario.non.abi.changes",
                                scenarioName = "incremental_non_abi_build"
                            )
                        ),
                    kotlinVersion = kotlinVersion,
                    runUsing = runUsing,
                ),
                Scenario(
                    "incremental_abi_build",
                    tasks = listOf(gradleTask),
                    projectDir = project.projectDir,
                    abiChanges = project.providers.gradleProperty("scenario.abi.changes").orNull
                        ?: throw IllegalArgumentException(
                            createErrorMessageForMissedProperty(
                                propertyName = "scenario.abi.changes",
                                scenarioName = "incremental_abi_build"
                            )
                        ),
                    kotlinVersion = kotlinVersion,
                    runUsing = runUsing,
                ),
            )
        }
    )


    @get:Nested
    val testProject: Provider<TestProject> = objectFactory.property<TestProject>().value(
        providerFactory.provider { TestProject.createTestProject(project, logger) }
    )

    @get:Optional
    @get:InputFile
    abstract val scenarioFile: RegularFileProperty

    @get:InputDirectory
    abstract val gradleProfilerDir: DirectoryProperty

    private val gradleProfilerBin: Provider<RegularFile> = gradleProfilerDir.map {
        it.dir("bin")
            .run {
                if (System.getProperty("os.name").contains("windows", ignoreCase = true)) {
                    file("gradle-profiler.bat")
                } else {
                    file("gradle-profiler")
                }
            }
    }

    private val generatedScenarioFile: Provider<RegularFile> = projectLayout
        .buildDirectory
        .dir("generated-scenarios/$name")
        .zip(kotlinVersion) { dir, kotlinVersion ->
            dir.file("benchmark_${kotlinVersion.replace(".", "_")}.scenarios")
        }


    @TaskAction
    fun run() {
        val scenario = createScenariosIfNeed()
        testProject.get().checkoutProjectFromGit(logger)
        runBenchmark(scenario)
    }

    private fun createScenariosIfNeed(): File {
        return scenarioFile.orNull?.asFile ?: generatedScenarioFile.get().asFile.also { file ->
            file.parentFile.mkdirs()
            file.createNewFile()
            file.outputStream().bufferedWriter().use { writer ->
                scenarios.get().forEach {
                    it.printScenario(writer)
                }
                writer.flush()
            }
        }
    }

    companion object {
        @JvmStatic
        fun registerPerformanceTask(
            project: Project,
            benchmarkName: String,
            testedKotlinVersion: String,
            configure: PerformanceTask.() -> Unit = {}
        ): TaskProvider<PerformanceTask> {
            return project.tasks.register<PerformanceTask>(benchmarkName) {
                description = "Run $benchmarkName with Kotlin version $testedKotlinVersion"
                group = "benchmarks"
                kotlinVersion.value(testedKotlinVersion).finalizeValue()
                configure(this)
            }
        }

        private fun createErrorMessageForMissedProperty(propertyName: String, scenarioName: String) =
            "Unable to create \'$scenarioName\' scenario for performance test.\n" +
                    "Please provide path to a project file with \'$propertyName\' property."
    }

    private fun runBenchmark(scenarioFile: File) {
        logger.info("Staring benchmark")
        val workDirectory = projectLayout.buildDirectory.dir("benchmarkRuns").get().asFile.also {
            it.mkdirs()
        }
        //files in output directory will be overriden by gradle profile
        val gradleProfilerOutputDir =
            "${testProject.get().projectDir.name}-${kotlinVersion.get()}-profile-out".replace(".", "-")

        val profilerProcessBuilder = ProcessBuilder()
            .directory(workDirectory)
            .command(
                gradleProfilerBin.get().asFile.absolutePath,
                "--benchmark",
                "--project-dir",
                testProject.get().projectDir.absolutePath,
                "--output-dir",
                gradleProfilerOutputDir,
                "--scenario-file",
                scenarioFile.absolutePath,
            )
            .also {
                // Required, so 'gradle-profiler' will use toolchain JDK instead of current user one
                it.environment()["JAVA_HOME"] = System.getProperty("java.home")
                it.environment()["kotlin_version"] = kotlinVersion.get()
            }

        runBenchmarksForKotlinVersion(profilerProcessBuilder, workDirectory.resolve(gradleProfilerOutputDir))
    }

    private fun runBenchmarksForKotlinVersion(profilerProcessBuilder: ProcessBuilder, gradleProfilerOutputDir: File) {
        val logFile = gradleProfilerOutputDir.resolve("profile.log")
        logger.lifecycle("Benchmarks log will be written at file://${logFile.absolutePath}")

        // Gradle benchmark tool prints both to file and to stdout.
        // Discarding (or redirecting to any file) standart output also fixes "hanging" on Windows hosts
        profilerProcessBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD)

        val profilerProcess = profilerProcessBuilder.start()
        // Stop profiler on script stop
        Runtime.getRuntime().addShutdownHook(Thread {
            profilerProcess.destroy()
        })

        // stderr reader
        val stdErrPrinter = thread {
            val reader = profilerProcess.errorStream.bufferedReader()
            reader.lines().forEach { logger.error(it) }
        }

        profilerProcess.waitFor()
        stdErrPrinter.join() // wait for stderr printer to finish printing errors

        if (profilerProcess.exitValue() != 0) {
            throw IllegalStateException("Benchmarks finished with non-zero exit code: ${profilerProcess.exitValue()}")
        } else {
            logger.lifecycle("Benchmarks finished successfully.")
        }
    }
}