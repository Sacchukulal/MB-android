/** @type {import('tailwindcss').Config} */
// Magic Bill design tokens — dual theme via CSS variables.
// The variables are set per-theme at the app root (see constants/theme.ts
// `themeVars`); :root below carries dark-theme defaults as a fallback.
// Values mirror MB-website/src/app/globals.css.

const c = (name) => `rgb(var(${name}) / <alpha-value>)`;

module.exports = {
  content: ["./src/**/*.{js,jsx,ts,tsx}"],
  presets: [require("nativewind/preset")],
  theme: {
    extend: {
      colors: {
        bg: {
          DEFAULT: c("--c-bg"), // page background
          deep: c("--c-bg-deep"), // recessed areas
        },
        surface: {
          DEFAULT: c("--c-surface"), // cards
          2: c("--c-surface-2"), // raised / pressed surfaces
        },
        line: {
          DEFAULT: "var(--c-line)", // hairline borders (alpha baked in)
          strong: "var(--c-line-strong)",
        },
        ink: {
          DEFAULT: c("--c-ink"),
          muted: c("--c-ink-muted"),
          faint: c("--c-ink-faint"),
        },
        accent: {
          DEFAULT: c("--c-accent"),
          bright: c("--c-accent-bright"),
          deep: c("--c-accent-deep"),
          soft: "var(--c-accent-soft)", // tinted chips (alpha baked in)
        },
        brand: {
          indigo: c("--c-indigo"),
        },
        success: c("--c-success"),
        warning: c("--c-warning"),
        danger: c("--c-danger"),
        info: c("--c-info"),
      },
      fontFamily: {
        sans: ["Inter_400Regular"],
        "sans-medium": ["Inter_500Medium"],
        "sans-semibold": ["Inter_600SemiBold"],
        "sans-bold": ["Inter_700Bold"],
        "sans-extrabold": ["Inter_800ExtraBold"],
        mono: ["monospace"],
      },
      borderRadius: {
        card: "16px",
      },
    },
  },
  plugins: [
    // Dark-theme defaults so styles resolve even outside the themed root.
    ({ addBase }) =>
      addBase({
        ":root": {
          "--c-bg": "11 18 32",
          "--c-bg-deep": "7 13 24",
          "--c-surface": "17 26 44",
          "--c-surface-2": "24 35 58",
          "--c-ink": "232 238 247",
          "--c-ink-muted": "148 163 184",
          "--c-ink-faint": "100 116 139",
          "--c-accent": "20 184 166",
          "--c-accent-bright": "45 212 191",
          "--c-accent-deep": "13 148 136",
          "--c-indigo": "79 70 229",
          "--c-success": "52 211 153",
          "--c-warning": "251 191 36",
          "--c-danger": "248 113 113",
          "--c-info": "96 165 250",
          "--c-line": "rgba(148, 163, 184, 0.14)",
          "--c-line-strong": "rgba(148, 163, 184, 0.28)",
          "--c-accent-soft": "rgba(45, 212, 191, 0.12)",
        },
      }),
  ],
};
