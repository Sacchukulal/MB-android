/**
 * Magic Bill design tokens — dual theme (dark = brand default, light).
 *
 * Styling flows through CSS variables: tailwind.config.js maps semantic
 * color names (bg, surface, ink, accent…) to `rgb(var(--c-*) / <alpha>)`,
 * and `themeVars` (nativewind `vars()`) sets those variables per theme at
 * the app root. Components never hardcode a scheme — `bg-surface` etc.
 * resolve to the active palette.
 *
 * For programmatic colors (charts, icons, gradients, navigation theme) use
 * `useThemeColors()` from stores/theme.
 */
import { vars } from "nativewind";

export type ThemeMode = "light" | "dark";

export interface Palette {
  bg: string;
  bgDeep: string;
  surface: string;
  surface2: string;
  line: string;
  lineStrong: string;
  text: string;
  textMuted: string;
  textFaint: string;
  accent: string;
  accentBright: string;
  accentDeep: string;
  accentSoft: string;
  indigo: string;
  success: string;
  warning: string;
  danger: string;
  info: string;
  chart: {
    cash: string;
    card: string;
    upi: string;
    credit: string;
    series: string[];
  };
}

export const palettes: Record<ThemeMode, Palette> = {
  // Brand default — deep navy + teal, mirrors MB-website.
  dark: {
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
    chart: {
      cash: "#34d399",
      card: "#60a5fa",
      upi: "#2dd4bf",
      credit: "#fbbf24",
      series: ["#2dd4bf", "#60a5fa", "#a78bfa", "#fbbf24", "#f87171", "#34d399"],
    },
  },
  // Light — cool white surfaces, same teal brand, darker tones for contrast.
  light: {
    bg: "#f5f7fb",
    bgDeep: "#e9eef5",
    surface: "#ffffff",
    surface2: "#eef2f8",
    line: "rgba(15, 23, 42, 0.10)",
    lineStrong: "rgba(15, 23, 42, 0.18)",
    text: "#0f172a",
    textMuted: "#475569",
    textFaint: "#8494ab",
    accent: "#0d9488",
    accentBright: "#0d9488",
    accentDeep: "#0f766e",
    accentSoft: "rgba(13, 148, 136, 0.12)",
    indigo: "#4f46e5",
    success: "#059669",
    warning: "#b45309",
    danger: "#dc2626",
    info: "#2563eb",
    chart: {
      cash: "#059669",
      card: "#2563eb",
      upi: "#0d9488",
      credit: "#b45309",
      series: ["#0d9488", "#2563eb", "#7c3aed", "#b45309", "#dc2626", "#059669"],
    },
  },
};

/** "#rrggbb" -> "r g b" channel triplet for rgb(var()/alpha) tokens. */
function ch(hex: string): string {
  const n = parseInt(hex.slice(1), 16);
  return `${(n >> 16) & 255} ${(n >> 8) & 255} ${n & 255}`;
}

function toVars(p: Palette) {
  return vars({
    "--c-bg": ch(p.bg),
    "--c-bg-deep": ch(p.bgDeep),
    "--c-surface": ch(p.surface),
    "--c-surface-2": ch(p.surface2),
    "--c-ink": ch(p.text),
    "--c-ink-muted": ch(p.textMuted),
    "--c-ink-faint": ch(p.textFaint),
    "--c-accent": ch(p.accent),
    "--c-accent-bright": ch(p.accentBright),
    "--c-accent-deep": ch(p.accentDeep),
    "--c-indigo": ch(p.indigo),
    "--c-success": ch(p.success),
    "--c-warning": ch(p.warning),
    "--c-danger": ch(p.danger),
    "--c-info": ch(p.info),
    // Full-value vars (pre-baked alpha, no /opacity modifier needed)
    "--c-line": p.line,
    "--c-line-strong": p.lineStrong,
    "--c-accent-soft": p.accentSoft,
  });
}

/** Root style objects that activate a theme for the whole subtree. */
export const themeVars: Record<ThemeMode, ReturnType<typeof vars>> = {
  dark: toVars(palettes.dark),
  light: toVars(palettes.light),
};

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
