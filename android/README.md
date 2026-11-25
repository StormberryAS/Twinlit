# Twinlit for Android

Sun and moon times and alarms, calculated on the device. Version 0.0.1; the root
[README](../README.md) says what it does and how it relates to SunApp and MoonApp.

## Editions

There are two editions, two separate apps as far as Android is concerned. They are built from the
same code and the same manifest; they differ only in the package name, the signing key and the
version code.

| Package | Where | Version code | Signed by |
|---|---|---|---|
| `no.stormberry.twinlit` | GitHub Releases, Zapstore | 1 | Twinlit's own release key (certificate SHA-256 `d54d480d04ece7e8e3a0215abfacffaa2f0f84e4b0303c224a6b89bc84a77418`), applied by the release workflow |
| `no.stormberry.twinlit.play` | Google Play | 1001 (the sovereign code plus 1000) | Google, under Play App Signing, from a bundle signed with a separate upload key (certificate SHA-256 `c9188922c73d3a48e379673fe994d4f0a6d2367942b7f1e747192bfccdd13c33`) |

They install side by side and **neither can ever update the other**.

The permission claim is checkable on any build:

```sh
aapt2 dump permissions app-debug.apk
```

You should see exactly the nine lines of [Permissions](#permissions) and nothing else. In particular
there is no `android.permission.INTERNET`, so the app cannot reach the network, and no location
permission of any kind.

## Permissions

All nine are **install-time** permissions. Android provides no way to defer one, so all nine appear in
the Zapstore listing the moment you install Twinlit, whether or not you ever create an alarm. What is
genuinely deferred is every grant you can see or refuse:

- **No dialog appears and no special access is requested until you create your first alarm.** Searching
  cities, reading sun and moon times and changing the date ask for nothing.
- On the first alarm the app shows one sheet, not three dialogs, saying plainly that these grants are
  needed for alarms and for nothing else.
- **On Android 7 through 11 nothing is ever requested at all**, because none of these grants exist there.
- Refusing does not cost you the alarm. The rule is saved and armed anyway, and the alarm list states
  exactly what is degraded, for example "it may ring late or not at all" when exact alarms are switched off.

### The nine, one sentence each

| Permission | Why it is here |
|---|---|
| `USE_EXACT_ALARM` | Fires the alarm at the minute you set on Android 13 and later, granted at install because an alarm clock is this app's primary function. |
| `SCHEDULE_EXACT_ALARM` | The same capability on Android 12 and 12L, where `USE_EXACT_ALARM` does not exist yet; capped at `maxSdkVersion="32"` so no newer device is ever asked for it. |
| `POST_NOTIFICATIONS` | The alarm shows through a notification: on Android 13 and later this is what puts the alarm, with Snooze and Dismiss, on the screen and over the lock screen. Without it an alarm still sounds, and opening Twinlit shows the alarm screen to stop it. It is the only runtime dialog in the app. |
| `RECEIVE_BOOT_COMPLETED` | A reboot erases every pending alarm, so they are recomputed and re-armed when the device comes back, including before the first unlock. |
| `WAKE_LOCK` | Keeps the CPU awake for the few seconds between the alarm broadcast landing and the ringing service taking over, so a dozing device cannot sleep through it. |
| `VIBRATE` | Vibrates while ringing; per alarm, and switchable off. |
| `USE_FULL_SCREEN_INTENT` | Puts the ring screen in front of the lock screen instead of a notification a sleeping person has to go looking for. |
| `FOREGROUND_SERVICE` | Ringing runs as a foreground service so the system cannot kill the audio part-way through. |
| `FOREGROUND_SERVICE_SYSTEM_EXEMPTED` | The service type Android 14 and later require for that service. It is the type for apps that hold an exact-alarm permission, which fits an alarm clock. The alarm tone is played on the alarm audio stream (`AudioAttributes` `USAGE_ALARM`). |

### What is deliberately absent

- **No `INTERNET` and no `ACCESS_NETWORK_STATE`.** The sun and moon maths, the city catalogue and the
  IANA zone identifiers are all compiled into the APK. Both permissions are stripped from the merged
  manifest with `tools:node="remove"`, so a future dependency cannot quietly add them back.
- **No location permission, coarse or fine.** Coordinates come from the bundled city catalogue or from
  numbers you type. The app never asks the device where it is. The Auto appearance uses the chosen place.
- **No `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.** Alarms are armed with `setAlarmClock`, which is already
  exempt from Doze deferral.
- **No `SYSTEM_ALERT_WINDOW` and no `QUERY_ALL_PACKAGES`.**

`expected-permissions.txt` is the machine-readable copy: the release workflow diffs the built APK
against it in **both** directions (a missing `RECEIVE_BOOT_COMPLETED` would not break the build, it would
quietly make every alarm die at the next reboot). It compares names only, so `SCHEDULE_EXACT_ALARM`'s
`maxSdkVersion` cap lives solely in the manifest. Change the file and the manifest in the same commit.

The one exported component that other apps can reach is `AlarmIntentActivity` (the AlarmClock intents of
assistants and clock apps). It is protected by `com.android.alarm.permission.SET_ALARM`, as the
AOSP clock does, so an installed app without that permission cannot silence a ringing alarm.

## When Android gets in the way of an alarm

- **Notifications off, or the ring channel blocked or turned below High.** The alarm still sounds and
  vibrates, but Android may show nothing to stop it with. Opening Twinlit then shows the alarm screen with
  Snooze and Dismiss, and the alarm list says so in its red banner, whose button opens the right setting.
  It still stops by itself after ten minutes.
- **Android 12 and 12L with exact alarms switched off.** Switching them off cancels every exact alarm
  without telling the app. Twinlit keeps an inexact backstop of the nearest alarm or snooze, which
  survives that, and sets every alarm again whenever it is opened. An inexact alarm may come late, and
  then rings through a backup notification on its own channel.
- **Snoozes** are remembered on the device, so they survive a restart; one that is more than ten minutes
  late leaves a "Missed" notice instead.
- **Clock changes:** an alarm that has rung is not rung again when the clock is corrected back by up to an
  hour.
- **One alarm at a time:** when a second alarm starts while one rings, the first is reported as missed
  once nothing rings.

## Build it yourself

```sh
cd android
./gradlew --no-configuration-cache :app:assembleDebug
```

The green build, as every change here ends with it, once for each edition:

```sh
./gradlew --max-workers=3 --no-configuration-cache :app:testDebugUnitTest :app:lintRelease :app:assembleDebug
./gradlew --max-workers=3 --no-configuration-cache -PplayBuild=true :app:testDebugUnitTest :app:lintRelease :app:assembleDebug
```

### Toolchain

AGP 9.3.1, Gradle 9.5.0, Kotlin 2.2.10, JDK 21, compileSdk 37.1, minSdk 24, targetSdk 36. Set
`JAVA_HOME` to a JDK 21 and `ANDROID_HOME` to the SDK (or write `sdk.dir` in `local.properties`, which
git ignores). On Debian a plain `java-25` is a JRE with no compiler; install a JDK or point
`JAVA_HOME` at one.

### Core library desugaring

`minSdk` is 24 but `java.time` is API 26+, and every sun, moon and alarm calculation is `java.time`.
Desugaring is therefore load-bearing. The desugar library carries its own copy of the timezone
database: on API 24 and 25 only, a government moving a DST transition can leave the zone rules stale
until `desugarJdkLibs` is bumped and the app is rebuilt.

### The city catalogue

`app/src/main/assets/data/cities.tsv` is not in the repository. It is copied at build time by the
`copyCityData` Gradle task from `../data/cities.tsv`, which is the catalogue SunApp and MoonApp ship,
byte for byte (`CatalogueHashTest` pins its SHA-256). A different catalogue is a deliberate change of
that test, the footer's date line and the "25,007 cities" texts together.

### Icons and fonts

- `tools/build-launcher-icon.py` is the one command that makes every form of the app icon, option b1
  "Sunset, moon above", from the three SVG layers in `tools/launcher-icon/` (the source, with its
  notes): the adaptive background, foreground and monochrome vectors, the carousel card icon, the PNG
  icons for Android 7 (API 24 and 25), which have no adaptive icons, the 512 px store icon and the
  Play feature graphic. Run `python3 tools/build-launcher-icon.py` from `android/`; it needs
  Chromium (`/usr/bin/chromium`, or `CHROMIUM=`) and Pillow. The other icon options are not in this
  repository.
- `tools/build-inter-fonts.py` cuts the four static Inter instances from the variable font. It needs
  `fonttools` and `brotli`, and refuses any source other than the reviewed one.
- 21 of the 22 `labs_switcher_*.xml` drawables of the Stormberry Labs carousel are SunApp's generated files,
  copied byte for byte; the comment in each names a converter that is not part of this repository.
  `labs_switcher_twinlit.xml` is new, generated by `tools/build-launcher-icon.py`.
- `drawable/ic_sun_mark.xml` and `drawable/ic_moon_mark.xml` are the two apps' own logos, copied byte
  for byte (SunApp `2a26f98` and MoonApp `68c7596`, both `ic_app_logo.xml`). They are never redrawn.

### Signing

Release builds are signed from `keystore.properties` (git ignores it) locally, or from
`ORG_GRADLE_PROJECT_RELEASE_*` environment variables in CI. Without either, the release variant builds
unsigned rather than failing. The release key and the separate Play upload key are two PKCS12 keystores
that live outside the repository; their certificates are pinned in `.github/workflows/android-release.yml`
(the release certificate) and in `tools/check-play-bundle.sh` (both). A build signed with any other key fails
the workflow and the bundle check.

### The Play checks

`-PplayBuild=true` changes the application ID, the signing key, the version code, the release guards
and the archive name, and nothing in the code, the manifest or the resources. Two guards sit on the
release tasks, because the failure modes are permanent: `bundleRelease` without the flag fails (that AAB
would claim the sovereign package on Play), and `assembleRelease` with the flag fails (Play takes the
bundle).

```sh
./gradlew --no-configuration-cache -PplayBuild=true :app:processReleaseManifest
tools/check-play-bundle.sh --manifest \
  app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml 0.0.1 1001
```

`tools/check-play-bundle.sh` fails unless the package is `no.stormberry.twinlit.play` with the given
versionName and versionCode, the permissions equal `expected-permissions.txt` (no `INTERNET`,
`SCHEDULE_EXACT_ALARM` capped at API 32), the activities, receivers and the one service are exactly the
app's own, and `AlarmService` is a foreground service of type `systemExempted`. With a bundle instead of
`--manifest` it also checks the dex (the 13 classes the manifest keeps: every activity, the service and
every receiver of the app) and the signature (one signer, the upload certificate, never the release certificate).
`tools/check-play-bundle.sh --dex <apk, aab or a directory of dex files>` runs the dex half alone, for
example on `:app:assembleRelease` (sovereign, unsigned) or on the Play edition's
`:app:minifyReleaseWithR8` output, neither of which needs a key.

### Releasing

Tag `android-vX.Y.Z` and push the tag. The workflow runs in the environment `release`, which holds the
signing secrets, accepts android-v* tags only and waits for one of its reviewers. It runs the unit tests,
lints, builds, verifies the signature against the pinned certificate and the permission allowlist, publishes
a provenance attestation and creates the GitHub release, with the store release note of that version
(`fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`) as its body.

## Known limits

- **Above roughly 80 degrees latitude, timezone-by-nearest-city degrades.** The pick becomes
  longitude-blind. The highest city in the catalogue is Longyearbyen at 78.22 N, so this only affects
  coordinates typed further north.
- **English exonyms are only as good as GeoNames.**
- **A polar day with two moonrises keeps one of them** (the website's rise and set search does the same),
  so an alarm anchored to moonrise can skip that day.

## Credits

The footer credits only what the app's function is built on: [SunCalc](https://github.com/mourner/suncalc)'s
formulae (BSD 2-Clause) and the city data from [GeoNames](https://www.geonames.org/) and
[Kartverket](https://www.kartverket.no/en/api-and-data) (CC BY 4.0). The rest is credited here and in
[NOTICE](../NOTICE), not in the app: the [Inter](https://rsms.me/inter/) typeface (OFL 1.1), the
[Lucide](https://github.com/lucide-icons/lucide) LinkedIn and GitHub icons (ISC), [Kotlin](https://github.com/JetBrains/kotlin)
and the [Jetpack](https://github.com/androidx/androidx) libraries, Compose and Material 3 included
(Apache 2.0), and the [Java time backport](https://github.com/google/desugar_jdk_libs) that gives
Android 7 `java.time` (GPL 2.0 with Classpath Exception).

## Licence

MIT, Stormberry AS.
