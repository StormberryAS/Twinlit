import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// Signing credentials never live in this repo. Locally they come from
// android/keystore.properties (gitignored); in CI from ORG_GRADLE_PROJECT_* env vars.
// Missing credentials must not break `assembleDebug`, so everything below is optional.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) load(FileInputStream(keystorePropertiesFile))
}

fun signingValue(propKey: String, gradleKey: String): String? =
    keystoreProperties.getProperty(propKey) ?: providers.gradleProperty(gradleKey).orNull

val releaseStoreFile = signingValue("storeFile", "RELEASE_STORE_FILE")
val releaseStorePassword = signingValue("storePassword", "RELEASE_STORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "RELEASE_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "RELEASE_KEY_PASSWORD")
val canSignRelease = releaseStoreFile != null && file(releaseStoreFile).exists()

// Google Play is a SEPARATE package, deliberately. `no.stormberry.twinlit` is the sovereign build:
// our key, our certificate, shipped through GitHub Releases, Obtainium and Zapstore, with the
// NIP-C1 certificate link vouching for it. `-PplayBuild=true` builds `no.stormberry.twinlit.play`
// instead, which Google re-signs with an app signing key it holds and never hands back. Two
// package names mean two apps as far as Android is concerned: they install side by side and
// neither can ever update the other, which is what keeps existing installs safe from anything that
// happens on Play. One package name with two certificates would be the Mullvad failure: mutually
// un-installable builds and a forced uninstall to switch channel. Same pattern as
// UsernameGenerator's and MetadataScrubber's Play builds.
//
// THE TWO EDITIONS ARE THE SAME APP (Marcos, 2026-10-31, the parity rule). There is no manifest
// overlay and no compile-time edition switch. playBuild changes
// five things only, and nothing in the app's code, manifest or resources:
//   1. the application ID (`.play`, here);
//   2. the signing (the Play upload key, under signingConfigs);
//   3. the versionCode (1000 + the sovereign code, under defaultConfig);
//   4. the guards on the task graph (below);
//   5. the archive name (`twinlit-play`, at the end of this file).
// There are no product flavours (a flavour-level signingConfig is overridden by the build type's,
// and a flavour dimension renames every task and breaks the hardcoded APK path in
// .github/workflows/android-release.yml and the regex of zapstore.yaml). ManifestTest pins that no
// overlay or switch exists. tools/check-play-bundle.sh checks the built bundle: the same nine
// permissions as expected-permissions.txt and the alarm components.
val playBuild = when (providers.gradleProperty("playBuild").orNull?.lowercase()) {
    null -> false
    // A bare `-PplayBuild` arrives as an empty string. Treating that as false would hand back
    // a sovereign artefact from a command that plainly meant Play.
    "", "true", "1", "yes" -> true
    "false", "0", "no" -> false
    else -> error(
        "-PplayBuild=${providers.gradleProperty("playBuild").get()} is not a recognised " +
            "value. Use -PplayBuild=true, or omit the property for the sovereign build.",
    )
}

// The Play upload key is a separate key with a separate job: it proves who uploaded the
// bundle and never reaches a device, so it must never be the release key. If it is ever
// compromised it can be reset from the Play Console without touching a single install.
// android/keystore.properties names the file, type and alias and holds no password:
// PLAY_UPLOAD_STORE_PASSWORD and PLAY_UPLOAD_KEY_PASSWORD come from the keyring, as
// environment variables or Gradle properties. The release key's lookups above are unchanged.
fun uploadValue(propKey: String, gradleKey: String): String? =
    keystoreProperties.getProperty(propKey)
        ?: providers.gradleProperty(gradleKey).orNull
        ?: providers.environmentVariable(gradleKey).orNull

val uploadStoreFile = uploadValue("playUploadStoreFile", "PLAY_UPLOAD_STORE_FILE")
val uploadStoreType = uploadValue("playUploadStoreType", "PLAY_UPLOAD_STORE_TYPE")
val uploadStorePassword = uploadValue("playUploadStorePassword", "PLAY_UPLOAD_STORE_PASSWORD")
val uploadKeyAlias = uploadValue("playUploadKeyAlias", "PLAY_UPLOAD_KEY_ALIAS")
val uploadKeyPassword = uploadValue("playUploadKeyPassword", "PLAY_UPLOAD_KEY_PASSWORD")
val canSignPlayUpload = uploadStoreFile != null && file(uploadStoreFile).exists() &&
    uploadStorePassword != null && uploadKeyAlias != null && uploadKeyPassword != null

// The two keys must be two different files. A Play upload config pointing at the release
// keystore (or a byte-for-byte copy of it) would enrol the sovereign certificate in Play App
// Signing, which cannot be undone. Refused before anything is built.
if (releaseStoreFile != null && uploadStoreFile != null) {
    val rel = file(releaseStoreFile)
    val upl = file(uploadStoreFile)
    if (rel.exists() && upl.exists() &&
        (rel.canonicalFile == upl.canonicalFile || rel.readBytes().contentEquals(upl.readBytes()))
    ) {
        error(
            "The Play upload keystore and the release keystore are the same key " +
                "($uploadStoreFile). The release key signs the Zapstore APK only and must never " +
                "reach Play; the upload key signs the Play bundle only.",
        )
    }
}

// The upload passwords are read while Gradle configures the build, and gradle.properties turns
// the configuration cache on. A cached configuration stores those values under
// android/.gradle/configuration-cache, which breaks the rule that the password is never written
// anywhere. So the build refuses to configure, before any cache entry exists, whenever an upload
// password is set and the cache is in use.
abstract class BuildFeaturesHolder @Inject constructor(val features: org.gradle.api.configuration.BuildFeatures)

val configurationCacheRequested =
    objects.newInstance<BuildFeaturesHolder>().features.configurationCache.requested.getOrElse(false)
if (configurationCacheRequested && (uploadStorePassword != null || uploadKeyPassword != null)) {
    error(
        "Play upload passwords are set, so this build must run with --no-configuration-cache; " +
            "otherwise Gradle would store them in its configuration cache.",
    )
}

// Guards on the RESOLVED TASK GRAPH, not on the command line, because the command line lies:
// `build`, `assemble` and Gradle's `bR` abbreviation reach a release artefact without
// containing "bundleRelease" or "assembleRelease". packageRelease (APK) and signReleaseBundle
// (AAB) are the two tasks that consume a signing config, so the checks belong on them.
//
// The dangerous mistake is the one with no flag: `bundleRelease` on its own would write an AAB
// carrying the SOVEREIGN application ID, alarms and nine permissions included, signed with the
// release key. Uploading it would permanently claim no.stormberry.twinlit on Play and enrol the
// release certificate in Play App Signing, which cannot be undone.
tasks.matching { it.name == "packageRelease" || it.name == "signReleaseBundle" }.configureEach {
    val isBundle = name == "signReleaseBundle"
    val play = playBuild
    val haveUploadKey = canSignPlayUpload
    doFirst {
        if (isBundle && !play) {
            error(
                "Refusing to build a release bundle without -PplayBuild=true. This AAB would " +
                    "carry the sovereign application ID signed with the release key, and " +
                    "uploading it would permanently claim that package on Play. The sovereign " +
                    "channel ships APKs; Play takes the bundle, with -PplayBuild=true.",
            )
        }
        if (!isBundle && play) {
            error(
                "Refusing to build a release APK with -PplayBuild=true. Play takes the app " +
                    "bundle: run :app:bundleRelease -PplayBuild=true. An installable .play APK " +
                    "has no destination and must never be mistaken for the Zapstore one.",
            )
        }
        if (isBundle && play && !haveUploadKey) {
            error(
                "playBuild=true but the Play upload keystore is not fully configured. " +
                    "playUploadStoreFile, playUploadKeyAlias (android/keystore.properties) and " +
                    "PLAY_UPLOAD_STORE_PASSWORD and PLAY_UPLOAD_KEY_PASSWORD (from the keyring) " +
                    "must all be set, and the store file must exist.",
            )
        }
    }
}

// ONE VISIBLE VERSION, TWO CODE LINES. The sovereign versionCode and versionName in
// defaultConfig below are the only numbers ever bumped by hand. The Play build derives its
// code from them and shows the same version name (Marcos, 2026-10-05, for MetadataScrubber:
// "1.0.0 for both"):
//   Play versionCode = 1000 + sovereign versionCode   (0.0.1 code 1 -> code 1001)
//   Play versionName = sovereign versionName          (0.0.1 -> 0.0.1)
// Play only needs monotonic codes within its own package, and the offset keeps them distinct
// from the sovereign line and rising with it. The package name and the signing key are what
// keep the two apart, not the version. Build every Play upload from a sovereign release tag.
val playVersionCodeOffset = 1000

val cityAssetsDir = layout.buildDirectory.dir("generated/assets/cities")

android {
    namespace = "no.stormberry.twinlit"
    // compileSdk is 37.1 because androidx (core-ktx 1.19.0, lifecycle 2.11.0,
    // compose 1.11.4) requires compiling against API 37 or later. targetSdk stays
    // at 36, which is what Google Play requires for new apps from 31 August 2026;
    // the two are deliberately independent.
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        // The sovereign build keeps the original ID; the Play build gets its own. Once the Play
        // listing is published this string is permanent, because Play treats a changed
        // application ID as a completely different app. `namespace` above is unrelated: it is
        // the R class package, so nothing in the Kotlin source changes with the flag.
        applicationId = if (playBuild) "no.stormberry.twinlit.play" else "no.stormberry.twinlit"
        minSdk = 24
        targetSdk = 36
        // 0.0.1 (code 1, Play 1001), 2026-11: Twinlit, the merged sun and moon app that takes over
        // from SunApp and MoonApp. Both editions are the same code (the parity rule): they differ in
        // package name, versionCode and signing only. Nine install-time permissions, no INTERNET.
        versionCode = 1
        versionName = "0.0.1"
        // Play line, derived from the code above (see playVersionCodeOffset); same versionName.
        if (playBuild) {
            versionCode = playVersionCodeOffset + versionCode!!
        }

        // Instrumented tests (src/androidTest, WidgetRenderTest from M6) run on the platform's own
        // runner with JUnit 3, so they add no library: no androidx.test, no new Maven dependency.
        // They are not part of the green build. Density-split PNGs are generated at build time from vectors and are a source of
        // build nondeterminism, so keep the vectors only.
        vectorDrawables.generatedDensities()
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }

    // The platform's optional test libraries, for src/androidTest only (nothing in main uses them,
    // and they add nothing to the app's manifest or APK).
    useLibrary("android.test.runner")
    useLibrary("android.test.base")

    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                // Pinned explicitly: AGP documents these as "if null, a default value is
                // used" without saying what that default is, so an AGP upgrade could
                // silently change what the APK carries. minSdk 24 means v1 JAR signing
                // is dead weight; v2 covers 7.0+, v3 enables key rotation later.
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = false
            }
        }
        if (canSignPlayUpload) {
            create("playUpload") {
                storeFile = file(uploadStoreFile!!)
                if (uploadStoreType != null) storeType = uploadStoreType
                storePassword = uploadStorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
                // v1 stays ON here, unlike the release config above. An app bundle is signed
                // like a JAR, and this signature exists only so Play can verify who uploaded
                // it: Play strips it and re-signs the APKs it generates with the app signing key
                // it holds. Nothing a device installs from Play carries this signature.
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = false
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // The Play build never touches the release key and the sovereign build never touches
            // the upload key. Both stay conditional so a clean checkout can still assembleDebug.
            if (playBuild) {
                if (canSignPlayUpload) signingConfig = signingConfigs.getByName("playUpload")
            } else if (canSignRelease) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    // The city catalogue is shared with the web app and with the eight sibling
    // Labs apps. One source of truth: data/cities.tsv (byte-identical to the one SunApp and MoonApp ship, pinned by
    // SHA-256 in CatalogueHashTest) is copied into assets at build time rather than
    // duplicated here.
    sourceSets["main"].assets.directories.add(cityAssetsDir.get().asFile.absolutePath)

    buildFeatures {
        compose = true
        buildConfig = false
        resValues = false
        shaders = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // java.time is API 26+ and minSdk is 24, so two API levels would not have
        // it. Every solar and alarm calculation in this app is java.time, so
        // desugaring is load-bearing rather than a convenience. Note the desugar
        // library carries its OWN tzdb rather than reading Mainline updates, so on
        // API 24 and 25 a government moving a DST transition can leave the zone
        // rules stale until this dependency is bumped. See android/README.md.
        isCoreLibraryDesugaringEnabled = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json",
            )
        }
    }

    androidResources {
        // Deliberately NOT adding cities.tsv to noCompress, which is the opposite of
        // the choice UsernameGenerator made for its word lists. That file is ~900 KiB
        // raw and roughly 370 KiB deflated, AssetManager.open() inflates it
        // transparently, and the inflate cost lands inside the parse budget we were
        // going to pay anyway. Storing it uncompressed would add half a megabyte to
        // the APK for no measurable gain.
    }

    dependenciesInfo {
        // Strips the Google-signed dependency blob from the APK. It is encrypted, it is
        // not reproducible, and for an app whose whole claim is "nothing leaves the
        // device" an opaque metadata block is the wrong look.
        includeInApk = false
        includeInBundle = false
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        disable += setOf(
            // Version bumps are a deliberate, reviewed act here, not something lint
            // should fail the build over. See gradle/libs.versions.toml.
            "GradleDependency",
            "AndroidGradlePluginVersion",
            "ObsoleteLintCustomCheck",
            // targetSdk 36 is intentional. It is what Google Play requires for new
            // apps from 31 August 2026, and API 37 opts into runtime behaviour this
            // app has not been tested against. compileSdk is already 37.1.
            "OldTargetApi",
        )
    }
}

// Under -PplayBuild=true the artefact is named for what it is, so a Play bundle can never be
// mistaken for a sovereign one on disk. Deliberately NOT set for the sovereign build, whose
// app-release.apk path the release workflow expects.
if (playBuild) {
    base { archivesName = "twinlit-play" }
}

kotlin {
    // The JDK that COMPILES, as distinct from the bytecode level, which stays at 17 in
    // compileOptions above and in jvmTarget below. Pinned to 21 because that is what
    // .github/workflows/android-release.yml installs, and a third party rebuilding from a
    // tag is meant to get the same bytes as the released artefact: the compiler version is
    // part of the build definition, not a property of whoever is building.
    //
    // Without this, Gradle compiles with whatever JVM happens to run the daemon. On this
    // machine the default `java` is /usr/lib/jvm/java-25-openjdk-amd64, which on Debian 13
    // is a JRE with no compiler, so the build died outright with "Toolchain installation ...
    // does not provide the required capabilities: [JAVA_COMPILER]". Gradle auto-detects
    // installed JDKs, so any vendor's 21 satisfies this and no machine-specific path is
    // written into the repo.
    jvmToolchain(21)

    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll("-Xjvm-default=all")
    }
}

val copyCityData by tasks.registering(Copy::class) {
    description = "Copies the packed city catalogue from the repo root into app assets."
    group = "build"
    from(rootProject.file("../data")) {
        include("cities.tsv")
    }
    into(cityAssetsDir.map { it.dir("data") })
    // Deterministic ordering, so the APK is byte-reproducible across machines.
    duplicatesStrategy = DuplicatesStrategy.FAIL
}

// NOTICE travels inside the APK (assets/legal/NOTICE) and the footer's "Licences" link shows it: the BSD
// 2-Clause licence of SunCalc asks for its copyright line and disclaimer in the materials of a binary
// distribution, and a Play download never sees the repository (fix round 2).
val copyNotice by tasks.registering(Copy::class) {
    description = "Copies the repository's NOTICE into app assets (assets/legal/NOTICE)."
    group = "build"
    from(rootProject.file("..")) {
        include("NOTICE")
    }
    into(cityAssetsDir.map { it.dir("legal") })
    duplicatesStrategy = DuplicatesStrategy.FAIL
}

tasks.named("preBuild") { dependsOn(copyCityData, copyNotice) }

// Unit tests read files as text, and Gradle must know every one of them as an input, or a cached run stays
// UP-TO-DATE after one of them changed and a test that would fail is not run (R12, D127; R13, D141; R13-F, D147).
// Outside the module, all in the repository (found from the module directory by `TestFiles`): the root documents
// (README.md, CHANGELOG.md, DISCLAIMER.md, LICENSE, NOTICE, .gitattributes, .gitignore, zapstore.yaml), android/README.md,
// android/.gitignore, android/expected-permissions.txt, the Gradle wrapper properties, the whole of android/tools/
// (the icon source and its pins, the Play bundle check), tools/ (the golden generator), data/ (the city catalogue),
// fastlane/ (the store texts and images) and .github/ (the release workflow). Inside the module: this file, and the
// whole of src (main, test, androidTest, debug and release: Kotlin sources, resources, manifests), which the source,
// resource and manifest tests read as text; a comment or a resource file changes no class, so the compiled classes
// alone do not show the change.
// Paths are relative, so the inputs do not depend on where the repository sits.
tasks.withType<Test>().configureEach {
    val repo = layout.projectDirectory.dir("../..")
    inputs.files(
        repo.file("README.md"), repo.file("CHANGELOG.md"), repo.file("DISCLAIMER.md"), repo.file("LICENSE"),
        repo.file("NOTICE"), repo.file(".gitattributes"), repo.file(".gitignore"), repo.file("zapstore.yaml"),
        repo.file("android/README.md"), repo.file("android/.gitignore"), repo.file("android/expected-permissions.txt"),
        repo.file("android/gradle/wrapper/gradle-wrapper.properties"),
    )
        .withPropertyName("repositoryFiles")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(layout.projectDirectory.file("build.gradle.kts"))
        .withPropertyName("moduleBuildScript")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(layout.projectDirectory.dir("src"))
        .withPropertyName("moduleSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    for (dir in listOf("android/tools", "tools", "data", "fastlane", ".github")) {
        inputs.dir(repo.dir(dir))
            .withPropertyName("repository-" + dir.trim('.').replace('/', '-'))
            .withPathSensitivity(PathSensitivity.RELATIVE)
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit)
    // Real org.json ahead of the stubbed framework copy on the unit-test classpath only.
    // Nothing here reaches the APK: AlarmStore compiles and runs against the platform
    // org.json on a device, and this dependency exists purely so the same code can be
    // exercised by a JVM test instead of needing Robolectric or an emulator.
    testImplementation(libs.json)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)

    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.ui.tooling.preview)
}
