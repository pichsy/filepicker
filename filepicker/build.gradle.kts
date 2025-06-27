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

// === maven-publish & signing 配置（Kotlin DSL 迁移版） ===
import java.util.Properties
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.api.tasks.bundling.Jar

plugins {
    id("maven-publish")
    id("signing")
}

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

val ossrhUsername: String? = localProps.getProperty("ossrhUsername")
val ossrhPassword: String? = localProps.getProperty("ossrhPassword")
val signingKeyId: String? = localProps.getProperty("signing.keyId")
val signingPassword: String? = localProps.getProperty("signing.password")
val signingSecretKeyRingFile: String? = localProps.getProperty("signing.secretKeyRingFile")

val PUBLISH_GROUP_ID: String by project
val PUBLISH_ARTIFACT_ID: String by project
val PUBLISH_VERSION: String by project
val PUBLISH_NAME: String by project
val POM_URL: String by project
val POM_LICENSE_NAME: String by project
val POM_LICENSE_URL: String by project
val POM_DEVELOPER_ID: String by project
val POM_DEVELOPER_NAME: String by project
val POM_DEVELOPER_EMAIL: String by project
val POM_SCM_CONNECTION: String by project
val POM_SCM_DEV_CONNECTION: String by project
val POM_SCM_URL: String by project

// 源码 jar
val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from("src/main/java")
    exclude("**/R.class", "**/BuildConfig.class")
}

// javadoc jar 已在上方定义为 androidJavadocsJar

publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = PUBLISH_GROUP_ID
            artifactId = PUBLISH_ARTIFACT_ID
            version = PUBLISH_VERSION
            artifact("${'$'}buildDir/${'$'}PUBLISH_NAME")
            artifact(sourcesJar.get())
            artifact(tasks["androidJavadocsJar"])
            pom {
                name.set(PUBLISH_ARTIFACT_ID)
                description.set(POM_LICENSE_URL)
                url.set(POM_URL)
                licenses {
                    license {
                        name.set(POM_LICENSE_NAME)
                        url.set(POM_LICENSE_URL)
                    }
                }
                developers {
                    developer {
                        id.set(POM_DEVELOPER_ID)
                        name.set(POM_DEVELOPER_NAME)
                        email.set(POM_DEVELOPER_EMAIL)
                    }
                }
                scm {
                    connection.set(POM_SCM_CONNECTION)
                    developerConnection.set(POM_SCM_DEV_CONNECTION)
                    url.set(POM_SCM_URL)
                }
            }
        }
    }
    repositories {
        maven {
            name = PUBLISH_NAME
            val releasesRepoUrl = "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
            val snapshotsRepoUrl = "https://s01.oss.sonatype.org/content/repositories/snapshots/"
            url = uri(if (PUBLISH_VERSION.endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl)
            credentials {
                username = ossrhUsername
                password = ossrhPassword
            }
        }
    }
}

signing {
    useInMemoryPgpKeys(signingKeyId, signingPassword, signingSecretKeyRingFile?.let { file(it).readText() })
    sign(publishing.publications["release"])
}
// === END maven-publish & signing 配置 ===
