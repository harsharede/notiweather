# NotiWeather

An open-source, ad-free, tracker-free weather app for Android that lives in
your status bar instead of asking you to open an app. It shows the current
temperature as its notification icon; your area's name, today's high/low,
and sunrise/sunset in the notification's title/subtitle; and the current
conditions plus the next two hours when you expand it — just icons and
numbers, nothing to scroll through.

## How it stays battery-friendly

Weather is refreshed roughly every 15 minutes in the background, using
[WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager)'s
periodic work (15 minutes is Android's minimum periodic interval, and
WorkManager itself respects Doze/App Standby) rather than a wake-locking
alarm loop or a foreground service. There's no continuous polling and no
proprietary location dependency — it reads whatever location fix Android
already has cached, only asking the radios for a fresh one when nothing
cached is available.

## Features

- Current temperature as the status bar icon (drawn on-device, no bundled
  icon set)
- Place name, today's high/low, and sunrise/sunset in the notification's
  title/subtitle — visible whether it's collapsed or expanded
- Current + next 2 hours (icon + temperature) in the expanded notification
- Weather from [Open-Meteo](https://open-meteo.com) and place names from
  [OpenStreetMap Nominatim](https://nominatim.org) — both free, open, no
  API key, no account
- No ads, no analytics, no third-party SDKs; the app only talks to those
  two services
- No Google Play services dependency — location comes from Android's own
  `LocationManager`
- Survives reboots: if you left updates on, they resume automatically

## Download

**Android:** grab the latest APK from the
[Releases page](https://github.com/harsharede/notiweather/releases/latest) —
every push to `main` rebuilds it. Sideloading it requires
enabling "install from unknown sources" for whichever app you download it
with. It's signed with Gradle's default debug key, which is fine for
sharing/testing but not for the Play Store.

## Project status

Early, working scaffold: location lookup, the Open-Meteo client, the
persistent notification, and the 15-minute background schedule are all
implemented. It hasn't yet been run against a wide range of devices/OEM
skins — see [Contributing](#contributing) if you'd like to help.

## Architecture

```
app/src/main/java/com/harsharede/notiweather/
  MainActivity.kt              Permission requests + on/off toggle
  Scheduler.kt                  Owns the WorkManager periodic request
  WeatherUpdateWorker.kt        CoroutineWorker: location -> weather -> notification
  LocationHelper.kt             Cached-first location lookup via LocationManager
  WeatherApi.kt                  Open-Meteo HTTP client + JSON parsing
  GeocodingApi.kt                Nominatim reverse geocoding (coords -> place name)
  WeatherCode.kt                 WMO weather code -> emoji/description
  NotificationHelper.kt          Builds the notification, draws the temperature
                                 status-bar icon, fills the expanded custom view
  Prefs.kt                       Tiny on/off flag in SharedPreferences
  BootReceiver.kt                 Re-enqueues the periodic work after a reboot
  NotificationDismissReceiver.kt  Re-posts the notification if it gets swiped away
```

## Getting started

1. Install [Android Studio](https://developer.android.com/studio) (or just
   the command-line SDK tools).
2. Open the project, let Gradle sync, and run the `app` configuration on a
   device or emulator — or from the command line:
   ```
   ./gradlew assembleDebug
   ```
3. On first launch, tap **Enable** and grant the location (and, on Android
   13+, notification) permission it asks for.

## Privacy

The app requests approximate/precise location only to send it straight to
Open-Meteo's forecast endpoint and OpenStreetMap's Nominatim reverse-geocoding
endpoint, both over HTTPS — nothing is sent anywhere else, nothing is stored
beyond an on-device on/off flag, and there's no analytics or crash-reporting
SDK.

## Contributing

Issues and PRs welcome. Good first areas to help with:

- A settings screen for units (°C/°F) and refresh interval
- Testing across OEM notification skins (MIUI, One UI, etc.) where custom
  status bar icons can render inconsistently
- A widget as an alternative/companion to the notification
- Geocoded city search as an alternative to device location

## License

MIT — see [LICENSE](LICENSE).

## Attribution

Weather data provided by [Open-Meteo.com](https://open-meteo.com), licensed
under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Place names
from [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors,
via the [Nominatim](https://nominatim.org) API.
