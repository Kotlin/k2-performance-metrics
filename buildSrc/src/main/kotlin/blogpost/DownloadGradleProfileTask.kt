package org.jetbrains.kotlin.k2.blogpost

import okhttp3.OkHttpClient
import okhttp3.Request
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.OutputDirectory
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream
import javax.inject.Inject
import org.gradle.api.file.RegularFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider

abstract class DownloadGradleProfileTask @Inject constructor(
    projectLayout: ProjectLayout,
) : DefaultTask() {

    @get:OutputDirectory
    val gradleProfilerDir: Provider<Directory> = projectLayout.buildDirectory.dir("gradle-profiler-$GRADLE_PROFILER_VERSION")

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

    companion object {
        private const val GRADLE_PROFILER_VERSION = "0.21.0-alpha-4"
        private const val GRADLE_PROFILER_URL: String =
            "https://repo1.maven.org/maven2/org/gradle/profiler/gradle-profiler/$GRADLE_PROFILER_VERSION/gradle-profiler-$GRADLE_PROFILER_VERSION.zip"

        private val String.dropLeadingDir: String get() = substringAfter('/')
        private const val DOWNLOAD_GRADLE_TASK_NAME = "downloadGradleProfile"

        fun registerTask(project: Project) =
            project.tasks.register(
                DOWNLOAD_GRADLE_TASK_NAME,
                DownloadGradleProfileTask::class.java
            ) {
                description = "Download gradle profiler"
                group = "benchmarks"
            }
    }


    @TaskAction
    fun run() {
        downloadGradleProfilerIfNotAvailable(gradleProfilerDir.get().asFile)
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
}