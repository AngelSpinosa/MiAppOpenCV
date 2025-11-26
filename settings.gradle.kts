pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Repositorio necesario para la librería USB
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "MiAppOpenCV"
include(":app")
include(":openCV")

// --- IMPORTANTE: ---
// He eliminado la línea 'include(":usbSerialForAndroid"...)'.
// No la necesitas porque estamos usando la versión de internet.
// Si la dejas, creará conflictos.