plugins {
  alias(libs.plugins.android.application) // agp: 8.13.0
  alias(libs.plugins.kotlin.android) // kotlin: 2.1.0
  alias(libs.plugins.kotlin.parcelize)
}

android {
  namespace = "com.ccko.pikxplus"
  compileSdk = 36
  defaultConfig {
    applicationId = "com.ccko.pikxplus"
    minSdk = 30
    targetSdk = 34
    versionCode = 2
    versionName = "2.0"
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

sourceSets { getByName("main") { java.srcDirs("src/main/java") } }

  buildFeatures {
    viewBinding = false
  }
  
}

dependencies {
  // Core AndroidX
  implementation("androidx.core:core-ktx:1.17.0")
  implementation("androidx.appcompat:appcompat:1.7.1")
  implementation("androidx.constraintlayout:constraintlayout:2.2.1")
  implementation("androidx.coordinatorlayout:coordinatorlayout:1.3.0")
  implementation("androidx.activity:activity-ktx:1.8.2")
  implementation("androidx.fragment:fragment-ktx:1.6.2")
  
  implementation ("androidx.preference:preference-ktx:1.2.1")
  
  // Material Design
  implementation("com.google.android.material:material:1.13.0")
  // Lifecycle & ViewModel
  implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
  implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.9.4")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    // ViewPager2
  implementation("androidx.viewpager2:viewpager2:1.0.0")
  implementation("androidx.viewpager2:viewpager2:1.1.0-beta02")
  // Navigation
  implementation("androidx.navigation:navigation-fragment-ktx:2.9.5") 
  implementation("androidx.navigation:navigation-ui-ktx:2.9.5") 
  // RecyclerView
  implementation("androidx.recyclerview:recyclerview:1.3.2")
  // Coroutines
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3") 
  // Coil (Image Loading)
  implementation("io.coil-kt:coil:2.5.0")
  implementation("io.coil-kt:coil-gif:2.5.0")
  implementation("io.coil-kt:coil-video:2.5.0")
  implementation("androidx.exifinterface:exifinterface:1.3.3")
  // Media3 (Video Player)
  implementation("androidx.media3:media3-exoplayer:1.10.1")
  implementation("androidx.media3:media3-ui:1.10.1")
  implementation("androidx.media3:media3-common:1.10.1")
  // glide
  implementation("com.github.bumptech.glide:glide:4.13.2")
  // annotationProcessor("com.github.bumptech.glide:compiler:5.0.7")
  annotationProcessor("com.github.bumptech.glide:compiler:4.13.2")
  
  // implementation("com.davemorrissey.labs:subsampling-scale-image-view:3.10.0")
  // implementation 'androidx.drawerlayout:drawerlayout:1.1.1'
  // fast scroll
  implementation("me.zhanghai.android.fastscroll:library:1.3.0")
  implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0-alpha01")
  
  // CameraX core
  implementation("androidx.camera:camera-camera2:1.4.2")
  implementation("androidx.camera:camera-lifecycle:1.4.2")
  implementation("androidx.camera:camera-view:1.4.2")
  // If you want video capture
  implementation("androidx.camera:camera-video:1.4.2")
  // For audio recording (video needs it)
  implementation("androidx.camera:camera-extensions:1.4.2")
}
