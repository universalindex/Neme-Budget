# Devpost Draft Copy

## User Experience

Neme Budget is designed for fast, low-friction money tracking with privacy built in. The app uses a clean Jetpack Compose interface with four focused tabs (Dashboard, Transactions, Budgets, Settings). Users can process notifications on demand, review parsed transactions with confidence indicators, and correct categories in a single edit sheet. Those corrections become reusable rules, so the system learns user preferences over time.

The onboarding flow makes setup clear in under a minute: privacy-first value proposition, permission guidance, and local model readiness. Across the app, state updates are reactive, so new transactions and budget changes appear immediately without manual refresh.

## Innovation

Most budgeting tools depend on cloud sync or bank APIs. Neme Budget takes a different path: local-first intelligence powered by an on-device LLM pipeline. Bank-style notifications are processed directly on the phone, parsed into structured transactions, and saved into encrypted local storage.

This architecture enables:

- No bank credential sharing with third-party services.
- No required cloud backend for core budgeting features.
- Explainable user control via custom rules and app-level ignore filters.

By combining notification ingestion, local LLM extraction, and encrypted persistence, Neme Budget demonstrates a practical privacy-preserving alternative to traditional personal finance apps.

