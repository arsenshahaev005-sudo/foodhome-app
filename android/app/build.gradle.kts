import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val debugBaseUrl = providers.gradleProperty("FOODHOME_DEBUG_BASE_URL")
    .orElse("")
    .get()
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

android {
    namespace = "market.foodhome.app"
    compileSdk = 37

    defaultConfig {
        // Provisional until Food&Home verifies ownership and store availability.
        applicationId = "market.foodhome.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("String", "DEBUG_BASE_URL", "\"$debugBaseUrl\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("String", "DEBUG_BASE_URL", "\"\"")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.all {
            it.useJUnit()
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

abstract class GenerateBridgeManifestAsset : DefaultTask() {
    @get:InputFile
    abstract val manifestFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val outputFile = outputDirectory.file("manifest.json").get().asFile
        outputFile.parentFile.mkdirs()
        manifestFile.get().asFile.copyTo(outputFile, overwrite = true)
    }
}

androidComponents.onVariants { variant ->
    val taskName = "generate${variant.name.replaceFirstChar { it.uppercaseChar() }}BridgeManifestAsset"
    val generateManifest = tasks.register<GenerateBridgeManifestAsset>(taskName) {
        manifestFile.set(rootProject.layout.projectDirectory.file("../bridge-contract/manifest.json"))
        outputDirectory.set(project.layout.buildDirectory.dir("generated/bridge-assets/${variant.name}"))
    }
    variant.sources.assets?.addGeneratedSourceDirectory(
        generateManifest,
        GenerateBridgeManifestAsset::outputDirectory,
    )
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.webkit)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    androidTestImplementation(composeBom)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)

    testImplementation(libs.junit4)
    testImplementation(libs.org.json)
}
