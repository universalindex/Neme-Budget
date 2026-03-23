# 📱 Zero-Cloud AI Budgeter

## Project Overview
"Zero-Cloud AI Budgeter" is a privacy-first, local-only budgeting application designed for Android. It uniquely leverages on-device Small Language Models (SLMs) to process incoming bank push notifications, extract transaction data, and update a local, encrypted database in real-time. Our core philosophy is absolute privacy: no cloud servers, no API fees, and no third-party financial integrations like Plaid. The app offers a sleek, modern, dark-mode-first user interface inspired by premium budgeting tools like Copilot Money or Monarch Money.

## Features

### 1. Onboarding Flow
- **Privacy Pitch**: Introduces the "100% Local Privacy" concept.
- **Permission Gate**: Guides users to grant `BIND_NOTIFICATION_LISTENER_SERVICE` permission.
- **"Brain Download"**: A progress screen for downloading the local SLM model (e.g., Gemma 2B) to the device.
- **Quick Config**: Initial input for the user's primary bank to aid AI context.

### 2. Dashboard (Home Screen)
- **"Safe to Spend"**: A prominent metric displaying remaining disposable income.
- **Visual Charts**: Interactive donut or bar charts for spending by category.
- **Recent Activity Widget**: A summary of the latest 3-5 AI-processed transactions (Merchant, Amount, Category).

### 3. Transactions Tab (The Ledger)
- **Chronological List**: A scrolling, date-grouped list of all transactions.
- **AI Confidence Indicators**: Visual cues (e.g., sparkle icon ✨) for AI-categorized transactions.
- **Human-in-the-Loop (Edit Mode)**: Allows users to correct AI errors via a bottom sheet, ideally saving custom rules for future AI learning.

### 4. Budgets Tab
- **Category Buckets**: Progress bars showing spending against defined limits for various categories.
- **Visual Warnings**: Dynamic color changes (green, yellow, red) as spending approaches limits.

### 5. Settings & AI Control Room
- **Notification Filters**: Toggles to exclude notifications from specific apps (e.g., Venmo, CashApp).
- **AI Hints**: A text field for providing context rules (e.g., "Always categorize 'Chevron' as Gas").
- **Data Management**: Options to export the local SQLite database to CSV or wipe it entirely.

## Technical Architecture
- **Frontend**: Kotlin / Jetpack Compose (Android Native)
- **Backend/Logic**: Entirely local to the device.
- **Data Scraper**: Native Android `NotificationListenerService`.
- **AI Engine**: MediaPipe LLM Inference API or MLC LLM for on-device inference using a ~2B parameter model (e.g., Gemma 2B or Llama 3.2 1B). Leverages the phone's NPU/GPU if available.
- **Database**: Local SQLite database, encrypted with SQLCipher.

## Hackathon Context
This project is being developed for the **Weber State AI Hackathon (March 22 - April 3, 2026)**. Our goal is to demonstrate innovation, technical complexity, and a superior user experience by building a cutting-edge, local-first AI application that directly addresses the judging criteria:
- **Innovation & Creativity**: Eliminating external banking APIs for a truly private, notification-driven budgeting solution.
- **Technical Complexity**: Implementing on-device LLM inference and an encrypted local database.
- **User Experience**: Delivering a polished, intuitive UI inspired by leading financial apps.
- **Presentation**: Preparing a compelling demo video and live presentation showcasing the real-time AI capabilities.

## Setup & Development
(Detailed setup instructions will follow, including environment setup, dependency management, and specific steps for both frontend and backend development.)
