# Description

This projects allows you to run performance tests on your Gradle project to compare 
performance difference between [Kotlin K1 and K2 compilers](https://blog.jetbrains.com/kotlin/2023/02/k2-kotlin-2-0/). 

Performance tests will build your project with kotlin `2.0.0-RC1` and kotlin `1.9.23` releases by running `assmeble` task.
The `assemble` task is used by default to build your project.
Benchmarks will be run three scenarios: "clean build",
"incremental build with non-ABI changes" and "incremental build with ABI changes".
After benchmark will finish open [benchmarkResult.ipynb](benchmarkResult.ipynb) Kotlin notebook to compare Gradle
and Kotlin time in the produced result.

## Getting Started

## Project requirements

Project should allow changing the Kotlin version via `kotlin_version` Gradle property.

If a project is using version catalog,
you can update `settings.gradle.kts` files in it to override the kotlin version:
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

## Tested project

There are two options to configure your project for performance tests:
- set the path to your project via `project.path` in [gradle.properties](gradle.properties).
- set `project.git.url` and `project.git.commit.sha` in [gradle.properties](gradle.properties) to clone a project from git repository. 

In addition, you can specify directory where the project will be stored with `project.git.directory` property, 
otherwise Git project name will be used.

## Update build scenarios for your project  

Additional scenarios configuration is required to run performance tests on your project. 
Please set in [gradle.properties](gradle.properties) the following properties:
- set list of files split by comma for incremental build with non-ABI changes in `scenario.non.abi.changes` property
- set a single file path for incremental build with ABI change in `scenario.abi.changes` property
- set the default task to build your project in `scenario.task` property, otherwise `assemble` task will be used.

## Custom build scenarios

If you are interested in running performance build on custom scenarios, you can configure them via 
`PerformanceTask.scenarios` task input,
or set [Gradle profiler](https://github.com/gradle/gradle-profiler) scenario file via `PerformanceTask.scenarioFile` task input.

For example, you can do follow update in [build.gradle.kts](build.gradle.kts) file to use custom scenarios:

```kotlin

import org.jetbrains.kotlin.k2.blogpost.PerformanceTask

val customScenarios = listOf(Scenario(
    name = "new scenario",
    cleanTasks = listOf("clean_task_if_need"),
    kotlinVersion = kotlinVersion,
    tasks = listOf("task_to_execute"),
    projectDir = project.projectDir,
    nonAbiChanges = listOf("path_to_file"),
    warmUpRounds = 5,
    executionRounds = 5,
))

val benchmark_2_0 = PerformanceTask.registerPerformanceTask(project, "benchmark_2_0", "2.0.0-RC1") {
    scenarios.set(customScenarios)
}

val benchmark_1_9 = PerformanceTask.registerPerformanceTask(project, "benchmark_1_9", "1.9.23") {
    scenarios.set(customScenarios)
}

tasks.register("runBenchmarks") {
    dependsOn(benchmark_2_0, benchmark_1_9)
}

```

## Run performance test
Please set up `JAVA_HOME` and `ANDROID_HOME`(if need) variables before run tests

Execute 
```bash
./gradlew runBenchmarks
```

## Visualise results

To analyse benchmark results,
you need to have installed [Kotlin notebook](https://blog.jetbrains.com/kotlin/2023/07/introducing-kotlin-notebook/) 
plugin in IntelliJ IDEA.

Please open [benchmarkResult.ipynb](benchmarkResult.ipynb) and run it with `Run All` action to compare produced results. 

## Troubleshooting

It is possible that a new warning appears in the build with kotlin `2.0.0`. 
Please either fix them or disable `allWarningsAsErrors` compiler option if you use it.

If  [dependencies verification](https://docs.gradle.org/8.2.1/userguide/dependency_verification.html#sub:enabling-verification) is enabled,
please check that both kotlin `1.9` and kotlin `2.0` dependencies are included.

The `JSON` build report output type is available since kotlin `2.0.0-RC1` and kotlin `1.9.23`. 
If a `kotlinDsl` plugin is applied in the `buildSrc` subproject,
we recommend to also apply `kotlin(“jvm”)` plugin to avoid any issues with unknown output type.
