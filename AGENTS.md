# 🥝 KIWI PHOTOS — AI AGENT & SYSTEM CONTEXT DOCUMENT (AGENTS.md)

> **Document Purpose**: This file serves as the definitive high-density architectural, domain, and operational context for AI assistants, agents, and engineers working on the **Kiwi Photos** codebase.

---

## 1. 🎯 Product Identity & Core Philosophy

- **App Name**: Kiwi Photos (`co.juneai.june`)
- **Category**: Offline-First Intelligent Photo & Video Gallery for Android
- **Core Principle**: **100% Privacy & Zero Cloud Leaks**. All neural embeddings, facial recognition, OCR text scanning, clustering, search indexing, and computational photography MUST execute entirely on-device. No telemetry, third-party analytics, or cloud dependencies exist for core gallery features.
- **Target OS**: Android 8.0+ (API 26+) through Android 15+ (API 36+).

---

## 2. 🏛️ Tech Stack & Architectural Layers

```
┌────────────────────────────────────────────────────────────────────────────┐
│                             PRESENTATION LAYER                             │
│  Jetpack Compose • Material 3 • Navigation Compose • Coil Image Loading   │
├────────────────────────────────────────────────────────────────────────────┤
│                               VIEWMODEL LAYER                              │
│  StateFlow • Unidirectional Data Flow (UDF) • Coroutine Scopes • Hilt DI   │
├────────────────────────────────────────────────────────────────────────────┤
│                              DOMAIN & ML ENGINE                            │
│  MobileCLIP-S2 (512-d) • MobileFaceNet (128-d) • ML Kit OCR & Faces       │
│  SubjectSegmenter • PortraitBokehRenderer • BatteryStatusManager           │
├────────────────────────────────────────────────────────────────────────────┤
│                                STORAGE LAYER                               │
│  ObjectBox Vector DB • MediaStore API • EncryptedSharedPreferences (AES256)│
└────────────────────────────────────────────────────────────────────────────┘
```

| Component | Technology | Role / Notes |
| :--- | :--- | :--- |
| **Language** | Kotlin 1.9+ | Modern, coroutine-native |
| **UI Framework** | Jetpack Compose + Material 3 | Declarative, smooth 60fps animations |
| **Dependency Injection** | Dagger Hilt | `@HiltViewModel`, `@AndroidEntryPoint`, `@Singleton` |
| **Vector & Spatial DB** | ObjectBox (`io.objectbox`) | Ultra-fast native C++ vector database for on-device embeddings |
| **Relational / FTS DB** | Room SQLite | FTS4 table for full-text OCR keyword search |
| **Background Tasks** | AndroidX WorkManager | Fault-tolerant, battery-aware periodic and one-time workers |
| **Image Loading** | Coil 2.7.0 (`io.coil-kt`) | Coroutine-first image loading with memory LRU cache |
| **Encrypted Storage** | AndroidX Security Crypto | `EncryptedSharedPreferences` backed by Android KeyStore (`AES256_GCM`) |
| **Vision AI (Semantic)** | Apple MobileCLIP-S2 (TFLite) | 512-dimensional multimodal vision-language embeddings |
| **Biometric Face AI** | MobileFaceNet (TFLite) | 128-dimensional facial recognition embeddings |
| **Vision Utilities** | Google ML Kit | Latin Text Recognition (OCR) + BlazeFace Face Detection |

---

## 3. 🧠 Machine Learning & Computational Photography Pipelines

### A. Semantic Vision-Language Search ([ClipEmbedder.kt](file:///Users/cadmus/AndroidStudioProjects/Kiwi/app/src/main/java/co/juneai/june/ml/clip/ClipEmbedder.kt))
- **Model**: Apple MobileCLIP-S2 (512-d embeddings). Hosted on Cloudflare CDN (`https://kiwi-model-server.kighurugerald.workers.dev/mobileclip_s2.tflite`).
- **Delivery**: Downloaded dynamically upon app launch via game-style asset loader or on-demand in Settings, drastically reducing APK size from ~400 MB to ~10 MB. Saved to `context.filesDir/models/mobileclip_s2.tflite` for 100% offline private execution.
- **Dimensions**: 512-d unit-normalized float vectors ($L_2$ norm $= 1.0$).
- **Inference**: Executed with 4-thread XNNPACK CPU acceleration on `Dispatchers.IO`.
- **Query Flow**:
  1. Text query encoded into 512-d vector via `ClipEmbedder.embedText(query)`.
  2. Cosine similarity scored across `ImageEmbedding` table in ObjectBox in $< 5\text{ms}$.
  3. Hybrid ranking merges vector score with OCR keyword matches and named face tags.

### B. Facial Clustering Pipeline ([FaceDetectorAndClusterer.kt](file:///Users/cadmus/AndroidStudioProjects/Kiwi/app/src/main/java/co/juneai/june/ml/face/FaceDetectorAndClusterer.kt))
- **Stage 1 (Detection)**: ML Kit `FaceDetection` locates face bounding boxes, rotation angles, and smile probabilities.
- **Stage 2 (Embedding)**: `FaceEmbedder.kt` feeds cropped face into `assets/mobilefacenet.tflite` to produce a 128-d biometric vector.
- **Stage 3 (Centroid Clustering)**: Assigns face to cluster using prototype centroid cosine matching ($\text{threshold} \ge 0.72$), eliminating cluster drift.
- **Naming & Merging**: Named people update search tokens so queries like *"Sarah at the beach"* work automatically.

### C. Subject Lift & Sticker Maker ([SubjectSegmenter.kt](file:///Users/cadmus/AndroidStudioProjects/Kiwi/app/src/main/java/co/juneai/june/ml/segmentation/SubjectSegmenter.kt))
- Extracts primary foreground persons, pets, or objects in $< 25\text{ms}$.
- Renders an anti-aliased transparent `ARGB_8888` PNG with glowing shimmer.
- Exports directly to WhatsApp, Telegram, Discord, Messages via `Intent.ACTION_SEND` (`image/png`).

### D. Studio Portrait Mode & Depth Bokeh ([PortraitBokehRenderer.kt](file:///Users/cadmus/AndroidStudioProjects/Kiwi/app/src/main/java/co/juneai/june/ml/portrait/PortraitBokehRenderer.kt))
- Multi-pass $O(1)$ separable stack blur algorithm applied to background mask.
- Variable aperture depth slider: $f/1.4$ (maximum bokeh) to $f/16$ (sharp).
- Studio lighting color matrices: *Natural Bokeh*, *Stage Mono (B&W Background)*, *Studio Spotlight*, *Golden Hour Warmth*.

---

## 4. 🗄️ Database Schemas & Storage Entities

### 1. `ImageEmbedding` (ObjectBox Entity)
- `@Id var id: Long`
- `@Index var mediaStoreId: Long` (Fast $O(1)$ indexed lookup)
- `var embedding: FloatArray?` (512-d vector)
- `var uri: String`, `var name: String`, `var mimeType: String`
- `var isTrashed: Boolean`, `var trashedAt: Long?`
- `var primaryObject: String?`, `var aestheticScore: Float`, `var sharpnessScore: Float`

### 2. `BackupRecord` (ObjectBox Entity)
- `@Id var id: Long`
- `@Index var mediaStoreId: Long`
- `var remoteAssetId: String?`, `var providerName: String`
- `var backedUpAt: Long`, `var fileSize: Long`, `var isSuccess: Boolean`

### 3. `FaceClusterEntity` & `FaceClusterSummary` (ObjectBox / Room)
- `var clusterId: String`, `var personName: String`, `var coverMediaStoreId: Long`
- `var embedding: FloatArray` (128-d), `var smileProbability: Float`

---

## 5. ⚙️ Background Services & Workers

| Worker | Class | Trigger / Policy | Resilience & Power Rules |
| :--- | :--- | :--- | :--- |
| **Photo Neural Indexer** | `IndexImagesWorker` | Unique Work (`index_images_work`) | Resumes in $O(1)$ from `HashSet` checkpoint; paused if battery $< 60\%$ without charger. |
| **Video Keyframe Indexer**| `IndexVideosWorker` | Unique Work (`index_videos_work`) | Extracts temporal keyframes at scene shifts; corrupt files isolated into placeholders. |
| **Self-Hosted Cloud Backup** | `SelfHostedBackupWorker` | Periodic (6 hrs) / One-Time | Streams multipart asset bytes chunk-by-chunk to Immich (REST) or WebDAV (`PUT`). Enforces Wi-Fi / Charging constraints. |

---

## 6. 📱 Navigation & Screen Registry ([KiwiPhotosNavigation.kt](file:///Users/cadmus/AndroidStudioProjects/Kiwi/app/src/main/java/co/juneai/june/navigation/KiwiPhotosNavigation.kt))

### Bottom Navigation Tabs (4 Core Tabs)
1. **`Photos` (`Screens.Library`)**: Chronological day/month timeline with top bar Settings entry.
2. **`Memories` (`Screens.ForYou`)**: "On This Day", Monthly Throwbacks, Trip Highlights.
3. **`Search` (`Screens.Search`)**: Multimodal AI search bar, People & Pets carousel, Category filters.
4. **`Collections` (`Screens.Albums`)**: Folders, People & Pets, Map, Cleaner, Trash, Vault.

### Dedicated Hubs
- **`Screens.People` ([PeopleScreen.kt](file:///Users/cadmus/AndroidStudioProjects/Kiwi/app/src/main/java/co/juneai/june/ui/screen/people/PeopleScreen.kt))**: People & Pets gallery, Naming (✏️), Merging (🔀), Re-clustering (🔄).
- **`Screens.PhotoMap` ([PhotoMapScreen.kt](file:///Users/cadmus/AndroidStudioProjects/Kiwi/app/src/main/java/co/juneai/june/ui/screen/map/PhotoMapScreen.kt))**: Geographic spatial map using MapLibre/OSM.
- **`Screens.StorageCleaner` ([StorageCleanerScreen.kt](file:///Users/cadmus/AndroidStudioProjects/Kiwi/app/src/main/java/co/juneai/june/ui/screen/cleaner/StorageCleanerScreen.kt))**: Duplicate & blur de-cluttering.
- **`Screens.SelfHostedBackup` ([SelfHostedBackupScreen.kt](file:///Users/cadmus/AndroidStudioProjects/Kiwi/app/src/main/java/co/juneai/june/ui/screen/backup/SelfHostedBackupScreen.kt))**: Immich, Nextcloud, and WebDAV sync settings.
- **`Screens.Settings` ([SettingsScreen.kt](file:///Users/cadmus/AndroidStudioProjects/Kiwi/app/src/main/java/co/juneai/june/ui/screen/settings/SettingsScreen.kt))**: 6-Section On-Device AI, Security, Display, and Cleaner hub.
- **`Screens.Passcode` / `Screens.SecureVault`**: AES-256 encrypted hidden space.

---

## 7. 🛡️ System Integration & Android Platform Invariants

1. **Scoped Storage & Trash Sync (Android 11+ / API 30+)**:
   - Trashing items uses `MediaStore.createTrashRequest(contentResolver, uris, true)` so photos are synchronized with the OS trash across all gallery apps.
2. **Partial Photo Access (Android 14 / API 34+)**:
   - Handles `READ_MEDIA_VISUAL_USER_SELECTED` gracefully with `SelectedPhotoAccess` suppression.
3. **Threading & Freezing Guarantees**:
   - **Zero Heavy Computations on `Dispatchers.Main`**: All bitmap decoding, ML inferences, vector queries, and DB writes MUST run on `Dispatchers.IO` or `Dispatchers.Default`.
4. **Battery & Thermal Guard**:
   - Heavy neural indexing is restricted to **Charging State OR Battery $\ge 60\%$** (`BatteryStatusManager.kt`).

---

## 8. 📂 Key Directory Reference

```
app/src/main/java/co/juneai/june/
├── data/
│   ├── local/              # DAOs, ObjectBox Entities, EncryptedSharedPreferences, EntityMappers
│   ├── model/              # MediaItem, SearchResult, ExifData models
│   └── repository/         # MediaStoreRepository, StorageCleanerRepository, MemoriesRepository
├── domain/
│   ├── intent/             # IntentParser, IntentRouter, DateRangeFilter
│   ├── model/              # MediaDetail, FileInfo, VaultAuthMode
│   └── repository/         # GalleryRepository, VaultManagerRepository
├── ml/
│   ├── clip/               # ClipEmbedder.kt (MobileCLIP-S2 & CLIP ViT-B/32)
│   ├── face/               # FaceDetectorAndClusterer.kt & FaceEmbedder.kt
│   ├── ocr/                # OcrScanner.kt (ML Kit Text Recognition)
│   ├── portrait/           # PortraitBokehRenderer.kt (Depth Bokeh & Studio Light)
│   ├── segmentation/       # SubjectSegmenter.kt (Subject Lift & Stickers)
│   ├── tagging/            # ZeroShotTagger.kt & PrimaryObjectDetector.kt
│   └── vector/             # PCACompression.kt
├── navigation/             # Screens.kt, KiwiPhotosNavigation.kt
├── network/backup/         # SelfHostedClient.kt, ImmichBackupClient, WebDavBackupClient
├── service/                # IndexImagesWorker, IndexVideosWorker, SelfHostedBackupWorker
├── ui/
│   ├── animation/          # Particle.kt, SharedTransitionBridge.kt
│   ├── components/         # KiwiPhotosBottomNavBar, BottomNavItem, BottomNavConfig
│   └── screen/             # Compose screens (photos, search, people, albums, backup, etc.)
└── utils/                  # BatteryStatusManager, CryptoManager, DateTimeFormatters, FileSizeFormatter
```

---

## 9. 🧪 Testing & Verification Reference

Unit tests are located in [PerformanceUnitTest.kt](file:///Users/cadmus/AndroidStudioProjects/Kiwi/app/src/test/java/co/juneai/june/performance/PerformanceUnitTest.kt).
Run all unit tests with:
```bash
./gradlew testDebugUnitTest
```
