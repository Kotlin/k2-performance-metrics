package org.jetbrains.kotlin.k2.blogpost

import java.io.File
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.lib.TextProgressMonitor
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional

class TestProject(
    @get:Internal
    val projectDir: File,

    @get:Optional
    @get:Input
    val projectGitUrl: String? = null,

    @get:Optional
    @get:Input
    val gitCommitSha: String? =  null,
) {

    @get:Input
    val projectDirAsString = projectDir.absolutePath

    fun checkoutProjectFromGit(logger: Logger) {
        if (projectGitUrl == null) return

        require(gitCommitSha != null) { "Git commit SHA is required" }

        logger.info("Git project will be checked out into \'${projectDir.path}\'")
        val git = if (projectDir.exists()) {
            logger.info("Repository is available, resetting it state")
            Git.open(projectDir).also {
                it.reset()
                    .setMode(ResetCommand.ResetType.HARD)
                    .setProgressMonitor(gitOperationsPrinter)
                    .call()
            }
        } else {
            logger.info("Running git checkout for $projectGitUrl")
            projectDir.mkdirs()
            Git.cloneRepository()
                .setDirectory(projectDir)
                .setCloneSubmodules(true)
                .setProgressMonitor(gitOperationsPrinter)
                .setURI(projectGitUrl)
                .call()
        }

        git.checkout()
            .setName(gitCommitSha)
            .setProgressMonitor(gitOperationsPrinter)
            .call()
    }

    companion object {
        private val gitOperationsPrinter = TextProgressMonitor()
        private const val PROJECT_PATH_PROPERTY = "project.path"
        private const val PROJECT_GIT_URL_PROPERTY = "project.git.url"
        private const val PROJECT_GIT_COMMIT_SHA_PROPERTY = "project.git.commit.sha"

        private const val errorMessage = "Unable to initialize project for performance test.\n" +
                "Please either provide path to existed project with \'$PROJECT_PATH_PROPERTY\' property \n" +
                "or git information with \'$PROJECT_GIT_URL_PROPERTY\' and \'$PROJECT_GIT_COMMIT_SHA_PROPERTY\' properties \n" +
                "to check out it."

        internal fun createTestProject(project: Project, logger: Logger): TestProject {
            val providers = project.providers
            val projectPath = providers.gradleProperty(PROJECT_PATH_PROPERTY).orNull
            if (projectPath != null) {
                logger.info("Use existing project located at \'$projectPath\' for performance test")
                return TestProject(File(projectPath))
            }

            val projectGitUrl = providers.gradleProperty(PROJECT_GIT_URL_PROPERTY).orNull
                ?: throw IllegalArgumentException(errorMessage)

            val gitCommitSha = providers.gradleProperty(PROJECT_GIT_COMMIT_SHA_PROPERTY).orNull ?: throw IllegalArgumentException(errorMessage)
            val projectDir = providers.gradleProperty("project.git.directory").orNull?.let { File(it) }
                ?: project.layout.buildDirectory
                    .dir(projectGitUrl.gitName())
                    .get().asFile
            return TestProject(projectDir, projectGitUrl, gitCommitSha)
        }

        fun String.gitName(): String {
            return substringBefore(".git").substringAfterLast("/")
        }
    }
}