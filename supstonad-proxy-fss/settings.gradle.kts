rootProject.name = "supstonad-proxy-fss"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://packages.confluent.io/maven/")
    }
}

include(":server")
