plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.pichs.filepicker.demo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pichs.filepicker.demo"
        minSdk = 24
        targetSdk = 35
        versionCode = 400
        versionName = "4.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)

    // 协程
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    //#好用必不可少的三方库
    implementation(libs.xwidget)
    implementation(libs.xxpermissions)
    implementation(libs.glide)
    implementation(libs.okhttp)

    // 基础库
    implementation(libs.xbase)
    implementation(project(":filepicker"))

    // brv
    implementation(libs.brv)
    // 弹窗
    implementation(libs.basepopup)

}