# Contributing to Kiwi Photos 🥝

Thank you for your interest in contributing to **Kiwi Photos**! We are building a truly private, on-device AI-powered gallery that prioritizes user sovereignty, performance, and delightful computational photography.

---

## 📜 Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Our Core Guarantees & Philosophy](#our-core-guarantees--philosophy)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Fork & Clone](#fork--clone)
  - [Building the Project](#building-the-project)
- [Development Guidelines](#development-guidelines)
  - [Architecture & Layers](#architecture--layers)
  - [Threading & Performance Rules](#threading--performance-rules)
  - [Machine Learning & Privacy Invariants](#machine-learning--privacy-invariants)
  - [Code Style & Formatting](#code-style--formatting)
- [Submitting a Pull Request](#submitting-a-pull-request)
  - [Commit Message Conventions](#commit-message-conventions)
  - [PR Checklist](#pr-checklist)
- [Reporting Issues & Feature Requests](#reporting-issues--feature-requests)

---

## Code of Conduct

This project and everyone participating in it is governed by our [Code of Conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code.

---

## Our Core Guarantees & Philosophy

1. **100% Privacy & Zero Cloud Leaks**:
   - Photo analysis, semantic embeddings, face clustering, OCR text scanning, and depth bokeh MUST execute entirely on the user's device.
   - Never introduce analytics, crashlytics with user telemetry, or third-party cloud SDKs that process or upload photo metadata.
2. **Offline-First Resilience**:
   - The app must remain 100% functional without an internet connection. Cloud features are strictly restricted to optional, user-configured self-hosted backends (e.g., Immich, WebDAV) and model asset downloading.
3. **Battery & Thermal Guard**:
   - Heavy neural indexing must respect device battery and charging state. Main thread blocking is strictly prohibited.

---

## Getting Started

### Prerequisites

- **Android Studio**: Android Studio Ladybug (2024.2+) or later.
- **Java Development Kit**: JDK 17 (recommended: JetBrains Runtime or Temurin 17).
- **Android SDK**: API level 34+ platform and build-tools installed.
- **Hardware / Emulator**: An Android device or emulator running API 26+ (recommended: API 34+ with Google Play).

### Fork & Clone

```bash
# 1. Fork the repository on GitHub
# 2. Clone your fork locally
git clone https://github.com/<your-username>/kiwi.app.git
cd kiwi.app

# 3. Add upstream remote
git remote add upstream https://github.com/GeraldWambui/kiwi.app.git
```

### Building the Project

```bash
# Compile and build debug APK
./gradlew assembleDebug

# Run unit and performance tests
./gradlew testDebugUnitTest

# Run Android lint
./gradlew lintDebug
```

---

## Development Guidelines

### Architecture & Layers

Kiwi Photos adheres to **Clean Architecture** with **Jetpack Compose**, **Unidirectional Data Flow (UDF)**, and **StateFlow**:

```
Presentation (Compose UI) ──► ViewModel (UDF StateFlow) ──► Domain (ML/Processors) ──► Data (ObjectBox / MediaStore)
```

- **`ui/`**: Jetpack Compose screens (`ui/screen/`), common widgets (`ui/components/`), theme, and animation bridges.
- **`domain/`**: Pure Kotlin business models (`domain/model/`), NLP intent parsing (`domain/intent/`), and repository interfaces.
- **`ml/`**: On-device machine learning pipelines (`ml/clip/`, `ml/face/`, `ml/ocr/`, `ml/portrait/`, `ml/segmentation/`, `ml/tagging/`, `ml/vector/`, `ml/video/`).
- **`data/`**: ObjectBox vector database, Room SQLite databases, MediaStore repository, and EncryptedSharedPreferences.
- **`service/`**: Battery-aware AndroidX WorkManager background indexers.

For the comprehensive directory layout, package map, and module specifications, please read [**docs/ARCHITECTURE.md**](docs/ARCHITECTURE.md).

### Threading & Performance Rules

- **Zero Heavy Computations on `Dispatchers.Main`**: All ML inference, image decoding, vector indexing, and database queries MUST run on `Dispatchers.IO` or `Dispatchers.Default`.
- **Memory Efficiency**: Always recycle or release large intermediate `Bitmap` allocations and avoid holding references to large byte buffers.
- **Compose Recomposition**: Keep Compose state lambdas stable; use immutable data classes for screen states.

### Machine Learning & Privacy Invariants

- Neural models are stored in private internal app storage (`context.filesDir/models/`).
- Embeddings are unit-normalized ($L_2$ norm = 1.0) 512-d float vectors for MobileCLIP-S2, and 128-d float vectors for MobileFaceNet.
- Always provide graceful degradation: if an ML model is downloading or unavailable, functions should return empty fallbacks instead of crashing.

### Code Style & Formatting

- Follow standard [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html).
- Use meaningful, descriptive variable and function names.
- Document complex computational algorithms (such as custom matrix transforms or clustering thresholds).

---

## Submitting a Pull Request

### Commit Message Conventions

We follow [Conventional Commits](https://www.conventionalcommits.org/):

- `feat:` A new feature (e.g., `feat(search): add date range filter chip`)
- `fix:` A bug fix (e.g., `fix(face): handle null bounding boxes in portrait mode`)
- `docs:` Documentation changes (e.g., `docs: update self-hosted sync instructions`)
- `style:` Code style/formatting changes that do not affect logic
- `refactor:` Code changes that neither fix a bug nor add a feature
- `perf:` Performance improvements
- `test:` Adding or improving tests
- `chore:` Maintenance tasks, dependency updates, build configurations

### PR Checklist

Before submitting your pull request, ensure:
1. `./gradlew testDebugUnitTest` passes without errors.
2. `./gradlew assembleDebug` builds successfully.
3. Code adheres to on-device privacy invariants (zero cloud dependencies for core features).
4. Commits are atomic, descriptive, and logically organized.
5. The PR description clearly details the motivation, changes made, and steps to test.

---

## Reporting Issues & Feature Requests

- **Bug Reports**: Please open an issue using the Bug Report template, detailing device model, Android OS version, reproduction steps, and logcat output if applicable.
- **Feature Requests**: We welcome ideas! Explain the use case and why it aligns with our offline-first, privacy-respecting vision.
- **Security Disclosures**: Please review [SECURITY.md](SECURITY.md) before reporting security-related vulnerabilities.
