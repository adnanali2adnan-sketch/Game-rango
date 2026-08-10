import java.net.URL
import java.net.HttpURLConnection
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.net.ssl.SSLContext
import javax.net.ssl.HttpsURLConnection
import java.security.cert.X509Certificate

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.rangocompanion.fgtpq"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  implementation("com.google.mlkit:text-recognition:16.0.0")
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}

tasks.register("uploadApk") {
  doLast {
    // Bypass SSL Verification for expired certificates (e.g. bashupload.com / file.io in future years)
    try {
      val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate>? = null
        override fun checkClientTrusted(certs: Array<X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(certs: Array<X509Certificate>?, authType: String?) {}
      })
      val sc = SSLContext.getInstance("SSL")
      sc.init(null, trustAllCerts, null)
      HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)
      HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
      println("🛡️ SSL bypass applied successfully.")
    } catch (e: Exception) {
      println("⚠️ SSL bypass failed: ${e.message}")
    }

    val apkFile = file("${project.layout.buildDirectory.get().asFile}/outputs/apk/debug/app-debug.apk")
    if (!apkFile.exists()) {
      println("❌ ERROR: APK file not found at ${apkFile.absolutePath}")
      return@doLast
    }
    println("📦 Found APK: ${apkFile.absolutePath} (${apkFile.length()} bytes)")
    
    val boundary = "====${System.currentTimeMillis()}===="

    // Attempt 1: Upload to file.io (multipart form POST)
    try {
      println("🚀 Uploading to file.io via multipart POST...")
      val url = URL("https://file.io")
      val connection = url.openConnection() as HttpURLConnection
      connection.doOutput = true
      connection.requestMethod = "POST"
      connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
      connection.connectTimeout = 60000
      connection.readTimeout = 60000

      connection.outputStream.use { outputStream ->
        val writer = outputStream.writer()
        writer.write("--$boundary\r\n")
        writer.write("Content-Disposition: form-data; name=\"file\"; filename=\"${apkFile.name}\"\r\n")
        writer.write("Content-Type: application/vnd.android.package-archive\r\n\r\n")
        writer.flush()

        apkFile.inputStream().use { inputStream ->
          inputStream.copyTo(outputStream)
        }
        outputStream.flush()

        writer.write("\r\n--$boundary--\r\n")
        writer.flush()
      }

      val responseCode = connection.responseCode
      if (responseCode in 200..299) {
        val responseText = connection.inputStream.bufferedReader().use { it.readText() }.trim()
        println("=== UPLOAD SUCCESS (file.io) ===")
        println(responseText)
        println("======================")
      } else {
        val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error body"
        println("⚠️ file.io failed with code $responseCode: $errorText")
      }
    } catch (e: Exception) {
      println("⚠️ file.io exception: ${e.message}")
    }

    // Attempt 2: Upload to tmpfiles.org (multipart form POST)
    try {
      println("🚀 Uploading to tmpfiles.org via multipart POST...")
      val url = URL("https://tmpfiles.org/api/v1/upload")
      val connection = url.openConnection() as HttpURLConnection
      connection.doOutput = true
      connection.requestMethod = "POST"
      connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
      connection.connectTimeout = 60000
      connection.readTimeout = 60000

      connection.outputStream.use { outputStream ->
        val writer = outputStream.writer()
        writer.write("--$boundary\r\n")
        writer.write("Content-Disposition: form-data; name=\"file\"; filename=\"${apkFile.name}\"\r\n")
        writer.write("Content-Type: application/vnd.android.package-archive\r\n\r\n")
        writer.flush()

        apkFile.inputStream().use { inputStream ->
          inputStream.copyTo(outputStream)
        }
        outputStream.flush()

        writer.write("\r\n--$boundary--\r\n")
        writer.flush()
      }

      val responseCode = connection.responseCode
      if (responseCode in 200..299) {
        val responseText = connection.inputStream.bufferedReader().use { it.readText() }.trim()
        println("=== UPLOAD SUCCESS (tmpfiles.org) ===")
        println(responseText)
        println("======================")
      } else {
        val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error body"
        println("⚠️ tmpfiles.org failed with code $responseCode: $errorText")
      }
    } catch (e: Exception) {
      println("⚠️ tmpfiles.org exception: ${e.message}")
    }
  }
}

