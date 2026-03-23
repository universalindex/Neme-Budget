# Project Changelog (Neme Budget)

This file tracks significant changes and progress for the Neme Budget app. Please update it whenever you complete a major task or make a critical architectural decision.

## 2024-03-22 - Dev 2 (Backend / AI)

### LLM / Backend Updates
*   Successfully downloaded and ran the MLC LLM compiler/tools locally on the laptop to obtain the necessary Android C++ bindings (`mlc4j.aar`).
*   The compiled `.aar` library is prepped and ready for local integration. We have not actually hooked the engine up to the Android UI or pipeline yet (that is the next step).

---

## 2024-03-22 - AI Assistant

### Architectural Decisions
*   **The Final Decision (MLC LLM - Corrected Path!):** Confirmed commitment to **MLC LLM**. Resolved dependency issues by adding MLC LLM's *official custom Maven repository* and specifying correct artifact versions.

### Build / Infrastructure Updates
*   **README.md**: Initial project `README.md` created.
*   **settings.gradle.kts**: Cleaned up unnecessary repository links, and **added MLC LLM's custom Maven repository (`https://repo.mlc.ai/`)**.
*   **app/build.gradle.kts**: Configured dependencies to use `org.apache.tvm:tvm4j-core:0.1.0-alpha0` and `ai.mlc:mlc-llm-android:0.1.0-alpha0`.

### LLM / Backend Updates
*   **LlmPipeline.kt**: Created initial `LlmPipeline` class for LLM integration (placeholder `simulateLlmInference` and `processAndVerify` methods).

### Frontend / UI Updates
*   **MainActivity.kt**: Updated to include `LlmTestingScreen` for manual LLM input and verification display.

---

## 2026-03-23 - AI Assistant

### Repository Hygiene Updates
* Hardened `.gitignore` with a clean Android-focused baseline and full `.idea/` ignore policy to prevent IDE-specific environment files from being committed.
* Removed already-tracked `.idea` files from git index using `git rm -r --cached .idea` so they remain local but stop affecting teammates.
* Rationale: reduce machine-specific config churn and avoid unintended IDE environment drift across collaborators.
