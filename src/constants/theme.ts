/**
 * Magic Bill design tokens — single source of truth for programmatic color
 * access (charts, navigation theme, native props). Class-based styling uses
 * the same values via tailwind.config.js.
 */

export const colors = {
  bg: "#0b1220",
  bgDeep: "#070d18",
  surface: "#111a2c",
  surface2: "#18233a",
  line: "rgba(148, 163, 184, 0.14)",
  lineStrong: "rgba(148, 163, 184, 0.28)",

  text: "#e8eef7",
  textMuted: "#94a3b8",
  textFaint: "#64748b",

  accent: "#14b8a6",
  accentBright: "#2dd4bf",
  accentDeep: "#0d9488",
  accentSoft: "rgba(45, 212, 191, 0.12)",

  indigo: "#4f46e5",

  success: "#34d399",
  warning: "#fbbf24",
  danger: "#f87171",
  info: "#60a5fa",
} as const;

/** Font family names as registered by expo-font (Google Fonts static cuts). */
export const fonts = {
  regular: "Inter_400Regular",
  medium: "Inter_500Medium",
  semibold: "Inter_600SemiBold",
  bold: "Inter_700Bold",
  extrabold: "Inter_800ExtraBold",
  mono: "monospace",
} as const;

export const spacing = {
  xs: 4,
  sm: 8,
  md: 16,
  lg: 24,
  xl: 32,
  xxl: 48,
} as const;

export const radius = {
  sm: 8,
  md: 12,
  card: 16,
  full: 9999,
} as const;

/** Palette for chart series / payment modes — stays readable on navy. */
export const chartPalette = {
  cash: "#34d399",
  card: "#60a5fa",
  upi: "#2dd4bf",
  credit: "#fbbf24",
  series: ["#2dd4bf", "#60a5fa", "#a78bfa", "#fbbf24", "#f87171", "#34d399"],
} as const;
