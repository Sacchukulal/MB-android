/** @type {import('tailwindcss').Config} */
// Magic Bill design tokens — mirrors MB-website/src/app/globals.css.
// Committed dark theme: deep navy base + teal/emerald accent.
module.exports = {
  content: ["./src/**/*.{js,jsx,ts,tsx}"],
  presets: [require("nativewind/preset")],
  theme: {
    extend: {
      colors: {
        bg: {
          DEFAULT: "#0b1220", // page background
          deep: "#070d18", // recessed areas
        },
        surface: {
          DEFAULT: "#111a2c", // cards
          2: "#18233a", // raised / pressed surfaces
        },
        line: {
          DEFAULT: "rgba(148, 163, 184, 0.14)", // hairline borders
          strong: "rgba(148, 163, 184, 0.28)",
        },
        ink: {
          DEFAULT: "#e8eef7",
          muted: "#94a3b8",
          faint: "#64748b",
        },
        accent: {
          DEFAULT: "#14b8a6", // teal-500
          bright: "#2dd4bf", // teal-400 — links, highlights
          deep: "#0d9488", // teal-600 — gradients, hover
          soft: "rgba(45, 212, 191, 0.12)", // tinted chips
        },
        brand: {
          indigo: "#4f46e5", // logo indigo, used sparingly
        },
        success: "#34d399",
        warning: "#fbbf24",
        danger: "#f87171",
        info: "#60a5fa",
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
  plugins: [],
};
