pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        fun MavenArtifactRepository.githubPackages(repository: String) {
            url = uri("https://maven.pkg.github.com/OpenXingZhi/$repository")
            credentials {
                username = providers.gradleProperty("GitHubPackagesUsername").orNull
                    ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("GitHubPackagesPassword").orNull
                    ?: System.getenv("GITHUB_TOKEN")
            }
        }
        maven {
            name = "GitHubPackagesLicense"
            githubPackages("license-library")
        }
        maven {
            name = "GitHubPackagesRemoteConfig"
            githubPackages("remote-config")
        }
        maven {
            name = "GitHubPackagesTrustedTime"
            githubPackages("trusted-time")
        }
    }
}

rootProject.name = "android-bootstrap-build"
include(":android-bootstrap")
