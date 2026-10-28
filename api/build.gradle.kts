plugins {
    `java-library`
    `maven-publish`
    signing
}

fun prop(name: String): String = rootProject.property(name) as String

val apiVersion = prop("api_version")
version = "$apiVersion.${prop("api_patch")}"
group = prop("maven_group")
base {
    archivesName.set("${prop("archives_base_name")}-api")
}

repositories {
    mavenCentral()
}

val javaVersion = prop("java_version").toInt()

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
    withSourcesJar()
    withJavadocJar()
}

val consumerTest by sourceSets.creating

dependencies {
    testImplementation(platform("org.junit:junit-bom:${prop("junit_version")}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    "consumerTestImplementation"(files(tasks.jar))
    "consumerTestImplementation"(platform("org.junit:junit-bom:${prop("junit_version")}"))
    "consumerTestImplementation"("org.junit.jupiter:junit-jupiter")
    "consumerTestRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(javaVersion)
    options.encoding = "UTF-8"
}

tasks.jar {
    manifest {
        attributes("DX12MC-Api-Version" to apiVersion)
    }
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:all,-missing", "-quiet")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("dx12mc.apiVersion", apiVersion)
    systemProperty("dx12mc.apiSnapshot", file("api-signatures.txt").absolutePath)
    providers.gradleProperty("updateApiSnapshot").orNull?.let { systemProperty("dx12mc.updateApiSnapshot", it) }
    val snapshot = file("api-signatures.txt")
    inputs.property("apiSnapshot", provider { if (snapshot.exists()) snapshot.readText() else "" })
    if (providers.gradleProperty("updateApiSnapshot").isPresent) outputs.upToDateWhen { false }
}

val consumerTestTask = tasks.register<Test>("consumerTest") {
    group = "verification"
    description = "Compiles and runs a consumer against the API jar alone."
    testClassesDirs = consumerTest.output.classesDirs
    classpath = consumerTest.runtimeClasspath
    useJUnitPlatform()
}

tasks.named("check") {
    dependsOn(consumerTestTask)
}

val signingKeyName = rootProject.extra.properties["dx12mcSigningKey"] as String?

publishing {
    publications {
        create<MavenPublication>("api") {
            artifactId = "${prop("archives_base_name")}-api"
            from(components["java"])
            pom {
                name.set("${prop("pom_name")} API")
                description.set("Experimental API for mods and shader packs that build on DX12MC.")
                licenses {
                    license {
                        name.set(prop("license_name"))
                        url.set(prop("license_url"))
                    }
                }
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("${prop("github_packages_url")}/${providers.environmentVariable("GITHUB_REPOSITORY").orElse(prop("github_repository")).get()}")
            credentials {
                username = providers.gradleProperty("gpr.user")
                    .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                    .orElse(prop("developer_id"))
                    .get()
                password = providers.gradleProperty("gpr.key")
                    .orElse(providers.environmentVariable("GITHUB_TOKEN"))
                    .orElse(providers.environmentVariable("GH_TOKEN"))
                    .orElse("")
                    .get()
            }
        }
    }
}

signing {
    useGpgCmd()
    isRequired = signingKeyName != null
    sign(publishing.publications["api"])
}

tasks.withType<Sign>().configureEach {
    onlyIf { signingKeyName != null }
}
