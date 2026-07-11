# Commute Weather Widget

An Android home-screen widget for the daily bike-vs-drive decision. For a morning and an
evening commute window it surfaces the worst-feeling moment — air temp, a cyclist-specific
feels-like, a comfort category, peak precipitation, and a sky pictograph — each row tinted
by a green/yellow/red bikeability severity. No verdict is rendered; you integrate the rows
yourself. Full design rationale in [commute-weather-widget-spec.md](commute-weather-widget-spec.md).

## How it works

- **Feels-like**: Steadman Apparent Temperature (radiation-inclusive), fed an *effective
  cycling airspeed* — the quadrature of your cruising speed (default 16 mph) and ambient wind.
- **Worst hour**: the hour whose feels-like is furthest from 60 °F, in either direction.
- **Endpoints**: forecasts are fetched for both home and work (one Open-Meteo request) and
  merged conservatively — worse hour, max precip, worse severity.
- **Data**: [Open-Meteo](https://open-meteo.com) forecast API, free, no key.

## Building

```bash
./gradlew :app:testDebugUnitTest   # pure-Kotlin comfort engine tests (golden cases from the spec)
./gradlew :app:assembleDebug       # APK at app/build/outputs/apk/debug/app-debug.apk
```

Requires JDK 17 and an Android SDK (platform 35); point `local.properties` `sdk.dir` at it.

## Layout

- `domain/` — pure comfort engine, no Android imports (build/test this first)
- `data/` — Open-Meteo client + DTOs + repository
- `config/` — user config, JSON in Preferences DataStore
- `widget/` — Glance widget, WorkManager refresh, widget state cache
- `settings/` — Compose settings screen, Geocoder location entry, calibration readout

## Calibration

Two knobs matter (settings screen → "Feels-like calibration"): `solarGainK` (how much
blazing sun adds; default 0.08) and the temperature band edges. The settings screen shows
the worst hour's AT component breakdown (base + humidity − wind + sun − constant) so it's
obvious which to nudge.
