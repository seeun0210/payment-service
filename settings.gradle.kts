rootProject.name = "payment-service"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)

    repositories {
        mavenCentral()

        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/seeun0210/s-class-common")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: "seeun0210"  // ✅ GITHUB_ACTOR 사용
                password = System.getenv("GITHUB_TOKEN") ?: ""            // ✅ GITHUB_TOKEN 사용
            }
        }
    }
}