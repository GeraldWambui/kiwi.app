# Kiwi Photos Architecture Guide 🥝

> **Scope**: High-density architectural reference for open source contributors, maintainers, and automated tools working on the **Kiwi Photos** codebase.

---

## 1. Architectural Philosophy & Layering

Kiwi Photos follows **Clean Architecture** combined with **Jetpack Compose**, **Unidirectional Data Flow (UDF)**, and **Kotlin Coroutines / StateFlow**.

```
┌────────────────────────────────────────────────────────────────────────────┐
│                             PRESENTATION LAYER                             │
│       Jetpack Compose • Material 3 • Navigation Compose • Coil 2.7.0       │
├────────────────────────────────────────────────────────────────────────────┤
│                              VIEWMODEL LAYER                               │
│       StateFlow • Unidirectional Data Flow (UDF) • Hilt ViewModels         │
├────────────────────────────────────────────────────────────────────────────┤
│                                DOMAIN LAYER                                │
│       Intent Routing • Domain Models • Repository Interfaces               │
├────────────────────────────────────────────────────────────────────────────┤
│                             ML & VISION ENGINE                             │
│  MobileCLIP-S2 (512-d) • MobileFaceNet (128-d) • ML Kit OCR & Faces       │
│  SubjectSegmenter • PortraitBokehRenderer • AestheticsEvaluator            │
├────────────────────────────────────────────────────────────────────────────┤
│                               DATA LAYER                                   │
│  ObjectBox Vector DB (C++) • Room SQLite (FTS4) • Scoped MediaStore API    │
│  EncryptedSharedPreferences (AES256-GCM) • Self-Hosted Sync Engines        │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Directory Structure & Package Map

```
app/src/main/java/co/juneai/june/
├── KiwiPhotos.kt                   # Application entry point (@HiltAndroidApp)
├── MainActivity.kt                 # Single Activity hosting NavigationHost
│
├── di/                             # Dagger Hilt dependency injection modules
│   ├── DatabaseModule.kt           # Room DB and DAO providers
│   ├── DataStoreModule.kt          # SharedPreferences & EncryptedPrefs
│   ├── ObjectBoxModule.kt          # ObjectBox BoxStore and Box<T> providers
│   └── RepositoryModule.kt         # Repositories, ML components & SecurityHelper
│
├── navigation/                     # Navigation infrastructure
│   ├── Screens.kt                  # Destination routes & arguments
│   └── KiwiPhotosNavigation.kt     # Top-level NavHost & animated transitions
│
├── ui/                             # Presentation Layer
│   ├── animation/                  # Particle systems & shared element bridges
│   │   ├── Particle.kt
│   │   └── SharedTransitionBridge.kt
│   ├── components/                 # Reusable, shared UI widgets
│   │   └── navigation/             # App-wide navigation bars & tabs
│   │       ├── BottomNavConfig.kt
│   │       ├── BottomNavItem.kt
│   │       └── KiwiPhotosBottomNavBar.kt
│   ├── theme/                      # Material 3 colors, typography, and themes
│   ├── voice/                      # On-device voice search overlays & services
│   └── screen/                     # Feature-specific screens (UDF + StateFlow)
│       ├── albums/                 # Collections, folders, and custom albums
│       ├── backup/                 # Self-hosted cloud backup setup (Immich, WebDAV)
│       ├── cleaner/                # Duplicate and blurry photo decluttering hub
│       ├── download/               # On-demand neural model downloader
│       ├── foryou/                 # Memories, Throwbacks & Year-in-Review
│       ├── map/                    # Geospatial photo cluster map
│       ├── mediadetail/            # Fullscreen photo/video viewer, EXIF, & tools
│       ├── people/                 # Facial recognition clusters, naming & merging
│       ├── photos/                 # Main chronological photo timeline
│       ├── referral/               # Referral rewards & storage tiers
│       ├── search/                 # Multimodal AI search & smart filter chips
│       ├── settings/               # App configuration & AI model settings
│       ├── subscriptions/          # Local feature entitlements
│       ├── trash/                  # Scoped Storage trash bin
│       ├── vault/                  # AES-256 encrypted hidden vault
│       └── videoplayer/            # Hardware-accelerated ExoPlayer video screen
│
├── domain/                         # Domain Layer (Pure Kotlin / Business Logic)
│   ├── intent/                     # Search NLP intent classification & parsing
│   │   ├── DateRangeFilter.kt
│   │   ├── IntentParser.kt
│   │   ├── IntentRouter.kt
│   │   └── UserIntent.kt
│   ├── model/                      # Clean domain models
│   │   ├── FileInfo.kt
│   │   ├── MediaDetail.kt
│   │   └── VaultAuthMode.kt
│   └── repository/                 # Domain repository interfaces
│       ├── GalleryRepository.kt
│       ├── MediaRepository.kt
│       ├── TrashManagerRepository.kt
│       └── VaultManagerRepository.kt
│
├── ml/                             # On-Device Machine Learning Pipelines
│   ├── clip/                       # Apple MobileCLIP-S2 512-d embeddings
│   │   ├── ClipEmbedder.kt
│   │   ├── ClipImageProcessor.kt
│   │   └── ClipTokenizer.kt
│   ├── face/                       # Face detection & MobileFaceNet 128-d clustering
│   │   ├── FaceDetectorAndClusterer.kt
│   │   └── FaceEmbedder.kt
│   ├── model/                      # Model lifecycle & storage management
│   │   └── ModelManager.kt
│   ├── nsfw/                       # On-device safety & content moderation
│   │   └── NsfwClassifier.kt
│   ├── ocr/                        # Google ML Kit Latin Text Recognition
│   │   └── OcrScanner.kt
│   ├── portrait/                   # Studio Bokeh & depth aperture simulation
│   │   └── PortraitBokehRenderer.kt
│   ├── quality/                    # Laplacian variance aesthetics & sharpness
│   │   └── AestheticsEvaluator.kt
│   ├── segmentation/               # Subject Lift & transparent sticker extraction
│   │   └── SubjectSegmenter.kt
│   ├── tagging/                    # Zero-shot classification & primary object detection
│   │   ├── PrimaryObjectDetector.kt
│   │   └── ZeroShotTagger.kt
│   ├── text/                       # Tokenizers & Byte-Pair Encoding
│   │   ├── BPETokenizer.kt
│   │   └── Tokenizer.kt
│   ├── vector/                     # Dimensionality reduction & vector operations
│   │   └── PCACompression.kt
│   └── video/                      # Temporal video keyframe extraction
│       └── VideoKeyframeExtractor.kt
│
├── data/                           # Data Layer (ObjectBox, Room, MediaStore)
│   ├── local/                      # Persistent storage
│   │   ├── converters/             # Room TypeConverters
│   │   ├── daos/                   # Room DAOs
│   │   ├── db/                     # KiwiPhotosDatabase (Room)
│   │   ├── entities/               # ObjectBox & Room database entities
│   │   ├── extensions/             # Entity mappers (EntityMappers.kt)
│   │   └── prefs/                  # EncryptedSharedPreferences managers
│   ├── model/                      # Raw data models & MediaStore projections
│   └── repository/                 # Repository implementations
│       ├── KiwiPhotosRepositoryImpl.kt
│       ├── MediaContentObserver.kt
│       ├── MediaPagingSource.kt
│       ├── MediaStoreRepository.kt
│       ├── MemoriesRepository.kt
│       ├── StorageCleanerRepository.kt
│       └── VaultManagerRepositoryImpl.kt
│
├── network/                        # Optional Network Adapters
│   └── backup/                     # Self-hosted Immich, Nextcloud & WebDAV sync
│       └── SelfHostedClient.kt
│
├── service/                        # Android Background Services & WorkManager
│   ├── IndexImagesWorker.kt        # Photo neural indexing worker
│   ├── IndexVideosWorker.kt        # Temporal video frame indexing worker
│   ├── PlaybackService.kt          # Background media playback
│   ├── SelfHostedBackupWorker.kt   # Background cloud backup worker
│   └── TrashCleanupWorker.kt       # Automatic 30-day trash expirer
│
└── utils/                          # Common Cross-Cutting Utilities
    ├── BatteryStatusManager.kt     # Power state & thermal guarding
    ├── CryptoManager.kt            # Android Keystore AES-GCM encryption
    ├── DateTimeFormatters.kt       # Date/time formatting helpers
    ├── EncryptionManager.kt        # Vault file stream encryptor
    ├── FileSizeFormatter.kt        # Human-readable byte formatting
    ├── GpsExtractor.kt             # Exif geotag parser
    ├── MediaStoreTrashHelper.kt    # Android 11+ Scoped Storage trash requests
    ├── PermissionHandler.kt        # Android 14+ partial photo access handler
    └── SecurityHelper.kt           # PIN & biometric verification
```

---

## 3. Contribution Rules & Platform Invariants

1. **Strict On-Device Processing**:
   All neural embeddings, face clustering, OCR text scanning, and computational photography MUST execute 100% on the local device. Never send photos or embeddings to external cloud servers.
2. **Main Thread Safety**:
   Zero heavy compute on `Dispatchers.Main`. All bitmap decoding, neural inference, and database access must run on `Dispatchers.IO` or `Dispatchers.Default`.
3. **Battery & Power Awareness**:
   Background indexing must respect `BatteryStatusManager.isEligibleForIndexing` (charging or battery $\ge 60\%$).
4. **Android 11+ Scoped Storage & 14+ Partial Photo Access**:
   Never assume direct raw file system access to `/sdcard/`. Always interact through `MediaStore`, `ContentResolver`, and `createTrashRequest` / `createWriteRequest`.
