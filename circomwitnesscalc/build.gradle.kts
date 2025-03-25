import com.vanniktech.maven.publish.SonatypeHost

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.vanniktech.maven.publish") version "0.30.0"
    signing
}

android {
    namespace = "io.iden3.circomwitnesscalc"
    compileSdk = 34

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                cppFlags += "-O2 -frtti -fexceptions -Wall -fstack-protector-all"
                abiFilters += listOf("x86_64", "arm64-v8a")
            }
        }
        ndk {
            //noinspection ChromeOsAbiSupport
            abiFilters += listOf("x86_64", "arm64-v8a")
        }
    }

    packaging {
        jniLibs {
            keepDebugSymbols.add("**/*.so")
        }
    }

    sourceSets {
        getByName("main") {
            java.srcDir("src/main/java")
            jniLibs.srcDir("src/main/libs")
        }
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
    kotlinOptions {
        jvmTarget = "11"
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    publishing {
        multipleVariants {
            withJavadocJar()
            withSourcesJar()
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

mavenPublishing {
    coordinates("io.iden3", "circomwitnesscalc", "0.0.1-alpha.4")

    pom {
        name.set("circomwitnesscalc")
        description.set("This library is Android Kotlin wrapper for the [Rapidsnark](https://github.com/iden3/rapidsnark). It enables the generation of proofs for specified circuits within an Android environment.")
        inceptionYear.set("2024")
        url.set("https://github.com/iden3/android-rapidsnark")
        licenses {
            license {
                name.set("Apache License Version 2.0")
                url.set("https://github.com/iden3/android-rapidsnark/blob/main/LICENSE-APACHE")
                distribution.set("https://github.com/iden3/android-rapidsnark/blob/main/LICENSE-APACHE")
            }
            license {
                name.set("MIT License")
                url.set("https://github.com/iden3/android-rapidsnark/blob/main/LICENSE-MIT")
                distribution.set("https://github.com/iden3/android-rapidsnark/blob/main/LICENSE-MIT")
            }
        }
        developers {
            developer {
                id.set("demonsh")
                name.set("Dmytro Sukhyi")
                url.set("https://github.com/demonsh/")
            }
            developer {
                id.set("5eeman")
                name.set("Yaroslav Moria")
                url.set("https://github.com/5eeman/")
            }
        }
        scm {
            url.set("https://github.com/iden3/android-rapidsnark/")
            connection.set("scm:git:git://github.com/iden3/android-rapidsnark.git")
            developerConnection.set("scm:git:ssh://git@github.com/iden3/android-rapidsnark.git")
        }
    }

    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)

    signAllPublications()
}
