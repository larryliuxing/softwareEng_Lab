plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "17"
}

dependencies {
    implementation(project(":core"))
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
}

val mainOutput = sourceSets.main.get().output
val runtimeJars = configurations.runtimeClasspath

tasks.register<Jar>("studioJar") {
    group = "distribution"
    description = "打一个在 Windows 上用 java -jar 打开的配置程序"
    archiveFileName.set("taplab-studio.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    dependsOn(tasks.named("classes"))
    manifest {
        attributes["Main-Class"] = "com.taplab.studio.StudioAppKt"
    }
    from(mainOutput)
    from({
        runtimeJars.get().filter { it.isFile }.map { project.zipTree(it) }
    })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}
