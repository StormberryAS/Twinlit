# Changelog

Every released version of Twinlit, newest first. Each release's store note
(`fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`) names the main updates and links
here.

## 0.0.1 (versionCode 1; 1001 on Google Play)

The first release. Twinlit puts sun and moon times and alarms in one app, and it replaces SunApp and
MoonApp. Places and alarms from those apps are not carried over: Android keeps each app's data to
itself, so you add your place and your alarms again.

- **Three tabs: Sun, Moon and Alarms.** The Sun tab is SunApp's screen and the Moon tab is MoonApp's,
  on one shared place: sunrise, solar noon, sunset and day length; the moon's phase, illumination and
  distance, moonrise, transit and moonset; Midnight Sun, Polar Night and the days with no moonrise or
  moonset, said plainly.
- **Appearance:** Sun, Moon, Twilight, Auto or System, from the button at the top. Auto follows the sun's
  daily rhythm at your chosen place (Sun for the ten hours around solar noon, Twilight for two hours on
  each side, Moon for the rest) and needs no location access.
- **Alarms for the sun and the moon in one list:** sunrise, solar noon, sunset, moonrise, moonset, the moon
  at its highest, and the full and new moon nights, each with an offset before or after. Snooze, Missed
  notices, earliest and latest limits, a place per alarm, and the next fire times shown before you save.
  The alarm service is a `systemExempted` foreground service, as in SunApp and MoonApp 2.0.0.
- **Six home screen widgets:** three for the sun and three for the moon, on one see-through dark panel.
- **Nine permissions, all for the alarms**; no internet, no location, no tracking, no Google services.
  Nothing is asked until you create your first alarm.
- **Two editions, one app:** `no.stormberry.twinlit` (Zapstore, GitHub) and `no.stormberry.twinlit.play`
  (Google Play, versionCode 1001). They differ only in the package name, the signing key and the version code.
