buildscript {
    repositories {
        gradlePluginPortal()
        jcenter()
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.4.10")
        classpath("com.android.tools.build:gradle:4.1.1")
    }
}
group = "com.jerry0022.pyco"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}
