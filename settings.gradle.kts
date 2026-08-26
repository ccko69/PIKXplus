pluginManagement {
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  //  flatDir { dirs(file("libs")) }  // needs ".pom" files.
  // flatDir { dirs = setOf(file("libs")) }
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "PIKXplus"

include(":app")
