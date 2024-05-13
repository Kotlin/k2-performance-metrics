import org.jetbrains.kotlin.k2.blogpost.DownloadGradleProfileTask
import org.jetbrains.kotlin.k2.blogpost.PerformanceTask

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.9.23"
    `kotlin-dsl`
}

val downloadGradleProfileTask = DownloadGradleProfileTask.registerTask(project)

val cleanGeneratedReports_2_0 = tasks.register<Delete>("cleanGeneratedReports2_0") {
    delete(layout.projectDirectory.dir("reports/2.0.0-RC3"))
}

val benchmark_2_0 = PerformanceTask.registerPerformanceTask(project, "benchmark_2_0", "2.0.0-RC3") {
    gradleProfilerDir.set(downloadGradleProfileTask.flatMap { it.gradleProfilerDir })
    dependsOn(cleanGeneratedReports_2_0)
    dependsOn(downloadGradleProfileTask)
}

val cleanGeneratedReports_1_9 = tasks.register<Delete>("cleanGeneratedReports1_9") {
    delete(layout.projectDirectory.dir("reports/1.9.23"))
}
val benchmark_1_9 = PerformanceTask.registerPerformanceTask(project, "benchmark_1_9", "1.9.23") {
    gradleProfilerDir.set(downloadGradleProfileTask.flatMap { it.gradleProfilerDir })
    dependsOn(cleanGeneratedReports_1_9)
    dependsOn(downloadGradleProfileTask)
}

tasks.register("runBenchmarks") {
    group = "benchmarks"
    description = "Run all benchmarks"

    dependsOn(benchmark_2_0, benchmark_1_9)
}
