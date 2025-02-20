plugins {
  id("com.android.application")
  kotlin("android")
  jacoco
  kotlin("plugin.serialization")
}

jacoco { toolVersion = "0.8.8" }

// ./gradlew test connectedDevelopDebugAndroidTest jacocoTestReport
// task must be created, examples in Kotlin which call tasks.jacocoTestReport do not work
tasks.create("jacocoTestReport", JacocoReport::class.java) {
  group = "Reporting"
  description = "Generate Jacoco coverage reports."

  reports {
    xml.required.set(true)
    html.required.set(true)
  }

  val fileFilter =
      listOf(
          "**/R.class",
          "**/R$*.class",
          "**/BuildConfig.*",
          "**/Manifest*.*",
          "**/*Test*.*",
          "android/**/*.*",
          "**/*\$\$serializer.class",
      )

  val developDebug = "developDebug"

  sourceDirectories.setFrom(
      files(listOf("$projectDir/src/main/java", "$projectDir/src/main/kotlin")))

  classDirectories.setFrom(
      files(
          listOf(
              fileTree("$buildDir/intermediates/javac/$developDebug") { exclude(fileFilter) },
              fileTree("$buildDir/tmp/kotlin-classes/$developDebug") { exclude(fileFilter) },
          )))

  // execution data from both unit and instrumentation tests
  executionData.setFrom(
      fileTree(project.buildDir) {
        include(
            // unit tests
            "jacoco/test${"developDebug".capitalize()}UnitTest.exec",
            // instrumentation tests
            "outputs/code_coverage/${developDebug}AndroidTest/connected/**/*.ec",
        )
      })

  // dependsOn("test${"developDebug".capitalize()}UnitTest")
  // dependsOn("connected${"developDebug".capitalize()}AndroidTest")
}

tasks.withType(Test::class.java) {
  (this.extensions.getByName("jacoco") as JacocoTaskExtension).apply {
    isIncludeNoLocationClasses = true
    excludes = listOf("jdk.internal.*")
  }
}

val acraEmail =
    project.rootProject
        .file("local.properties")
        .let { if (it.exists()) it.readLines() else emptyList() }
        .firstOrNull { it.startsWith("acra.email") }
        ?.substringAfter("=")
        ?: System.getenv()["ACRA_EMAIL"] ?: ""

android {
  compileSdk = 31
  defaultConfig {
    versionCode = 31100
    versionName = "3.11.00"
    applicationId = "com.better.alarm"
    minSdk = 16
    targetSdk = 31
    testApplicationId = "com.better.alarm.test"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    multiDexEnabled = true
  }
  buildTypes {
    getByName("debug") {
      isTestCoverageEnabled = true
      buildConfigField("String", "ACRA_EMAIL", "\"$acraEmail\"")
      applicationIdSuffix = ".debug"
    }
    getByName("release") {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.txt")
      buildConfigField("String", "ACRA_EMAIL", "\"$acraEmail\"")
    }
  }

  flavorDimensions.add("default")

  productFlavors {
    create("develop") { applicationId = "com.better.alarm" }
    create("premium") { applicationId = "com.premium.alarm" }
  }

  installation {
    timeOutInMs = 20 * 60 * 1000 // 20 minutes
    installOptions("-d", "-t")
  }

  useLibrary("android.test.runner")
  useLibrary("android.test.base")
  useLibrary("android.test.mock")

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
  testOptions { unitTests.isReturnDefaultValues = true }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
  kotlinOptions {
    freeCompilerArgs =
        freeCompilerArgs + "-Xopt-in=kotlin.RequiresOptIn" + "-Xopt-in=kotlin.Experimental"

    jvmTarget = "1.8"
  }
}

dependencies {
  val coroutinesVersion = "1.6.4"
  val serializationVersion = "1.4.1"
  implementation("ch.acra:acra-mail:5.9.7")
  implementation("com.melnykov:floatingactionbutton:1.3.0")
  implementation("io.reactivex.rxjava2:rxjava:2.2.21")
  implementation("io.reactivex.rxjava2:rxandroid:2.1.1")
  implementation("io.insert-koin:koin-core:3.3.2")
  implementation("androidx.fragment:fragment:1.4.1")
  implementation("androidx.preference:preference:1.2.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutinesVersion")
  implementation("com.google.android.material:material:1.6.1")
  implementation("org.slf4j:slf4j-api:1.7.36")
  implementation("com.github.tony19:logback-android:2.0.1")
  implementation("androidx.multidex:multidex:2.0.1")
  implementation("androidx.datastore:datastore:1.0.0")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:$serializationVersion")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")

  testImplementation("net.wuerl.kotlin:assertj-core-kotlin:0.2.1")
  testImplementation("junit:junit:4.13.2")
  testImplementation("io.mockk:mockk:1.13.3")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")

  val androidxTest = "1.5.0"
  androidTestImplementation("com.squareup.assertj:assertj-android:1.2.0")
  androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
  androidTestImplementation("androidx.test:runner:$androidxTest")
  androidTestImplementation("androidx.test:rules:$androidxTest")
  androidTestImplementation("androidx.test.ext:junit:1.1.5")
}
