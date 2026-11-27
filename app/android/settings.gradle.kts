import java.util.Properties

val localProperties = Properties().apply {
    val localFile = file("local.properties")
    if (localFile.exists()) localFile.inputStream().use(::load)
}

pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
            credentials {
                username = "mapbox"
                password = localProperties.getProperty("MAPBOX_DOWNLOADS_TOKEN", "")
            }
        }
    }
}
rootProject.name = "RoadDamage"
include(":app")
