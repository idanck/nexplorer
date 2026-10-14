plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * `versionCode` נגזר ממספר הריצה ב-CI ולכן מונוטוני. בבנייה מקומית הוא 1.
 * `-PversionCodeOverride=N` — רק לבדיקת העדכון מקצה לקצה (QA), שמתקינה גרסה ישנה ומעדכנת אותה. לא מתפרסם.
 */
val buildNumber: Int = providers.gradleProperty("versionCodeOverride").orNull?.toIntOrNull()
    ?: (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toIntOrNull() ?: 1
val appVersionName: String = providers.gradleProperty("APP_VERSION_NAME").get()

/**
 * מפתח ה-release מגיע מ-CI (GitHub Secrets), אף פעם לא מהריפו.
 * בלי מפתח, release נחתם במפתח ה-debug: ניתן להתקנה, אבל לא יכול לעדכן התקנה חתומה.
 */
val releaseKeystore: File? = System.getenv("RELEASE_KEYSTORE_PATH")?.let(::file)?.takeIf { it.isFile }

android {
    namespace = "com.idanck.nexplorer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.idanck.nexplorer"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "$appVersionName.$buildNumber"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "UPDATE_MANIFEST_URL", "\"https://nexplorer-updates.idanck.com/latest.json\"")
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            // מאפשר להתקין build מקומי לצד הגרסה החתומה בלי לדרוס אותה.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField("boolean", "UPDATES_ENABLED", "false")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            buildConfigField("boolean", "UPDATES_ENABLED", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    implementation(libs.zip4j)
    implementation(libs.commons.compress)
    implementation(libs.xz)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    // org.json באנדרואיד הוא stub בבדיקות JVM; זה המימוש האמיתי.
    testImplementation(libs.org.json)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.uiautomator)
}
