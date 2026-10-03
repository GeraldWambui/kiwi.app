## 📝 Summary of Changes

A clear and concise description of what this pull request does, including context, motivation, and design decisions.

Fixes #(issue number)

---

## 🛠️ Type of Change

- [ ] 🐛 Bug fix (non-breaking change which fixes an issue)
- [ ] 🚀 New feature (non-breaking change which adds functionality)
- [ ] ⚡ Performance optimization (latency, memory, or battery improvement)
- [ ] 🔨 Code refactoring (restructuring without behavior alteration)
- [ ] 📚 Documentation update
- [ ] 🧪 Tests (adding or updating test suites)

---

## 🧪 Testing Performed

Describe how you tested these changes:
- [ ] Tested on Android device / emulator (Device model: _______, Android version: _______)
- [ ] `./gradlew testDebugUnitTest` passed locally without errors.
- [ ] `./gradlew assembleDebug` compiled successfully.

---

## ✅ Contribution Checklist

- [ ] My code adheres to the clean architecture layers described in [ARCHITECTURE.md](docs/ARCHITECTURE.md).
- [ ] **Privacy Invariant**: No cloud tracking, remote telemetry, or unencrypted external data transmission is added.
- [ ] **Threading Invariant**: Zero heavy computation, bitmap operations, or ML inferences on `Dispatchers.Main`.
- [ ] **Battery Guard**: Any background processing adheres to battery and power management policies.
- [ ] I have updated relevant documentation where appropriate.
