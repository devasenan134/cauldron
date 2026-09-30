plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// The release signing key stays outside the repository, in ~/.gradle/gradle.properties:
//   CAULDRON_KEYSTORE, CAULDRON_KEYSTORE_PASSWORD, CAULDRON_KEY_ALIAS
// Without one, builds are signed with this computer's Android debug key.
//
// Self-hosting: point a build at your own server and Google project with
//   -PapiUrl=https://cauldron.example.com -PgoogleClientId=<your web client ID> -PappId=<your.package.name>
// (see SELF_HOSTING.md). The defaults build the app for cauldron.craftingtable.cc.
fun privateSetting(name: String): String? = (project.findProperty(name) as String?)?.takeIf { it.isNotBlank() }

android {
    namespace = "io.github.devasenan134.cauldron"
    compileSdk = 37

    defaultConfig {
        applicationId = privateSetting("appId") ?: "io.github.devasenan134.cauldron"
        minSdk = 28
        targetSdk = 36
        versionCode = 17
        versionName = "0.11.0"

        // -PapiUrl=http://10.0.2.2:8766 points a build at a local test server.
        buildConfigField("String", "API_URL", "\"${privateSetting("apiUrl") ?: "https://cauldron.craftingtable.cc"}\"")
        // Google sign-in asks for an ID token meant for the server (its "web" OAuth client), which the
        // server checks. Not a secret: the website hands it to every visitor too.
        buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", "\"${privateSetting("googleClientId")
            ?: "880824039451-klph2c1nnqmtp52ai5ma9j1eqj134ke2.apps.googleusercontent.com"}\"")
    }

    signingConfigs {
        val keystore = privateSetting("CAULDRON_KEYSTORE")
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = privateSetting("CAULDRON_KEYSTORE_PASSWORD")
                keyAlias = privateSetting("CAULDRON_KEY_ALIAS") ?: "cauldron"
                keyPassword = storePassword
            }
        }
    }

    buildTypes {
        // Both builds use the release key: Google sign-in only works for apps signed with a key
        // registered in Google Cloud, and one key means one registration.
        debug {
            manifestPlaceholders["cleartext"] = "true" // for a plain-http test server
            // -PdevToken=<session token> starts a test build signed in, without Google (emulator testing).
            buildConfigField("String", "DEV_TOKEN", "\"${project.findProperty("devToken") ?: ""}\"")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
        release {
            manifestPlaceholders["cleartext"] = "false"
            buildConfigField("String", "DEV_TOKEN", "\"\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    // Local only: release speed (R8, no debugging) but pointed at a test server and signed in with
    // -PdevToken, to measure animations the way a phone runs them. Never published.
    buildTypes.create("profiling") {
        initWith(buildTypes.getByName("release"))
        matchingFallbacks += "release"
        buildConfigField("String", "DEV_TOKEN", "\"${project.findProperty("devToken") ?: ""}\"")
        manifestPlaceholders["cleartext"] = "true"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.browser) // opens recipe videos and sources

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.credentials)
    implementation(libs.credentials.play.services)
    implementation(libs.googleid)
}
