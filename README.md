[![JetBrains incubator project](https://jb.gg/badges/incubator-flat-square.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)

## Introduction

Use this project to run performance tests on your Gradle project and compare the differences in
performance between [Kotlin K1 and K2 compilers](https://blog.jetbrains.com/kotlin/2023/02/k2-kotlin-2-0/). 

Three test scenarios are covered to benchmark the performance:

* clean build
* incremental build with non-ABI changes
* incremental build with ABI changes

After the build finishes, open the [benchmarkResult.ipynb](benchmarkResult.ipynb) Kotlin Notebook to compare the results.

> [!IMPORTANT]
> You must have the [Kotlin Notebook](https://blog.jetbrains.com/kotlin/2023/07/introducing-kotlin-notebook/) plugin 
> installed in IntelliJ IDEA Ultimate to view the results.

## Prerequisites

Ensure beforehand that the project that you want to analyze can be successfully compiled with both Kotlin versions that you want to compare.
By default, this project uses Kotlin 1.9.24 and Kotlin 2.0.0.

So that the Kotlin version can be automatically configured for your project, in your `build.gradle(.kts)` file, add the `$kotlin_version` parameter:

```kotlin
plugins {
    id("org.jetbrains.kotlin.jvm") version "$kotlin_version"
}
```

If your project uses a version catalog, update the `settings.gradle.kts` file to override the Kotlin version using the following code:

```kotlin
versionCatalogs {
    create("libs") {
        val kotlinVersion = System.getenv("kotlin_version")
        if (kotlinVersion != null) {
            version("kotlin", kotlinVersion)
        }
    }
}
```

> [!IMPORTANT]
> You might encounter new warnings in your builds using Kotlin `2.0.0`.
> If you use the `allWarningsAsErrors` [compiler option](gradle-compiler-options.md#attributes-common-to-jvm-js-and-js-dce), you can remove it to continue without fixing these warnings.
> We recommend that you fix all warnings before proceeding.

Ensure that the [`JAVA_HOME`](https://docs.oracle.com/cd/E19182-01/821-0917/inst_jdk_javahome_t/index.html) environment variable is set.  
If your project involves Android development, make sure that the [`ANDROID_HOME`](https://developer.android.com/tools/variables) 
environmental variable is set as well.

If [dependencies verification](https://docs.gradle.org/8.2.1/userguide/dependency_verification.html#sub:enabling-verification) is enabled,
ensure that the dependencies for both Kotlin `1.9` and Kotlin `2.0` are correctly included in your project setup.
Or [disable/lenient](https://docs.gradle.org/current/userguide/dependency_verification.html#sec:disabling-verification) dependency verification.

If you are using the `kotlinDsl` plugin in the `buildSrc` subproject, we recommend applying the `kotlin("jvm")` plugin as well.
This prevents issues related to unrecognized output types, such as the new JSON format for build reports available from Kotlin `1.9.23` and Kotlin `2.0.0-RC1`, and ensures compatibility across your project's configuration.

## Step 1: Configure project settings

To configure your project for performance tests, modify your [gradle.properties](gradle.properties) file:

* Set `project.path` to configure the path to your project.
* Set `project.git.url` and `project.git.commit.sha` to specify the URL and commit of a Git repository from which to clone your project.
* Set `project.git.directory` to specify the directory where the project is stored.

## Step 2: Configure build scenarios 

To configure default scenarios for your project, modify your [gradle.properties](gradle.properties) file:

* Set `scenario.non.abi.changes` to specify files for incremental builds with non-ABI changes. For multiple files, separate them with commas.
* Set `scenario.abi.changes` to specify a single file path for incremental builds with ABI changes.
* Set `scenario.task` to define the default build task; if not set, the `assemble` task will be used by default.

### Create custom build scenarios

You can define custom build scenarios for your project by specifying them in your [`build.gradle.kts`](build.gradle.kts) file.
Set up your scenarios using the `PerformanceTask.scenarios` task input or configure a
[Gradle profiler](https://github.com/gradle/gradle-profiler) scenario file using the `PerformanceTask.scenarioFile` task input.

For example, you can add the following configuration to your [build.gradle.kts](build.gradle.kts) file to use custom scenarios:

```kotlin
import org.jetbrains.kotlin.k2.blogpost.PerformanceTask

// Defines custom scenarios
val customScenarios = listOf(Scenario(
    name = "new scenario",
    cleanTasks = listOf("clean_task_if_needed"),
    kotlinVersion = kotlinVersion,
    tasks = listOf("task_to_execute"),
    projectDir = project.projectDir,
    nonAbiChanges = listOf("path_to_file"),
    warmUpRounds = 5,
    executionRounds = 5,
))

// Registers performance tasks for Kotlin versions 2.0.0 and 1.9.24
val benchmark_2_0 = PerformanceTask.registerPerformanceTask(project, "benchmark_2_0", "2.0.0") {
    scenarios.set(customScenarios)
}

val benchmark_1_9 = PerformanceTask.registerPerformanceTask(project, "benchmark_1_9", "1.9.24") {
    scenarios.set(customScenarios)
}

// Registers a task to run all benchmarks
tasks.register("runBenchmarks") {
    dependsOn(benchmark_2_0, benchmark_1_9)
}
```

## Step 3: Run performance test

To run performance tests, run the following command in your terminal:

```bash
./gradlew runBenchmarks
```

## Step 4: (Optional) Visualize results

> [!IMPORTANT]
> You must have the [Kotlin Notebook](https://blog.jetbrains.com/kotlin/2023/07/introducing-kotlin-notebook/) plugin 
> installed in IntelliJ IDEA Ultimate to view the results.

To analyze the results:

1. Open the [benchmarkResult.ipynb](benchmarkResult.ipynb) Kotlin Notebook file.
2. Run all code cells in the Kotlin Notebook using the `Run All` button to display and compare the produced results. 

## Troubleshooting

If you encounter any issues using this project, let us know:
* Join our Kotlin Slack workspace - [get an invite](https://surveys.jetbrains.com/s3/kotlin-slack-sign-up?_gl=1*ju6cbn*_ga*MTA3MTk5NDkzMC4xNjQ2MDY3MDU4*_ga_9J976DJZ68*MTY1ODMzNzA3OS4xMDAuMS4xNjU4MzQwODEwLjYw) and share your experience in the [#k2-early-adopters](https://kotlinlang.slack.com/archives/C03PK0PE257) channel.
* Create an issue in [our issue tracker](https://youtrack.jetbrains.com/newIssue?project=KT&summary=K2+release+migration+issue&description=Describe+the+problem+you+encountered+here.&c=tag+k2-release-migration).