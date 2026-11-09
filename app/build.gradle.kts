plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room3)
    id("de.uchilab.privacy-check")
}

kotlin { jvmToolchain(21) }

// Exported schema of the task database (ADR 0003); checked in, input of later migrations and the field list.
room3 { schemaDirectory("$projectDir/schemas") }

// Exact versions per configuration in app/gradle.lockfile; also the input of the vulnerability scan.
dependencyLocking { lockAllConfigurations() }

android {
    namespace = "de.uchilab.todo"
    compileSdk = 37

    defaultConfig {
        applicationId = "de.uchilab.todo"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            // R8: shrink and obfuscate; the unsigned release build runs in the pipeline.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures { compose = true }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Robolectric for recent Android SDKs reflects into JDK internals.
            it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
            // Output dir of EmulatorSeedTest (tools/checks/emulator-tasks.sh); without it the test is skipped.
            providers.gradleProperty("uchilab.seedOut").orNull?.let { dir -> it.systemProperty("uchilab.seedOut", dir) }
            // Output dir of ShowcaseSeedTest (tools/checks/emulator-showcase.sh); without it the test is skipped.
            providers.gradleProperty("uchilab.showcaseOut").orNull?.let { dir -> it.systemProperty("uchilab.showcaseOut", dir) }
        }
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        // androidx.lifecycle ships lint checks built against an older Kotlin analysis API than AGP's lint;
        // lint skips them itself. Revisit when androidx.lifecycle is updated.
        disable += "ObsoleteLintCustomCheck"
        // The pipeline must not turn red without a code change (exception: a new vulnerability, which the
        // scheduled vulnerability scan reports). These checks depend on newly published versions, online
        // data or the current date. Newer versions arrive as Renovate pull requests, vulnerabilities via
        // tools/checks/vuln-scan.sh, so no information is lost (ADR 0002).
        disable += listOf(
            "GradleDependency", // newer version of a dependency published (Google Maven index)
            "NewerVersionAvailable", // newer version on Maven Central (network)
            "AndroidGradlePluginVersion", // newer AGP published
            "OutdatedLibrary", // library marked outdated in the Google Play SDK Index (online)
            "PlaySdkIndexDeprecated", // Google Play SDK Index (online)
            "PlaySdkIndexGenericIssues", // Google Play SDK Index (online)
            "PlaySdkIndexNonCompliant", // Google Play SDK Index (online)
            "PlaySdkIndexVulnerability", // Google Play SDK Index (online); vulnerabilities: vuln-scan.sh
            "OldTargetApi", // compares with the highest API level installed on the runner image
            "ExpiringTargetSdkVersion", // depends on the current date (Play deadline)
            "ExpiredTargetSdkVersion", // depends on the current date (Play deadline)
        )
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)

    implementation(libs.room3.runtime)
    implementation(libs.sqlite.framework)
    implementation(libs.kotlinx.coroutines.android)
    ksp(libs.room3.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Robolectric/Compose test (unit tests) and the Room compiler (KSP) bring in older guava versions with
    // known vulnerabilities. A constraint only raises the version where guava is already present; it ships in
    // no APK. Not for androidLintTool: Lint is tested with its own versions, only an AGP update helps there.
    constraints {
        testImplementation(libs.guava) { because("UCH-68: vulnerable guava on the unit test classpath") }
        ksp(libs.guava) { because("UCH-68: vulnerable guava on the KSP processor classpath") }
    }
}

privacyCheck {
    allowlist.set(rootProject.file("tools/checks/allowlist.txt"))
    testAllowlist.set(rootProject.file("tools/checks/allowlist-test.txt"))
    denylist.set(rootProject.file("tools/checks/denylist.txt"))
    permissionAllowlist.set(rootProject.file("tools/checks/permissions-allowlist.txt"))
}

// The checks are only trustworthy if their own tests (incl. negative tests) pass first.
tasks.named("privacyCheck") { dependsOn(gradle.includedBuild("checks").task(":test")) }
