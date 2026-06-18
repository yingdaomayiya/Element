plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val buildEnv = providers.gradleProperty("buildEnv").orElse("dev").get()
val ciVersionCode = providers.gradleProperty("ciVersionCode").orNull?.toIntOrNull()
val ciVersionName = providers.gradleProperty("ciVersionName").orNull
val requireReleaseSigning = providers.gradleProperty("requireReleaseSigning")
    .orElse("false")
    .map(String::toBoolean)
    .get()

val baseUrl = when (buildEnv) {
    "dev" -> "https://dev.example.com/"
    "test" -> "https://test.example.com/"
    "prod" -> "https://api.example.com/"
    else -> error("不支持的 buildEnv=$buildEnv，可选值：dev、test、prod")
}

android {
    namespace = "com.example.remotedabao"
    compileSdk = 35

    val releaseStoreFile = System.getenv("ANDROID_KEYSTORE_FILE")
    val releaseStorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
    val releaseKeyAlias = System.getenv("ANDROID_KEY_ALIAS")
    val releaseKeyPassword = System.getenv("ANDROID_KEY_PASSWORD")
    val hasReleaseSigning = listOf(
        releaseStoreFile,
        releaseStorePassword,
        releaseKeyAlias,
        releaseKeyPassword
    ).all { !it.isNullOrBlank() }

    if (requireReleaseSigning && !hasReleaseSigning) {
        error(
            "Release 构建缺少签名环境变量：" +
                "ANDROID_KEYSTORE_FILE、ANDROID_KEYSTORE_PASSWORD、" +
                "ANDROID_KEY_ALIAS、ANDROID_KEY_PASSWORD"
        )
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("releaseFromEnvironment") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "com.example.remotedabao"
        minSdk = 24
        targetSdk = 35
        versionCode = ciVersionCode ?: 1
        versionName = ciVersionName ?: "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "BUILD_ENV", "\"$buildEnv\"")
        buildConfigField("String", "BASE_URL", "\"$baseUrl\"")
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-$buildEnv-debug"
        }

        release {
            isDebuggable = false
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("releaseFromEnvironment")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
