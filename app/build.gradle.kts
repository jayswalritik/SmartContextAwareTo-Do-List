plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.smartto_do_list"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.smartto_do_list"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    buildFeatures {
        viewBinding = true
    }

    defaultConfig {
        applicationId = "com.example.smartto_do_list"
        // other configs...

        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf(
                    "room.schemaLocation" to "$projectDir/schemas"
                )
            }
        }
    }

}

dependencies {
    // OMSDroid
    implementation("org.osmdroid:osmdroid-android:6.1.10")

    // Google Play Services Location
    implementation("com.google.android.gms:play-services-location:18.0.0")

    // TapTargetView for coach marks
   // implementation("com.getkeepsafe.taptargetview:taptargetview:1.13.3")

        implementation ("uk.co.samuelwall:material-tap-target-prompt:3.3.2")


    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.core:core:1.12.0")

    // Room database dependencies
    implementation("androidx.room:room-runtime:2.7.1")
    annotationProcessor("androidx.room:room-compiler:2.7.1")

    // Other libs (assuming libs.* are defined in versions catalog)
    implementation(libs.recyclerview)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)

    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.activity:activity:1.8.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    implementation("androidx.work:work-runtime:2.9.0")


}
