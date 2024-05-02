# Description

This project enables you to run performance tests on your Gradle project to compare the differences in
performance between [Kotlin K1 and K2 compilers](https://blog.jetbrains.com/kotlin/2023/02/k2-kotlin-2-0/). 

The `assemble` task, used by default, builds your project using Kotlin versions `2.0.0-RC1` and `1.9.23` for performance tests.

Three test scenarios are covered to benchmark the performance:

* clean build
* incremental build with non-ABI changes
* incremental build with ABI changes

After the build finishes, open the [benchmarkResult.ipynb](benchmarkResult.ipynb) Kotlin Notebook to compare the results.
> You must have the [Kotlin Notebook](https://blog.jetbrains.com/kotlin/2023/07/introducing-kotlin-notebook/) plugin 
> installed in IntelliJ IDEA Ultimate to view the results.

## Project requirements

Ensure beforehand that the used project is compiled with both kotlin 1.9.23 and kotlin 2.0.0. 
You might also encounter new warnings in your builds using Kotlin `2.0.0`.
You can either resolve these warnings or disable the `allWarningsAsErrors` compiler option to continue without fixing them.

The JSON build report output type is available since Kotlin `2.0.0-RC1` and Kotlin `1.9.23`.
If you are using the `kotlinDsl` plugin in the `buildSrc` subproject, we recommend applying the `kotlin("jvm")` plugin as well.
This prevents issues related to unrecognized output types and ensures compatibility across your project's configuration.

Ensure that the [`JAVA_HOME`](https://docs.oracle.com/cd/E19182-01/821-0917/inst_jdk_javahome_t/index.html) environment variable is set.  
If your project involves Android development, make sure to set the [`ANDROID_HOME`](https://developer.android.com/tools/variables) 
environmental variable as well.

You can change the Kotlin version of your project, by setting the `kotlin_version` Gradle property.

If your project uses version catalog,
you can update the `settings.gradle.kts` file to override the Kotlin version using the following code:

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

If  [dependencies verification](https://docs.gradle.org/8.2.1/userguide/dependency_verification.html#sub:enabling-verification) is enabled,
ensure that the dependencies for both Kotlin `1.9` and Kotlin `2.0` are correctly included in your project setup.

## Configure project settings

You should modify your [gradle.properties](gradle.properties) file to configure your project for performance tests:

* Set `project.path` to configure the path to your project.
* Set `project.git.url` and `project.git.commit.sha` to specify the URL and commit of a Git repository from which to clone your project.
* Set `project.git.directory` to specify the directory where the project is stored.

## Configure build scenarios 

You should modify your [gradle.properties](gradle.properties) file to configure default scenarios for your project:

* Set `scenario.non.abi.changes` to specify files for incremental builds with non-ABI changes. For multiple files, separate them with commas.
* Set `scenario.abi.changes` to specify a single file path for incremental builds with ABI changes.
* Set `scenario.task` to define the default build task; if not set, the `assemble` task will be used by default.

## Create custom build scenarios

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

// Registers performance tasks for Kotlin versions 2.0.0-RC1 and 1.9.23
val benchmark_2_0 = PerformanceTask.registerPerformanceTask(project, "benchmark_2_0", "2.0.0-RC1") {
    scenarios.set(customScenarios)
}

val benchmark_1_9 = PerformanceTask.registerPerformanceTask(project, "benchmark_1_9", "1.9.23") {
    scenarios.set(customScenarios)
}

// Registers a task to run all benchmarks
tasks.register("runBenchmarks") {
    dependsOn(benchmark_2_0, benchmark_1_9)
}
```

## Run performance test

To run performance tests, run the following command in your terminal:

```bash
./gradlew runBenchmarks
```

## Visualize results

> You must have the [Kotlin Notebook](https://blog.jetbrains.com/kotlin/2023/07/introducing-kotlin-notebook/) plugin 
> installed in IntelliJ IDEA Ultimate to view the results.

To analyze the results:

1. Open the [benchmarkResult.ipynb](benchmarkResult.ipynb) Kotlin Notebook file.
2. Run all code cells in the Kotlin Notebook using the `Run All` button to display and compare the produced results. 

