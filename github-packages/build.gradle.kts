plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
}

description = "Settings plugin that adds OpenXingZhi GitHub Packages repositories"

gradlePlugin {
    plugins {
        create("githubPackages") {
            id = "com.xingzhi.github-packages"
            implementationClass = "com.xingzhi.gradle.GitHubPackagesPlugin"
        }
    }
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/OpenXingZhi/android-bootstrap")
            credentials {
                username = providers.gradleProperty("GitHubPackagesUsername")
                    .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                    .getOrElse("")
                password = providers.gradleProperty("GitHubPackagesPassword")
                    .orElse(providers.environmentVariable("GITHUB_TOKEN"))
                    .getOrElse("")
            }
        }
    }
}
