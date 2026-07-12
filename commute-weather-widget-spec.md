# Commute Weather Widget — Implementation Spec

An Android home‑screen widget that supports a daily bike‑vs‑drive decision. It reads the
weather forecast for a **morning window** and an **evening window** and, for each, surfaces
the worst‑feeling moment: air temp, a cyclist‑specific "feels like," a comfort category
(too cold → gloves → jacket → ideal → shorts → too hot), precipitation, and a sky pictograph.
Each row is tinted by an at‑a‑glance "bikeability" gradient.

**This is decision support, not an oracle.** There is deliberately no BIKE/DRIVE verdict
output. The user integrates the two rows with everything the widget can't know (schedule,
mood, school drop‑off, evening plans, whether the bike can sleep at the office). The widget
replaces the ritual of opening a weather app and scrubbing the hourly forecast.

The two windows are **fully independent**. There is no round‑trip coupling logic.

---

## 1. Settled design decisions (quick reference)

| Decision | Choice |
|---|---|
| Feels‑like model | Steadman **Apparent Temperature**, radiation‑inclusive form |
| Feels‑like inputs | air temp, humidity (vapour pressure), wind, sun (shortwave radiation) |
| Wind used | **effective cycling airspeed** = quadrature of self‑speed and ambient wind |
| Self‑speed (bike cruising) | default **16 mph**, configurable |
| Head/tail wind | ignored — quadrature already captures urban quartering |
| Sun | shortwave radiation → tunable solar‑gain term; cloud captured implicitly |
| "Worst" hour | hour whose feels‑like is **furthest from the ideal balance point** (default 60 °F, configurable), either direction |
| Categories | driven by **feels‑like**, not air temp; thresholds are **gear decision lines** |
| Precip data | window **peak probability** + **peak rate**, shown under the pictograph |
| Precip → yellow | probability ≥ 20% |
| Precip → red | rate ≥ gate (drizzle vs. true light‑rain line), default 0.3 mm/h |
| Row color | single "bikeability" tint = **max severity** of {temp category, precip} |
| Severity ladder | green: ideal, jacket · yellow: gloves, shorts · red: too‑cold, too‑hot |
| Endpoints | query weather at **both** home and work; take the **worse** per window |
| Data source | **Open‑Meteo** (free, no API key, **15‑minutely** `minutely_15`, native apparent‑temp components) |
| Stack | Kotlin · Jetpack **Glance** widget · **WorkManager** refresh · **DataStore** config |

---

## 2. The comfort engine (the heart of the app)

This is a set of **pure functions** with no Android dependencies. Build and unit‑test it
first, in isolation. Everything computes in **metric** (°C, m/s, hPa, W/m²) and converts to
imperial only for display.

### 2.1 Effective cycling wind

You generate your own airflow by moving. On calm days that self‑speed dominates; on windy days
the ambient wind combines with it. Because urban routing constantly changes your heading
relative to the wind, a scalar quadrature combination models the felt airspeed better than any
head/tail projection — and it happens to match the intuition that a 20 mph tailwind still
"feels like ~25 quartering" on cross streets.

```
self_ms     = self_speed_mph * 0.44704          # default 16 mph → 7.152 m/s
ambient_ms  = wind_speed_10m                     # from forecast, m/s
effective_ws = sqrt(self_ms^2 + ambient_ms^2)    # default combine mode
```

Properties: calm day → exactly self‑speed; any wind → strictly greater; light winds barely
move it (8 mph ambient at 16 mph self → 17.9 mph effective); quadrature adds sublinearly, i.e.
the "significant dampening" we wanted, for free.

Config knob `windCombine`: `"worst"` (default) evaluates AT under both quadrature and
`max(self_ms, ambient_ms)` and keeps whichever lands further from the ideal pivot — full wind
chill when cold, least wind relief when hot. `"quadrature"` and `"max"` force a single mode.

### 2.2 Vapour pressure (humidity term)

```
e = (RH / 100) * 6.105 * exp( 17.27 * Ta / (237.7 + Ta) )   # hPa, Ta in °C
```

### 2.3 Steadman Apparent Temperature (radiation‑inclusive)

```
AT = Ta
   + 0.348 * e
   - 0.70  * ws
   + 0.70  * ( Q / (ws + 10) )
   - 4.25
```

- `Ta` — air temperature (°C)
- `e`  — vapour pressure (hPa), from §2.2
- `ws` — **effective_ws** from §2.1 (m/s). It appears in **both** the convective‑cooling term
  (`-0.70*ws`) and the solar‑damping denominator (`Q/(ws+10)`). This is the elegant part: the
  same "you're moving at 16+ mph" airspeed both increases wind cooling and reduces felt sun.
- `Q`  — net solar radiation absorbed by the body (W/m²), from §2.4

`AT` is in °C. Convert for display: `F = C * 9/5 + 32`.

This one continuous formula covers the whole temperature range. Do **not** blend NWS Heat Index
and Wind Chill — those are undefined between 50–80 °F, which is exactly the ideal/jacket/shorts
band, and would ignore humidity and wind right where it matters most.

### 2.4 Solar gain (sun vs. cloud)

Steadman's `Q` is *net radiation absorbed by a person*, which is roughly an order of magnitude
smaller than the raw shortwave irradiance Open‑Meteo reports (noon summer sun ≈ 800–1000 W/m²).
Feeding raw shortwave straight in would produce an absurd +30–60 °F. Map it through a single
tunable constant:

```
Q = solarGainK * shortwave_radiation      # shortwave in W/m²
```

- **Default `solarGainK = 0.08`.** With this, full noon sun (~900 W/m²) at 16 mph adds roughly
  +3–4 °F over the shade value — a noticeable but not dramatic bump for a moving cyclist.
- Cloud cover is captured **implicitly**: overcast → low shortwave → small `Q`. No separate
  cloud term, no double counting, and it stays consistent with the pictograph.
- At night shortwave is 0, so `Q` = 0 and `AT` collapses to the shade value. (We ignore
  nighttime clear‑sky radiative *cooling* — real, but negligible for daylight commute windows.)
- `solarGainK` is **the** primary calibration knob (see §5.2).

### 2.5 Per‑bucket → window aggregation

For each window, gather every **15‑minute** forecast bucket that **overlaps** the clock window
(no interpolation). A sub‑hour commute window gets 3–5 real samples instead of 1–2. For each bucket compute `AT` per above. Then aggregate:

**Worst hour** — the decision‑relevant temperature:
```
worst_hour = argmax over buckets of | AT_hour - ideal |    # ideal balance point, default 60°F
```
Report that hour's **air temp**, its **AT** (feels‑like), its **category**, and its
**timestamp** (surfacing *when* the worst moment hits lets the user consider leaving earlier).
The ideal pivot means: warmer‑than‑ideal picks the hot extreme, colder picks the cold extreme,
and a window that sits entirely in the good band (e.g. 58–62) yields a trivially small distance
where any pick is fine — exactly the intended "it's all great, don't sweat it" behavior.

**Peak precipitation** — over the same buckets:
```
peak_prob = max(precipitation_probability)   # %
peak_rate = max(precipitation)               # mm (hourly)
```

**Pictograph** — computed independently of the worst‑temp hour (you care whether it rains *at
all* in the window, not only at the coldest minute):
```
if peak_rate >= redRateMmHr:      RAIN
else derive from mean cloud_cover over the window:
    < 25%   → SUNNY
    25–60%  → PARTLY_CLOUDY
    > 60%   → CLOUDY
```
(Optional later: a DRIZZLE icon for `0 < peak_rate < gate`, and a SNOW icon when precip
coincides with air temp near/below freezing.)

### 2.6 Category (from feels‑like)

Thresholds are **gear decision lines** (all configurable): `jacket` is the temperature below
which a jacket is needed, `shorts` the temperature at/above which you ride in shorts and
change at work, and so on. Between `jacket` and `shorts` no special gear is needed — that's
the `ideal` band. Separately, `ideal` (default 60 °F) is the **balance point** the worst‑hour
search measures distance from; it is a pivot, not a band edge, and should sit inside the
jacket–shorts band.

| Category | Feels‑like range (°F) | Meaning |
|---|---|---|
| too_cold | < 35 | don't ride |
| gloves | 35 – 45 | gloves needed |
| jacket | 45 – 55 | jacket needed |
| ideal | 55 – 68 | no special gear |
| shorts | 68 – 82 | shorts; change into pants at work |
| too_hot | ≥ 82 | too hot |

### 2.7 Severity and row color

```
temp_severity:
    green  → ideal, jacket
    yellow → gloves, shorts
    red    → too_cold, too_hot

precip_severity:
    red    if peak_rate >= redRateMmHr           # default 0.3 mm/h
    yellow if peak_prob >= yellowProbPct          # default 20%
    green  otherwise

row_severity = max(temp_severity, precip_severity)   # red > yellow > green
```

`row_severity` drives the row's bikeability gradient tint. The category **label** and the
precip **numbers** carry the *why* (e.g. an ideal‑temp row can still be red — "Ideal · rain
90%"). One glanceable axis: redder = worse for biking.

### 2.8 Both‑endpoints merge

Compute a full window result at **both** the home lat/lon and the work lat/lon, then merge
**field‑wise, conservatively**, per window:

```
merged.worst_hour   = the endpoint hour with the larger |AT - 60|
merged.peak_prob    = max(home.peak_prob, work.peak_prob)
merged.peak_rate    = max(home.peak_rate, work.peak_rate)
merged.picto        = recompute from the merged precip + worse cloud
merged.row_severity = max(home.row_severity, work.row_severity)
```

This catches the downtown urban‑heat‑island bump and a frost‑pocket start street without any
routing logic.

### 2.9 Two golden test cases

Seed the engine's unit tests with these (metric intermediates shown so each step is checkable).
`solarGainK = 0.08`, `self_speed = 16 mph = 7.152 m/s`, quadrature combine.

**Warm, humid, sunny, light wind**
```
Inputs:  Ta = 24 °C (75.2 °F), RH = 60%, ambient = 3 m/s, shortwave = 700 W/m²
effective_ws = sqrt(7.152^2 + 3^2)          = 7.756 m/s
e            = 0.6*6.105*exp(17.27*24/261.7) = 17.85 hPa
Q            = 0.08 * 700                    = 56 W/m²
AT = 24 + 0.348*17.85 - 0.70*7.756 + 0.70*(56/17.756) - 4.25
   = 24 + 6.212 - 5.429 + 2.208 - 4.25      = 22.74 °C ≈ 72.9 °F  → category: shorts
(shade check, shortwave = 0):               ≈ 20.53 °C ≈ 69.0 °F  → sun adds ~4 °F)
```

**Cold, breezy winter morning**
```
Inputs:  Ta = 3 °C (37.4 °F), RH = 70%, ambient = 5 m/s, shortwave = 100 W/m²
effective_ws = sqrt(7.152^2 + 5^2)          = 8.727 m/s
e            = 0.7*6.105*exp(17.27*3/240.7)  = 5.30 hPa
Q            = 0.08 * 100                    = 8 W/m²
AT = 3 + 0.348*5.30 - 0.70*8.727 + 0.70*(8/18.727) - 4.25
   = 3 + 1.844 - 6.109 + 0.299 - 4.25        = -5.22 °C ≈ 22.6 °F → category: too_cold
```
The second case shows the wind floor biting: 37 °F air feels like ~23 °F once you add
self‑generated 16 mph airflow.

---

## 3. Data source — Open‑Meteo

Free, no API key, **15‑minutely** resolution (`minutely_15` — native model resolution in North
America/Central Europe, gracefully interpolated from hourly elsewhere and at the far horizon),
and it natively returns every feels‑like component. Note `precipitation` is mm **per 15‑minute
bucket** — convert ×4 to mm/h before gating.

- **Endpoint:** `https://api.open-meteo.com/v1/forecast`
- **Multi‑point in one request:** pass comma‑separated coordinates —
  `latitude=<home>,<work>&longitude=<home>,<work>` — and the response is an array, one block
  per point. One request covers both endpoints.
- **Fields (`minutely_15=`):** `temperature_2m`, `relative_humidity_2m`, `precipitation`,
  `precipitation_probability`, `cloud_cover`, `wind_speed_10m`, `shortwave_radiation`.
  Also pull `apparent_temperature` — not used in the decision (it won't respect our wind floor)
  but handy as a sanity reference during calibration.
- **Units:** `temperature_unit=celsius`, `wind_speed_unit=ms`, `precipitation_unit=mm`,
  `timezone=auto`. Compute in metric; convert for display.
- **Forecast span:** request enough days to always cover today's evening window even when the
  widget refreshes early morning (`forecast_days=2` is safe).

> The coding agent should verify exact parameter names against the current Open‑Meteo docs
> before wiring the client — the names above reflect the stable v1 forecast API but should be
> confirmed. Cache the last successful response so the widget can render (marked stale) offline.

Location entry in settings: address field → Android `Geocoder` → store `lat/lon` + a label.
(Or a map‑pick; lat/lon is the stored form either way.)

---

## 4. Config

Stored via **DataStore**, simplest as a single JSON‑serialized object (kotlinx.serialization)
in Preferences DataStore. Edited through the settings screen (§6.4).

```jsonc
{
  "home": { "lat": 38.889, "lon": -76.995, "label": "Home" },
  "work": { "lat": 38.895, "lon": -77.036, "label": "Work" },
  "windows": {
    "morning": { "start": "07:15", "end": "08:15" },
    "evening": { "start": "17:00", "end": "18:00" }
  },
  "bike": {
    "selfSpeedMph": 16.0,
    "windCombine": "worst"             // or "quadrature" / "max"
  },
  "feelsLike": {
    "solarGainK": 0.08
  },
  "thresholds": {
    // gear decision lines in °F; "ideal" is the worst-hour balance point (a pivot, not a band edge)
    "tempF": { "tooCold": 35, "gloves": 45, "jacket": 55, "ideal": 60, "shorts": 68, "tooHot": 82 },
    "precip": { "yellowProbPct": 20, "redRateMmHr": 0.3 }
  },
  "refresh": { "intervalMinutes": 60 }
}
```

---

## 5. Config & calibration story

### 5.1 What's fixed vs. tunable
Fixed by design: the Steadman formula, the quadrature wind model, the severity ladder.
Everything in §4 is user‑tunable, including the ideal balance point (default 60 °F).

### 5.2 The two knobs that need real‑world calibration
1. **`solarGainK`** — how much blazing sun adds. Ride a few sunny days, compare the widget's
   feels‑like to how it actually felt; nudge up if sun feels underweighted, down if the widget
   runs hot on clear days.
2. **Temperature band edges** — personal comfort. Shift `tempF` bounds until "gloves,"
   "jacket," etc. match when *you* actually reach for each.

Both are fast to converge if the app exposes a **calibration/debug readout**: for the worst
hour, show the AT component breakdown — base temp, `+humidity`, `−wind`, `+solar`, giving the
final AT — plus the raw inputs. That makes it obvious which term to nudge. Worth building as a
long‑press or a debug toggle on the settings screen.

---

## 6. Architecture

### 6.1 Stack
- **Kotlin**, single app module (organize by package; split into Gradle modules only if it
  grows).
- **Jetpack Glance** — home‑screen widget UI.
- **WorkManager** — periodic background refresh.
- **DataStore** — config persistence.
- **Retrofit** or **Ktor** + kotlinx.serialization — Open‑Meteo client.
- **Jetpack Compose** — settings Activity.

### 6.2 Packages
- `domain/` — the pure comfort engine from §2. No Android imports. Fully unit‑tested (§2.9).
- `data/` — Open‑Meteo DTOs, API client, repository (fetch both points, cache last response).
- `config/` — config model + DataStore read/write.
- `widget/` — Glance widget, layout, rendering, the WorkManager worker.
- `settings/` — Compose settings UI.

### 6.3 Data flow
```
WorkManager worker (periodic + on config change)
  → Repository.fetch(home, work)        // one Open-Meteo request, two points
  → parse hourly arrays
  → for window in {morning, evening}:
        for endpoint in {home, work}:
            per-hour AT  → aggregate (worst hour, peak precip, picto, severity)   [§2.5–2.7]
        merge endpoints field-wise                                                [§2.8]
  → write two WindowResults into widget state
  → GlanceAppWidget.update()
```

### 6.4 Android specifics & gotchas
- **Glance gradients are limited.** Per‑row gradient backgrounds are not first‑class in Glance.
  Two workable approaches: (a) pre‑render a small gradient **drawable per severity** (green /
  yellow / red) and set it as the row's `background(ImageProvider(...))`; or (b) fall back to
  a solid tinted background with alpha if gradients prove painful. Decide early — it shapes the
  UI layer. Icons must be **drawables** (`ImageProvider`), not Compose vector painters.
- **Refresh:** WorkManager periodic at `refresh.intervalMinutes` (min practical period 15 min),
  plus an expedited one‑off on config change, plus honoring the system's widget update request.
  Don't hammer the network — a few refreshes across the morning is plenty.
- **Widget states:** loading, live, **stale** (show last cached result with a subtle
  timestamp/indicator when the latest fetch failed), and error (no cache yet). Tapping the
  widget opens settings.

---

## 7. UI spec

Two stacked rows — morning and evening — plus a slim header (date + last‑updated). Layout
mirrors the reference sketch: window label at left, pictograph with the precip numbers beneath
it, the worst‑hour air temp large, feels‑like secondary, category label, and the whole row
tinted by `row_severity`.

```
┌───────────────────────────────────────────────┐
│  Fri Jul 11              updated 6:52 AM        │
├───────────────────────────────────────────────┤
│  MORNING          ☀        58°   feels 61°      │   ← row tint = severity
│  7:15–8:15      0.0mm       Ideal               │
│                  0%      worst @ 7:15            │
├───────────────────────────────────────────────┤
│  EVENING          🌧        79°   feels 84°      │
│  5:00–6:00      1.2mm      Too hot              │
│                 90%      worst @ 5:00            │
└───────────────────────────────────────────────┘
```

Per‑row anatomy:
- **Time window** label (`MORNING` / `EVENING` + the clock range).
- **Pictograph** (sun / partly / cloudy / rain), with **peak rate** (mm) and **peak prob** (%)
  stacked under it.
- **Air temp** (large) and **feels‑like** (secondary) for the worst hour.
- **Category** label.
- **Background** — bikeability gradient tint from `row_severity` (green/yellow/red family).

> A "worst @ HH:MM" timestamp was displayed originally but removed: with hourly forecast
> buckets and a sub‑hour commute window it degenerates to always naming the window start.
> The worst hour's timestamp still appears in the settings calibration readout.

Keep it legible at a glance from a home screen; the numbers and label do the explaining, the
color does the alerting.

---

## 8. Suggested build phases

1. **Domain engine** — Steadman + quadrature wind + solar + category + severity + aggregation,
   as pure Kotlin. Wire up the two golden tests (§2.9) plus edge cases (empty window, all‑ideal
   window, precip gate boundaries). Ship nothing else until this is green.
2. **Data layer** — Open‑Meteo client, two‑point fetch, parse, feed the engine, log results.
3. **Widget** — Glance layout with static data, then live data via a WorkManager worker.
4. **Settings** — Compose config screen + DataStore; locations via Geocoder.
5. **Styling** — severity gradients + pictograph drawables + polish.
6. **Calibration mode** — component‑breakdown debug readout; tune `solarGainK` and band edges.

---

## 9. Deliberately out of scope (candidate v1.1+)

- **Round‑trip / BIKE‑vs‑DRIVE verdict** — intentionally omitted; human integrates the rows.
- **Wind gusts as a ridability deterrent** — gusts are fetched‑able (`wind_gusts_10m`); could
  add a gust threshold that pushes severity toward yellow/red independent of chill.
- **Drizzle and snow pictographs** — a light‑rain icon below the gate; a snow icon when precip
  meets near‑freezing air temp.
- **Asymmetric comfort** — the 60 °F pivot is symmetric; cyclists often tolerate warm better
  than cold, so a one‑line asymmetric weighting on `|AT − 60|` could be added.
- **Nighttime radiative cooling** — ignored; negligible for daylight commute windows.
