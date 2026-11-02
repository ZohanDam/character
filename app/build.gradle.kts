plugins { id("com.android.application") }

android {
    namespace = "com.zohandam.character"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.zohandam.character"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
        buildConfigField("String", "GITHUB_REPOSITORY", "\"ZohanDam/character\"")
    }
    buildFeatures { buildConfig = true }
    signingConfigs {
        create("release") {
            val keyPath = System.getenv("CHARACTER_KEYSTORE")
            if (keyPath != null) {
                storeFile = file(keyPath)
                storePassword = System.getenv("CHARACTER_STORE_PASSWORD")
                keyAlias = "character"
                keyPassword = System.getenv("CHARACTER_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies { testImplementation("junit:junit:4.13.2") }
