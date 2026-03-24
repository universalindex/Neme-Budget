# AGENTS.md

## Project Snapshot
- Android app in `app/` using Kotlin + Jetpack Compose; single module (`:app`) in `settings.gradle.kts`.
- Current app state is a prototype "LLM lab" within a multi-tab navigation shell (`Dashboard`, `Transactions`, `Budgets`, `Settings`).
- Product intent and privacy constraints are defined in `README.md` (local-only processing, no cloud backend).

## Current Architecture (What Exists Today)
- Entry point: `MainActivity` uses `NavHost` for a four-tab bottom navigation shell.
- The LLM Lab is accessible via `Settings -> Open LLM Lab`.
- UI -> pipeline flow in the lab:
  1) user types notification text,
  2) `simulateLlmInference(...)` returns JSON string via MLC streaming API,
  3) `processAndVerify(...)` parses and verifies merchant/amount against raw text.
- Core logic is in `app/src/main/java/com/example/nemebudget/llm/LlmPipeline.kt`.
- `LlmPipeline` uses `MLCEngine` from `mlc4j.aar`, implementing a persistent engine model to avoid reload lag.
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
- Existing tests are scaffolds only.
- Runtime debugging currently relies on logs from `LlmPipeline` tag `"LlmPipeline"`.

## Repo-Specific Conventions and Gotchas
- App package/namespace is `com.example.nemebudget`.
- `minSdk` is 24 in `app/build.gradle.kts` (plans mention 26; code wins).
- Local model integration uses `mlc4j.aar` in `app/libs`.
- JSON handling in active code uses `org.json.JSONObject`; Gson is declared but not used by `LlmPipeline`.
- Keep this project local-first: avoid adding cloud API assumptions unless explicitly requested.

## Integration Notes for AI Agents
- If you extend pipeline behavior, keep `ExtractedTransaction` as UI contract touchpoint.
- Prefer incremental PR-style changes: preserve `LlmTestingScreen` flow while adding new services/repositories.
- Before wiring roadmap features (notification listener, Room, SQLCipher), add manifest + Gradle changes in isolated commits.
- **After every change made, update `CHANGELOG.md` to record the modification and its rationale.**
