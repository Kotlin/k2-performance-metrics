package org.jetbrains.kotlin.k2.blogpost

import org.gradle.api.DefaultTask
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream
import okhttp3.OkHttpClient
import okhttp3.Request
import org.gradle.api.Project
import org.gradle.api.file.Directory
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
            listOf(
                Scenario(
                    "clean_build",
                    cleanTasks = listOf("clean"),
                    tasks = listOf(gradleTask),
                    projectDir = project.projectDir,
                    kotlinVersion = kotlinVersion
                ),
                Scenario(
                    "incremental_non_abi_build",
                    tasks = listOf(gradleTask),
                    projectDir = project.projectDir,
                    nonAbiChanges = project.providers.gradleProperty("scenario.non.abi.changes").orNull?.split(","),
                    kotlinVersion = kotlinVersion
                ),
                Scenario(
                    "incremental_abi_build",
                    tasks = listOf(gradleTask),
                    projectDir = project.projectDir,
                    abiChanges = project.providers.gradleProperty("scenario.abi.changes").orNull,
                    kotlinVersion = kotlinVersion
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

    @get:OutputDirectory
    val gradleProfilerDir: Provider<Directory> = projectLayout.buildDirectory.dir("gradle-profiler")

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
        downloadGradleProfilerIfNotAvailable(gradleProfilerDir.get().asFile)
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
        private const val GRADLE_PROFILER_VERSION = "0.19.0"
        private const val GRADLE_PROFILER_URL: String =
            "https://repo1.maven.org/maven2/org/gradle/profiler/gradle-profiler/$GRADLE_PROFILER_VERSION/gradle-profiler-$GRADLE_PROFILER_VERSION.zip"

        private val String.dropLeadingDir: String get() = substringAfter('/')

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
    }

    private fun downloadGradleProfilerIfNotAvailable(gradleProfilerDir: File) {
        if (gradleProfilerDir.listFiles().isNullOrEmpty()) {
            downloadAndExtractGradleProfiler(gradleProfilerDir)
        } else {
            logger.info("Gradle profiler has been already downloaded")
        }
    }

    private fun downloadAndExtractGradleProfiler(gradleProfilerDir: File) {
        logger.info("Downloading gradle-profiler into ${gradleProfilerDir.absolutePath}")

        gradleProfilerDir.mkdirs()

        val okHttpClient = OkHttpClient()
        val request = Request.Builder()
            .get()
            .url(GRADLE_PROFILER_URL)
            .build()
        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("Failed to download gradle-profiler, error code: ${response.code}")
        }

        val contentLength = response.body!!.contentLength()
        var downloadedLength = 0L
        logger.debug("Downloading: ")
        response.body!!.byteStream().buffered().use { responseContent ->
            ZipInputStream(responseContent).use { zip ->
                var zipEntry = zip.nextEntry
                while (zipEntry != null) {
                    if (zipEntry.isDirectory) {
                        gradleProfilerDir.resolve(zipEntry.name.dropLeadingDir).also { it.mkdirs() }
                    } else {
                        gradleProfilerDir.resolve(zipEntry.name.dropLeadingDir).outputStream().buffered().use {
                            zip.copyTo(it)
                        }
                    }
                    downloadedLength += zipEntry.compressedSize
                    logger.debug("..{}%", downloadedLength * 100 / contentLength)
                    zip.closeEntry()
                    zipEntry = zip.nextEntry
                }
            }
            logger.debug("\n")
        }
        gradleProfilerBin.get().asFile.setExecutable(true)
        logger.info("Finished downloading gradle-profiler")
    }

    private fun runBenchmark(scenarioFile: File) {
        logger.info("Staring benchmark")
        val workDirectory = projectLayout.buildDirectory.dir("benchmarkRuns").get().asFile.also {
            it.mkdirs()
        }
        val profilerProcessBuilder = ProcessBuilder()
            .directory(workDirectory)
            .inheritIO()
            .command(
                gradleProfilerBin.get().asFile.absolutePath,
                "--benchmark",
                "--project-dir",
                testProject.get().projectDir.absolutePath,
                "--scenario-file",
                scenarioFile.absolutePath,
            ).also {
                // Required, so 'gradle-profiler' will use toolchain JDK instead of current user one
                it.environment()["JAVA_HOME"] = System.getProperty("java.home")
                it.environment()["kotlin_version"] = kotlinVersion.get()
            }

        runBenchmarksForKotlinVersion(profilerProcessBuilder)
        logger.info("Benchmarks successful finished")
    }

    private fun runBenchmarksForKotlinVersion(profilerProcessBuilder: ProcessBuilder) {
        val profilerProcess = profilerProcessBuilder.start()
        // Stop profiler on script stop
        Runtime.getRuntime().addShutdownHook(Thread {
            profilerProcess.destroy()
        })
        profilerProcess.waitFor()

        if (profilerProcess.exitValue() != 0) {
            throw IllegalStateException("Benchmarks end with non zero code: ${profilerProcess.exitValue()}. Please look into benchmark log for more details")
        }
    }
}