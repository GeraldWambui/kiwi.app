# Security & Privacy Policy 🛡️

At **Kiwi Photos**, user privacy and data security are fundamental architectural requirements, not optional features.

---

## 🔒 Privacy & On-Device Security Guarantees

Kiwi Photos is architected with strict cryptographic and isolation boundaries:

1. **100% On-Device Neural Processing**:
   - Semantic embeddings (MobileCLIP-S2), face clustering (MobileFaceNet), OCR text extraction (ML Kit), and depth segmentation execute locally on your device CPU/GPU/NPU.
   - Photos, videos, embeddings, and biometrics are never transmitted to external AI servers.
2. **Hardware-Backed AES-256 Vault**:
   - Hidden files in the Secure Vault are encrypted with **AES-256-GCM**.
   - Keys are generated and stored in the Android KeyStore hardware security module (StrongBox / TEE where supported).
3. **Encrypted Credentials**:
   - Self-hosted sync credentials (e.g., Immich API keys, WebDAV passwords) are stored in `EncryptedSharedPreferences` backed by master keys in the Android KeyStore.
4. **Zero Telemetry**:
   - The app does not include third-party advertising SDKs, tracking frameworks, or diagnostic telemetry.

---

## 📦 Supported Versions

Security updates and patches are provided for the following releases:

| Version | Supported | Minimum Android OS | Target Android OS |
| :--- | :--- | :--- | :--- |
| `main` branch (1.0.x) | :white_check_mark: | Android 8.0+ (API 26) | Android 15 (API 35+) |

---

## 🚨 Reporting a Vulnerability

If you discover a security vulnerability or potential privacy leak in Kiwi Photos, we appreciate your responsible disclosure:

1. **Do NOT open a public GitHub issue** for undisclosed security vulnerabilities.
2. Email your disclosure to **`geraldwainaina25@gmail.com`** with the subject line: `[SECURITY] Kiwi Photos Vulnerability Report`.
3. Please include:
   - A detailed description of the vulnerability.
   - Proof of concept (PoC) code or step-by-step reproduction instructions.
   - Affected device(s), Android OS versions, and app version.
   - Any suggested remediations or patches.

### Response Timeline

- **Initial Response**: Within 48 hours acknowledging receipt of your report.
- **Triage & Assessment**: Within 5 business days with vulnerability confirmation and severity rating.
- **Fix & Disclosure**: We will work with you to patch the vulnerability and coordinate a responsible public release and security advisory.
