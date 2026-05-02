package co.juneai.june.ui.theme

import androidx.compose.ui.graphics.Color

// ── Bright High-Contrast Green Accent (used across ALL themes) ──
val KiwiAccent           = Color(0xFFA3E635)
val KiwiAccentLight      = Color(0xFFD4F990)
val KiwiAccentDark       = Color(0xFF6B9C15)
val KiwiAccentSubtle     = Color(0x33A3E635)

val KiwiBright = Color(0xFF66FF99)
val KiwiCore = Color(0xFF66BB6A)
val KiwiDeep = Color(0xFF2E7D32)

// ── Light Theme Palette ────────────────────────────────────
val LightBgColor       = Color(0xFFEAE5D8)  // Background color
val KiwiDark           = Color(0xFF2D3E27)  // Active icons, gallery name
val Kiwi               = Color(0xFF4A6741)  // Selected container
val KiwiLight          = Color(0xFF8DA082)  // Soft sage — secondary text
val EarthCream         = Color(0xFFF9F7F2)  // Earthy cream
val EarthSand          = Color(0xFFEAE5D8)
val NavUnselected      = Color(0xFF9CA3AF)  // Unselected icons in bottom nav
val SelectedText       = Color(0xFFFFFFFF)  // Selected text

// ── Dark Theme Palette ─────────────────────────────────────
val DarkBg             = Color(0xFF0A0F09)  // Deep forest black
val DarkCard           = Color(0xFF1A1C18)  // Dark moss — cards
val DarkActiveBtn      = Color(0xFF6B8E23)  // Olive drab — active pills
val DarkActiveTxt      = Color(0xFFA7C080)  // Light green — selected text
val DarkBorder         = Color(0x1AFFFFFF)  // White 10% — borders
val DarkMutedText      = Color(0x66FFFFFF)  // White 40% — muted text
val DarkNavActive      = Color(0xFFA3E635)  // Active icon (bright green)
val DarkNavUnselected  = Color(0xFFFFFFFF)  // Unselected nav
val DarkTopBarIcons    = Color(0xFFE2E8F0)  // Top app bar icons
val DarkUnselectedText = Color(0xFF8DA082)  // Unselected option text

val CardWhite          = Color(0xFFFFFFFF)

// ── Material3 Mapping (Light) ──────────────────────────────
val LightPrimary     = Kiwi
val LightOnPrimary   = Color.White
val LightBackground  = LightBgColor
val LightSurface     = LightBgColor
val LightOnSurface   = KiwiDark
val LightSecondary   = KiwiLight

// ── Material3 Mapping (Dark) ───────────────────────────────
val DarkPrimary     = DarkActiveBtn
val DarkOnPrimary   = DarkBg
val DarkBackground  = DarkBg
val DarkSurface     = DarkCard
val DarkOnSurface   = Color.White
val DarkSecondary   = DarkMutedText

// ── Ocean Theme ────────────────────────────────────────────
val OceanPrimary        = Color(0xFF00BCD4)
val OceanOnPrimary      = Color(0xFFFFFFFF)
val OceanBackground     = Color(0xFFE0F7FA)
val OceanSurface        = Color(0xFFB2EBF2)
val OceanOnSurface      = Color(0xFF006064)
val OceanSecondary      = Color(0xFF0097A7)
val OceanSurfaceVariant = Color(0xFF80DEEA)
val OceanOutline        = Color(0xFF4DD0E1)
val OceanInverseSurface = Color(0xFF004D40)
val OceanInverseOnSurface = Color(0xFFE0F2F1)
val OceanPrimaryContainer = Color(0xFF006064)
val OceanOnPrimaryContainer = Color(0xFFE0F7FA)
val OceanOnBackground   = Color(0xFF00363A)

// ── Forest Theme ───────────────────────────────────────────
val ForestPrimary       = Color(0xFF3E7B27)
val ForestOnPrimary     = Color.White
val ForestBackground    = Color(0xFFECF0E7)
val ForestSurface       = Color(0xFFDDE5D5)
val ForestOnSurface     = Color(0xFF1B3A12)
val ForestSecondary     = Color(0xFF6B8E5A)
val ForestSurfaceVariant = Color(0xFFC4D6B8)
val ForestOutline       = Color(0xFF8DA882)
val ForestInverseSurface = Color(0xFF1B3A12)
val ForestInverseOnSurface = Color(0xFFE8F0E2)
val ForestPrimaryContainer = Color(0xFF1B3A12)
val ForestOnPrimaryContainer = Color(0xFFD4F990)
val ForestOnBackground  = Color(0xFF1A2E14)

// ── Sunset Theme ──────────────────────────────────────────
val SunsetPrimary       = Color(0xFFFF5722)
val SunsetOnPrimary     = Color(0xFFFFFFFF)
val SunsetBackground    = Color(0xFFFFF3E0)
val SunsetSurface       = Color(0xFFFFE0B2)
val SunsetOnSurface     = Color(0xFFBF360C)
val SunsetSecondary     = Color(0xFFFF6F00)
val SunsetSurfaceVariant = Color(0xFFFFCC80)
val SunsetOutline       = Color(0xFFFFB74D)
val SunsetInverseSurface = Color(0xFF4E2700)
val SunsetInverseOnSurface = Color(0xFFFFF8E1)
val SunsetPrimaryContainer = Color(0xFFBF360C)
val SunsetOnPrimaryContainer = Color(0xFFFFF3E0)
val SunsetOnBackground  = Color(0xFF5D2100)

// ── Alpine Theme ──────────────────────────────────────────
val AlpineE2E8F0 = Color(0xFFE2E8F0)
val Alpine64748B = Color(0xFF64748B)
val AlpineFFFFFF = Color(0xFFFFFFFF)
val Alpine1E293B = Color(0xFF1E293B)
val AlpineF1F5F9 = Color(0xFFF1F5F9)
val Alpine0F172A = Color(0xFF0F172A)
val Alpine334155 = Color(0xFF334155)
val Alpine475569 = Color(0xFF475569)
val Alpine94A3B8 = Color(0xFF94A3B8)
val AlpineF8FAFC = Color(0xFFF8FAFC)

// Accent helpers
val Blue   = Color(0xFF2196F3)
val Red    = Color(0xFFF44336)
val Green  = Color(0xFF4CAF50)
val Orange = Color(0xFFFF9800)

//custom albums screen
val TitleColor = KiwiDark
val CountColor = KiwiLight
val AccentColor = Kiwi
val BorderColor = EarthSand
val CardBg = CardWhite
val CardShadowColor = Color.Black.copy(alpha = .1f)