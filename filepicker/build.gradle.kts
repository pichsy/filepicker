plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("kotlin-parcelize")
}

android {
    namespace = "com.pichs.filepicker"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro", "proguard-rules.pro")
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
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.material)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.annotation)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.xwidget)
    implementation("com.github.CarGuo.GSYVideoPlayer:gsyvideoplayer:v10.1.0")

    // brv
    implementation(libs.brv)
    // 弹窗
    implementation(libs.basepopup)
    // 图片加载
    implementation(libs.glide)
}

tasks.register<Javadoc>("javadoc") {
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).charSet = "UTF-8"
    // 只处理 Java 源码，避免 Kotlin 导致 path 为空
    val javaSrc = fileTree("src/main/java") { include("**/*.java") }
    source = javaSrc
    classpath += files(android.bootClasspath.joinToString(File.pathSeparator))
    (options as StandardJavadocDocletOptions).links("http://docs.oracle.com/javase/7/docs/api/")
    (options as StandardJavadocDocletOptions).linksOffline(
        "http://d.android.com/reference",
        "${android.sdkDirectory}/docs/reference"
    )
    exclude("**/BuildConfig.java")
    exclude("**/R.java")
    isFailOnError = false
}

tasks.withType<Javadoc> {
    enabled = false
}

tasks.register<Jar>("androidJavadocsJar") {
    dependsOn("javadoc")
    archiveClassifier.set("javadoc")
    from(tasks.named("javadoc").get().outputs.files)
}

// 此写法可忽略文件夹层级带来的影响
apply(from = "${rootProject.rootDir}/maven.gradle")