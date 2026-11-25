# Twinlit

Sun and moon times, and alarms anchored to them, for anywhere on Earth, computed on the device.
Twinlit puts SunApp, MoonApp and the alarms of both in one app, and replaces them.

An Android app in Kotlin and Jetpack Compose. **No network access, no location permission, no
tracking, no Google services**: the app declares no `INTERNET` permission, which makes the claim
checkable rather than promised.

**Status: version 0.0.1, the first release.** Signed releases of the sovereign edition come from the
tagged workflow of this repository (`.github/workflows/android-release.yml`), which pins the signing
certificate. The Google Play edition is `no.stormberry.twinlit.play`, versionCode 1001. The page is
twinlit.stormberry.as.

## What it does

- **Sun tab:** sunrise, solar noon and sunset, and day length, for any
  date and any place, in the place's own timezone. Midnight Sun and Polar Night are said plainly.
- **Moon tab:** the moon's phase, illumination and distance, moonrise, transit and moonset, and the
  dates of the next new moon night and full moon night. A day with no moonrise or moonset is said plainly.
- **Alarms tab:** one list of alarms for sun and moon events, each with an offset before or after,
  snooze, optional earliest and latest limits, and a place of its own. The editor shows the next fire
  times before you save.
- **Appearance** (the button at the top): Sun, Moon, Twilight, Auto or System. Auto follows the sun's
  daily rhythm at your chosen place: Sun for the ten hours around solar noon, Twilight for two hours on
  each side, Moon for the rest. It needs no location access: the place comes from the city search or from
  coordinates you type.
- **Six home screen widgets:** three for the sun, three for the moon. The large widgets add golden hour
  and the three twilights (sun), and the next new moon, first quarter, full moon and last quarter (moon).
- **City search** over the bundled catalogue of 25,007 cities, accent-insensitive both ways and
  exonym-aware, or typed coordinates (a decimal comma works as well as a point).

## Permissions

Nine, all for the alarms, all install-time, none for the sun and moon times. The full list is
`android/expected-permissions.txt`; each one is explained in one sentence in
[android/README.md](android/README.md#the-nine-one-sentence-each). There is no `INTERNET`, no
`ACCESS_NETWORK_STATE` and no location permission. Nothing is asked until you create your first alarm.

## Editions

| Package | Where | Version code |
|---|---|---|
| `no.stormberry.twinlit` | GitHub Releases, Zapstore (sovereign edition) | 1 |
| `no.stormberry.twinlit.play` | Google Play (build with `-PplayBuild=true`) | 1001 |

The same code and the same manifest. They differ only in the package name, the signing key and the
version code, and they can be installed side by side.

## Build

```sh
cd android
JAVA_HOME=<a JDK 21> ANDROID_HOME=<the Android SDK> ./gradlew --no-configuration-cache \
  :app:testDebugUnitTest :app:lintRelease :app:assembleDebug
# the Play edition: add -PplayBuild=true
```

More in [android/README.md](android/README.md). Repository layout: `android/` is the Gradle root,
`data/cities.tsv` the shared city catalogue (pinned by its SHA-256), `tools/golden/` the generators of
the test corpora, `fastlane/` the store texts.

## Made from SunApp and MoonApp

Twinlit's engines are ports of the two apps' reviewed final trees: SunApp `2a26f98` (sun maths, city
catalogue, place store, alarm engine and runtime, widgets, the Stormberry footer and Labs carousel) and
MoonApp `68c7596` (moon maths, the moon's screen and widgets). The tests compare the ports with the
original code's own output (`tools/golden/README.md`).

## Credits and licences

The footer of the app credits only what its function is built on: [SunCalc](https://github.com/mourner/suncalc)
([BSD 2-Clause](https://github.com/mourner/suncalc/blob/master/LICENSE)) for the sun and moon maths, and
the city data from [GeoNames](https://www.geonames.org/) and
[Kartverket](https://www.kartverket.no/en/api-and-data)
([CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)). Everything else Twinlit is built with is
credited here and in [NOTICE](NOTICE), which keeps the full notices:

| What | Used for | Licence |
|---|---|---|
| [SunCalc](https://github.com/mourner/suncalc) | Sun and moon maths, ported to Kotlin | BSD 2-Clause |
| [GeoNames](https://www.geonames.org/) and [Kartverket](https://www.kartverket.no/en/api-and-data) | The city catalogue | CC BY 4.0 |
| [Inter](https://rsms.me/inter/) | Typeface, four static instances | OFL 1.1 |
| [Lucide](https://github.com/lucide-icons/lucide) | LinkedIn and GitHub icons in the footer | ISC |
| [Kotlin](https://github.com/JetBrains/kotlin) | The programming language | Apache 2.0 |
| [Jetpack](https://github.com/androidx/androidx) (AndroidX, Compose, Material 3) | The app's libraries | Apache 2.0 |
| [Java time backport](https://github.com/google/desugar_jdk_libs) | `java.time` on Android 7, linked not modified | GPL 2.0 with Classpath Exception |

Twinlit's own code is MIT, Stormberry AS (`LICENSE`).

## Disclaimer

Supplied free of charge, **as is**, with no warranty of any kind. Using it creates no client or advisory
relationship with Stormberry AS, and nothing it produces is professional advice.

**Alarms are best effort, not a guarantee.** Android may delay or cancel an alarm when the device is
off, out of battery, in a restricted power mode, or when the manufacturer's battery optimisation stops
the app. Several manufacturers, Samsung and Xiaomi among them, force-stop background apps and silently
cancel pending alarms. **Do not rely on this as your only alarm for anything that matters.**

This is a **functioning prototype**, not a certified instrument and not a professional service. Values
are computed or modelled, not measured. Check anything that matters against an authoritative source
before you act on it. Stormberry AS reimburses no cost or loss arising from use of this application.

Full terms: [DISCLAIMER.md](DISCLAIMER.md).

Built by [Stormberry AS](https://stormberry.as). Proudly powered by sovereign AI agents.
