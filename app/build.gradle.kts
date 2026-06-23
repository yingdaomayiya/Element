import com.android.build.gradle.internal.api.BaseVariantOutputImpl

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val ciVersionCode = providers.gradleProperty("ciVersionCode").orNull?.toIntOrNull()
val ciVersionName = providers.gradleProperty("ciVersionName").orNull
val requireReleaseSigning = providers.gradleProperty("requireReleaseSigning")
    .orElse("false")
    .map(String::toBoolean)
    .get()

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
    }

    flavorDimensions += "environment"

    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            buildConfigField("String", "BUILD_ENV", "\"dev\"")
            buildConfigField("String", "BASE_URL", "\"https://dev.example.com/\"")
        }

        // AGP 保留了以 test 开头的 Flavor 名，因此测试环境内部使用 qa。
        create("qa") {
            dimension = "environment"
            applicationIdSuffix = ".test"
            versionNameSuffix = "-test"
            buildConfigField("String", "BUILD_ENV", "\"test\"")
            buildConfigField("String", "BASE_URL", "\"https://test.example.com/\"")
        }

        create("prod") {
            dimension = "environment"
            buildConfigField("String", "BUILD_ENV", "\"prod\"")
            buildConfigField("String", "BASE_URL", "\"https://api.example.com/\"")
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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

    applicationVariants.all {
        val currentVariant = this
        outputs.all {
            val currentOutput = this as BaseVariantOutputImpl
            currentOutput.outputFileName = buildString {
                append(rootProject.name)
                append('-')
                append(currentVariant.flavorName)
                append('-')
                append(currentVariant.buildType.name)
                append("-v")
                append(currentVariant.versionName)
                append('-')
                append(currentVariant.versionCode)
                append(".apk")
            }
        }
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
