# AGENTS.md

## Project Snapshot
- Android app in `app/` using Kotlin + Jetpack Compose; single module (`:app`) in `settings.gradle.kts`.
- Current app state is a prototype "LLM lab", not full budget product yet (`app/src/main/java/com/example/nemebudget/MainActivity.kt`).
- Product intent and privacy constraints are defined in `README.md` (local-only processing, no cloud backend).

## Current Architecture (What Exists Today)
- Entry point: `MainActivity` renders `LlmTestingScreen` only; no navigation graph yet.
- UI -> pipeline flow is direct in one screen:
  1) user types notification text,
  2) `simulateLlmInference(...)` returns JSON string,
  3) `processAndVerify(...)` parses and verifies merchant/amount against raw text.
- Core logic is in `app/src/main/java/com/example/nemebudget/llm/LlmPipeline.kt`.
- Device backend selection is heuristic (`getBestLlmDevice()` -> `"vulkan"` or `"cpu"`) and displayed in UI.
- `AndroidManifest.xml` currently registers only `MainActivity` (no `NotificationListenerService` yet).

## Planned-but-Not-Implemented Boundaries
- `DEV1_FRONTEND_PLAN.md` and `DEV2_BACKEND_AI_PLAN.md` describe future split:
  - frontend via shared `AppRepository` contracts,
  - backend pipeline with notification listener, Room/SQLCipher, and settings store.
- Treat these files as roadmap context, not source-of-truth for current runtime behavior.

## Build/Test/Debug Workflows
- Use Gradle wrapper from repo root (Windows):
  - `./gradlew.bat :app:assembleDebug`
  - `./gradlew.bat :app:testDebugUnitTest`
  - `./gradlew.bat :app:connectedDebugAndroidTest` (device/emulator required)
- Existing tests are scaffolds only:
  - unit: `app/src/test/java/com/example/nemebudget/ExampleUnitTest.kt`
  - instrumented: `app/src/androidTest/java/com/example/nemebudget/ExampleInstrumentedTest.kt`
- Runtime debugging currently relies on logs from `LlmPipeline` tag `"LlmPipeline"`.

## Repo-Specific Conventions and Gotchas
- App package/namespace is `com.example.nemebudget` (do not introduce `com.zerocloudbudget` from plans).
- `minSdk` is 24 in `app/build.gradle.kts` (plans mention 26; code wins).
- Local model integration is expected via manual `.aar`/`.jar` drop into `app/libs` (`fileTree(...)` dependency).
- JSON handling in active code uses `org.json.JSONObject`; Gson is declared but not used by `LlmPipeline`.
- Keep this project local-first: avoid adding cloud API assumptions unless explicitly requested.

## Integration Notes for AI Agents
- If you extend pipeline behavior, keep `ExtractedTransaction` as UI contract touchpoint (`merchant`, `amount`, `category`, verification fields).
- Prefer incremental PR-style changes: first preserve `LlmTestingScreen` flow, then add new services/repositories behind interfaces.
- Before wiring roadmap features (notification listener, Room, SQLCipher), add manifest + Gradle changes in isolated commits to reduce breakage.
- Cross-check dependency/repository claims in `CHANGELOG.md` against actual Gradle files before relying on them.
- **After every change made, update `CHANGELOG.md` to record the modification and its rationale.**
