package com.xingzhi.gradle

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.create

abstract class GitHubPackagesExtension {
    abstract val organization: Property<String>
    abstract val modules: ListProperty<String>
}

class GitHubPackagesPlugin : Plugin<Settings> {
    override fun apply(settings: Settings) {
        val extension = settings.extensions.create<GitHubPackagesExtension>("xingzhiGitHubPackages")
        extension.organization.convention("OpenXingZhi")

        settings.gradle.settingsEvaluated {
            val organization = extension.organization.get()
            val username = settings.providers.gradleProperty("GitHubPackagesUsername").orNull
                ?: System.getenv("GITHUB_ACTOR")
            val password = settings.providers.gradleProperty("GitHubPackagesPassword").orNull
                ?: System.getenv("GITHUB_TOKEN")
            settings.dependencyResolutionManagement.repositories {
                extension.modules.get().forEach { repository ->
                    maven {
                        name = "GitHubPackages${repository.toMavenName()}"
                        url = java.net.URI("https://maven.pkg.github.com/$organization/$repository")
                        credentials {
                            this.username = username
                            this.password = password
                        }
                    }
                }
            }
        }
    }
}

private fun String.toMavenName(): String =
    split('-', '_').joinToString("") { part ->
        part.replaceFirstChar(Char::uppercase)
    }
