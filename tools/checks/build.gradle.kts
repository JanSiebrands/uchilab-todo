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
