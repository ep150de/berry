# Berry 🫐

A free, open-source baby tracker for Android — in the spirit of Huckleberry, but
fully local-first: **no accounts, no cloud, no analytics, no network permission at all.**
Everything lives in a Room database on the device.

> Screenshots: capture them from the running app and drop them in `docs/` —
> no placeholders are committed.

## Features

- **Dashboard** — today's totals (sleep, feeds, diapers), live running timers with
  stop buttons, latest events, and quick navigation.
- **Sleep** — start/stop timers for naps and night sleep, log past sessions manually,
  edit or delete any entry.
- **Feeding** — nursing timers (left/right breast) and bottle logs with amount in
  ml or oz, edit or delete any entry.
- **Diaper** — one-tap quick log (wet / dirty / mixed), edit time or add a note.
- **History** — one chronological list of everything, filterable by type, with
  edit/delete on every row.
- **Stats** — last-7-days totals and a simple bar chart (sleep hours, feeds, diapers).
- **Multiple children** — child profiles with photo, name, and birthdate; a
  top-bar switcher; every child keeps their own logs, history, and stats.
  Great for twins.
- **Personalized home** — the header shows the selected child's photo, name,
  and live age; the dashboard greets you by time of day. Full dark mode for
  3 AM logging.
- **Smart nursing** — suggests which side to start on next ("other side next")
  based on the last session.
- **Next-up predictions** — learns median feed/diaper intervals and wake windows
  entirely on-device and shows a "Feeding usually due in ~20 min" banner on
  the dashboard. No account, no server, no paywall.
- **Ask Berry (v3)** — an on-device AI assistant: a small language model
  (LFM3-1.7B, ~1.1 GB, downloaded once over Wi-Fi) answers short questions
  about milestones and your child's learned patterns. The model only ever
  sees summarized pattern facts, never raw logs — and nothing leaves the
  phone. Needs a phone with 3 GB+ RAM and the native engine library
  (see "Ask Berry (v3)" below); without either, the screen says so plainly
  instead of pretending to work.

## Tech

- Kotlin, Jetpack Compose (Material 3), Navigation Compose
- Room (KSP) for persistence, `StateFlow` + `collectAsStateWithLifecycle()` for UI state
- minSdk 26 · targetSdk 34 · Java 17

## Open in Android Studio and build a debug APK

1. Install [Android Studio](https://developer.android.com/studio) (Hedgehog or newer)
   with JDK 17. Open it once so the Android SDK (API 34) and build tools install.
2. **File → Open** and select the `baby-tracker` folder. Android Studio will sync
   Gradle automatically (first sync downloads dependencies — needs internet).
3. If the Gradle wrapper jar is missing, Android Studio will offer to generate it,
   or run `gradle wrapper` from a local Gradle install.
4. Connect an Android device (USB debugging on) or start an emulator (API 26+).
5. Press **Run ▶** — this builds and installs the debug APK.
6. For a standalone APK file: **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
   Find it at `app/build/outputs/apk/debug/app-debug.apk` and share/install it directly.

## Ask Berry (v3) — on-device AI

- **Model:** Liquid AI LFM3-1.7B, GGUF Q4_K_M (~1.1 GB), repo
  `LiquidAI/LFM3-1.7B-GGUF` on Hugging Face. It is **not** in this repo or
  the APK: the app downloads it once, over Wi-Fi, into app-private storage
  (`ai/ModelManager.kt`). Fallback with a one-line change in
  `ai/AiConfig.kt`: point it at a Qwen3-1.7B GGUF Q4_K_M instead.
- **Engine:** llama.cpp via JNI (`ai/InferenceService.kt`). Build
  `libberry_llama.so` from llama.cpp v0.8.0+ (LFM3 support required) and
  place it in `app/src/main/jniLibs/arm64-v8a/` — full steps and the exact
  JNI signatures are in `app/src/main/cpp/README.md`. Without the `.so`,
  the app still builds and runs; Ask Berry reports the engine as missing.
- **What the model sees:** only v2 PatternEngine aggregates (median
  intervals, confidence tiers, next-due times), today's totals, and the
  bundled month-by-month milestone data (`ai/Milestones.kt`). Raw log
  entries never reach the model, and prompts stay under ~1K tokens with
  answers capped at 200 tokens.
- **Permissions:** v3 adds `INTERNET` + `ACCESS_NETWORK_STATE`, used only
  for the one-time model download. There is no server call in the answer
  path, no analytics, and no account.
- **Limits:** phones need 3 GB+ total RAM (checked at runtime); answers
  are general information, not medical advice — the UI and the prompt
  both say to contact a pediatrician for health concerns.

## Project layout

```
app/src/main/java/com/alexanderlingle/berry/
├── MainActivity.kt            # single-activity host
├── BerryApp.kt                # Application: owns the Room database + repository
├── data/                      # Room entities, DAOs, database, repository
│   ├── Entries.kt             # Child, SleepEntry, FeedingEntry, DiaperEntry
│   ├── Converters.kt          # enum <-> String type converters
│   ├── Daos.kt                # child-scoped queries
│   ├── ChildStore.kt          # selected child id (DataStore)
│   ├── BerryDatabase.kt       # v2 (+ migration 1->2 in BerryApp)
│   ├── BerryRepository.kt     # child-aware data access
│   └── PatternEngine.kt       # v2: median intervals, wake windows, predictions
├── ai/                        # v3: on-device assistant (model NOT bundled)
│   ├── AiConfig.kt            # model repo/file/URL, sampling, RAM floor, fallback
│   ├── Milestones.kt          # deterministic month-by-month milestone data
│   ├── PromptBuilder.kt       # pattern facts + age -> short structured prompt
│   ├── InferenceService.kt    # backend abstraction + llama.cpp JNI bridge
│   └── ModelManager.kt        # Wi-Fi download, verification, RAM gate
├── cpp/
│   └── README.md              # how to build libberry_llama.so (no binary here)
├── notify/                    # Phase 3: WorkManager prediction checks, notifier,
│                              # action receiver, dedupe prefs
├── util/
│   └── TimeUtils.kt           # date/time math + formatting (device timezone)
├── ui/
│   ├── theme/                 # Material 3 color schemes (light/dark), typography
│   ├── navigation/            # NavGraph + bottom bar destinations
│   ├── components/            # shared: dialogs, pickers, labels, ViewModel factory
│   └── screens/               # Dashboard, Sleep, Feeding, Diaper, History, Stats, Children, AskBerry
└── viewmodel/                 # one ViewModel per screen, StateFlow UI state
```

## Privacy

The manifest declares `POST_NOTIFICATIONS`, used only for the opt-in
predictive reminders (asked once at runtime on Android 13+; the app works
fully without it). v3 adds `INTERNET` + `ACCESS_NETWORK_STATE`, used only
for the one-time AI model download — there is no networking code in the
answer path, no analytics SDK, and no account system. Uninstalling the app
deletes the data; backups follow the device's normal app-backup behavior
(`allowBackup="true"`).

## Roadmap ideas

- Nap-need / wake-window hints (Huckleberry's SweetSpot-style)
- Multiple children profiles
- Growth charts (WHO percentiles)
- Optional encrypted export / import

## License

MIT — see [LICENSE](LICENSE).
