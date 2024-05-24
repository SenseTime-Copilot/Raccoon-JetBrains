import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import java.nio.charset.Charset

fun properties(key: String) = providers.gradleProperty(key)
fun environment(key: String) = providers.environmentVariable(key)

fun getDefaultPackageId() = properties("defaultPackageId")
fun getDefaultPackageName() = properties("defaultPackageName")

plugins {
    id("java") // Java support
    alias(libs.plugins.kotlin) // Kotlin support
    alias(libs.plugins.gradleIntelliJPlugin) // Gradle IntelliJ Plugin
    alias(libs.plugins.changelog) // Gradle Changelog Plugin
    alias(libs.plugins.qodana) // Gradle Qodana Plugin
    alias(libs.plugins.kover) // Gradle Kover Plugin
    alias(libs.plugins.gradleKotlinSerializationPlugin) // Gradle Kotlin Serialization Plugin
}

group = properties("pluginGroup").get()
version = properties("pluginVersion").get()

// Configure project's dependencies
repositories {
    mavenCentral()
}

// Dependencies are managed with Gradle version catalog - read more: https://docs.gradle.org/current/userguide/platforms.html#sub:version-catalog
dependencies {
//    implementation(libs.annotations)
//    implementation(libs.bundles.ktor)
//    implementation(libs.java.jwt)
    implementation(libs.flexmark.all)
    implementation(libs.bundles.okhttp3)
    implementation(libs.kotlinx.serialization)
}

// Set the JVM language level used to build the project. Use Java 11 for 2020.3+, and Java 17 for 2022.2+.
kotlin {
    @Suppress("UnstableApiUsage")
    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(17)
        vendor = JvmVendorSpec.JETBRAINS
    }
}

// Configure Gradle IntelliJ Plugin - read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    pluginName =
        (environment("PACKAGE_ID").getOrNull()?.takeIf { it.isNotBlank() } ?: getDefaultPackageId().get()) + "-plugin"
    logger.info("IntelliJ pluginName = $pluginName")

    version = properties("platformVersion")
    type = properties("platformType")

    // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file.
    plugins = properties("platformPlugins").map { it.split(',').map(String::trim).filter(String::isNotEmpty) }
}

// Configure Gradle Changelog Plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
    groups.empty()
    repositoryUrl = properties("pluginRepositoryUrl")
}

// Configure Gradle Qodana Plugin - read more: https://github.com/JetBrains/gradle-qodana-plugin
qodana {
    cachePath = provider { file(".qodana").canonicalPath }
    reportPath = provider { file("build/reports/inspections").canonicalPath }
    saveReport = true
    showReport = environment("QODANA_SHOW_REPORT").map { it.toBoolean() }.getOrElse(false)
}

// Configure Gradle Kover Plugin - read more: https://github.com/Kotlin/kotlinx-kover#configuration
koverReport {
    defaults {
        xml {
            onCheck = true
        }
    }
}

tasks {
    wrapper {
        gradleVersion = properties("gradleVersion").get()
    }

    patchPluginXml {
        version = properties("pluginVersion")
        sinceBuild = properties("pluginSinceBuild")
        untilBuild = properties("pluginUntilBuild")

        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        pluginDescription = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"

            with(it.lines()) {
                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
            }
        }

        val changelog = project.changelog // local variable for configuration cache compatibility
        // Get the latest available change notes from the changelog file
        changeNotes = properties("pluginVersion").map { pluginVersion ->
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }

        // Must save environment provider to task val at configuration time
        // See https://docs.gradle.org/8.4/userguide/configuration_cache.html#config_cache:requirements:reading_sys_props_and_env_vars
        val packageIdKey = "PACKAGE_ID"
        val packageNameKey = "PACKAGE_NAME"
        val packageId = environment(packageIdKey)
        val packageName = environment(packageNameKey)
        val defaultPackageId = getDefaultPackageId()
        val defaultPackageName = getDefaultPackageName()

        fun Provider<String>.getIfNotNullOrBlankOrElse(default: String) =
            getOrNull()?.takeIf { it.isNotBlank() } ?: default

        fun replacePackageVariables(src: String, variables: Map<String, String>): String =
            Regex("""\{([\w ]*)\}""").replace(src.also {
                logger.debug("before replace: \"$it\"")
            }) { matchResult ->
                variables.getValue(matchResult.groupValues[1].trim()).also {
                    logger.info("replace variable: \"${matchResult.groupValues[0]}\" -> \"$it\"")
                }
            }.also { logger.debug("after replace: \"$it\"") }

        // replace plugin.xml in destinationDir with packageVariables, after patchPluginXml(copy plugin.xml to destinationDir)
        doLast {
            val charset: Charset = Charsets.UTF_8
            val pluginXmlFilename = "plugin.xml"
            val packageVariables = mapOf(
                packageIdKey to packageId.getIfNotNullOrBlankOrElse(defaultPackageId.get()),
                packageNameKey to packageName.getIfNotNullOrBlankOrElse(defaultPackageName.get())
            )
            logger.info("Start Replace \"$pluginXmlFilename\"")
            destinationDir.file(pluginXmlFilename).get().asFile.apply {
                logger.info("Replace \"$path\" with packageVariables: $packageVariables")
                writeText(replacePackageVariables(readText(charset), packageVariables), charset)
            }
            logger.info("End Replace \"$pluginXmlFilename\"")
        }

        // always run patchPluginXml task
        outputs.upToDateWhen { false }
    }

    processResources {
        // Must save environment provider to task val at configuration time
        // See https://docs.gradle.org/8.4/userguide/configuration_cache.html#config_cache:requirements:reading_sys_props_and_env_vars
        val packageId = environment("PACKAGE_ID")
        val packageType = environment("PACKAGE_TYPE")
        val apiBaseUrl = environment("API_BASEURL")
        val defaultPackageId = getDefaultPackageId()
        val raccoonConfigJson = Json {
            encodeDefaults = true
            prettyPrint = true
        }

        fun MutableMap<String, JsonElement>.putIfNotNullOrBlank(key: String, value: String?) {
            value?.takeIf { it.isNotBlank() }?.let { put(key, JsonPrimitive(it)) }
        }

        fun File.asJsonFileAndPlus(json: Json, newConfigs: Map<String, JsonElement>) {
            val charset: Charset = Charsets.UTF_8
            logger.info("Start update configs to file \"$path\", $newConfigs")
            writeText(
                json.encodeToString(
                    JsonObject.serializer(),
                    JsonObject(json.decodeFromString(JsonObject.serializer(), readText(charset).also {
                        logger.debug("before update: \"$it\"")
                    }) + newConfigs)
                ).also { logger.debug("after update: \"$it\"") }, charset
            )
            logger.info("End update configs to file \"$name\"")
        }

        // update config json files in destinationDir if packageVariables are present, after processResources(copy resources to destinationDir)
        doLast {
            // update config.json
            buildMap {
                put(
                    "packageId",
                    JsonPrimitive(packageId.getOrNull()?.takeIf { it.isNotBlank() } ?: defaultPackageId.get()))
                putIfNotNullOrBlank("variant", packageType.getOrNull())
            }.let { packageVariables ->
                destinationDir.resolve("configs/config.json").asJsonFileAndPlus(raccoonConfigJson, packageVariables)
            }

            // update RaccoonClient.json
            buildMap {
                putIfNotNullOrBlank("apiBaseUrl", apiBaseUrl.getOrNull())
            }.takeIf { it.isNotEmpty() }?.let { packageVariables ->
                destinationDir.resolve("configs/RaccoonClient.json")
                    .asJsonFileAndPlus(raccoonConfigJson, packageVariables)
            } ?: logger.info("Skip update RaccoonClient.json because of packageVariables is empty")
        }

        // always run processResources task
        outputs.upToDateWhen { false }
    }

    // Configure UI tests plugin
    // Read more: https://github.com/JetBrains/intellij-ui-test-robot
    runIdeForUiTests {
        systemProperty("robot-server.port", "8082")
        systemProperty("ide.mac.message.dialogs.as.sheets", "false")
        systemProperty("jb.privacy.policy.text", "<!--999.999-->")
        systemProperty("jb.consents.confirmation.enabled", "false")
    }

    signPlugin {
        certificateChain = environment("CERTIFICATE_CHAIN")
        privateKey = environment("PRIVATE_KEY")
        password = environment("PRIVATE_KEY_PASSWORD")
    }

    publishPlugin {
        dependsOn("patchChangelog")
        token = environment("PUBLISH_TOKEN")
        // The pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
        // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
        // https://plugins.jetbrains.com/docs/intellij/deployment.html#specifying-a-release-channel
        channels =
            properties("pluginVersion").map { listOf(it.split('-').getOrElse(1) { "default" }.split('.').first()) }
    }
}
