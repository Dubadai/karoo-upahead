import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// GitHub release assets. Name the APK <name>.apk (no version), so these links always point at
// the newest (non-pre-)release.
val REPO = "Dubadai/karoo-upahead"
val APK = "upahead.apk"
val RELEASES = "https://github.com/$REPO/releases/latest/download"

// Release signing key, kept out of git. See README "Releases".
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// Build stamp: the git commit this APK was built from, plus "-dirty" when built with uncommitted
// changes, so every installed build can be told apart (Karoo playbook, "Build stamp"). Debug builds
// carry it in versionName (0.1.0-3d85207); every build has BuildConfig.BUILD_STAMP and logs a
// "BuildStamp" line when the extension starts. Release versionName stays clean (riders see it).
fun git(vararg args: String): String = providers.exec {
    commandLine("git", *args)
    isIgnoreExitValue = true
}.standardOutput.asText.get().trim()
val buildStamp = git("rev-parse", "--short=7", "HEAD").ifEmpty { "nogit" } +
    (if (git("status", "--porcelain", "--untracked-files=no").isNotEmpty()) "-dirty" else "")

android {
    namespace = "com.daviddokakis.upahead"
    compileSdk = 36

    buildFeatures { buildConfig = true }

    defaultConfig {
        applicationId = "com.daviddokakis.upahead"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "BUILD_STAMP", "\"$buildStamp\"")
        // Where the Karoo and UpdateCheck find the latest version. To test the update notice,
        // build with -PmanifestUrl=http://localhost:8791/manifest.json (README "Update notices").
        manifestPlaceholders["manifestUrl"] =
            (project.findProperty("manifestUrl") as String?) ?: "$RELEASES/manifest.json"
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            versionNameSuffix = "-$buildStamp"
        }
        release {
            // Your own release key when keystore.properties exists; otherwise the debug key, so
            // the project still builds anywhere (but such APKs can't update a published one).
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":karoo-ext"))
    implementation(project(":karoo-common"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.timber)
    testImplementation("junit:junit:4.13.2")
}

// manifest.json for the GitHub release (upload it next to the APK). The Karoo's Settings › Apps
// page and UpdateCheck read it to offer the update.
//   ./gradlew -q :app:releaseManifest -PreleaseNotes="What's new…"
tasks.register("releaseManifest") {
    group = "release"
    description = "Writes build.nosync/manifest.json for the GitHub release"
    val out = layout.buildDirectory.file("manifest.json")
    val code = android.defaultConfig.versionCode
    val name = android.defaultConfig.versionName
    val appId = android.defaultConfig.applicationId
    val notes = (project.findProperty("releaseNotes") as String?).orEmpty()
    outputs.file(out)
    outputs.upToDateWhen { false }
    doLast {
        val manifest = mapOf(
            "label" to "DDB UpAhead",
            "packageName" to appId,
            "latestApkUrl" to "$RELEASES/$APK",
            "latestVersion" to name,
            "latestVersionCode" to code,
            "developer" to "github.com/Dubadai",
            "description" to "Example Karoo extension.",
            "releaseNotes" to notes,
        )
        val f = out.get().asFile
        f.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(manifest)) + "\n")
        println("Wrote $f ($name, $code)")
    }
}
