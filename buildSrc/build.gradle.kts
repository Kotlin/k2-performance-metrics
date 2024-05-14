plugins {
    id("org.jetbrains.kotlin.jvm") version "1.9.24"
    `kotlin-dsl`
}

dependencies {
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.9.0.202403050737-r")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}

repositories {
    mavenLocal()
    mavenCentral()
}