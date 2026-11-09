plugins {
    `java-gradle-plugin`
    alias(libs.plugins.kotlin.jvm)
}

kotlin { jvmToolchain(21) }

dependencies {
    testImplementation(libs.junit)
}

gradlePlugin {
    plugins {
        create("privacyCheck") {
            id = "de.uchilab.privacy-check"
            implementationClass = "de.uchilab.checks.PrivacyCheckPlugin"
        }
    }
}

// UiTitlesShownTest runs the emulator list rule; a change to it must rerun the test (UCH-147).
tasks.test { inputs.file("ui-titles-shown.py") }
