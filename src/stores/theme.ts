import * as SecureStore from "expo-secure-store";
import { create } from "zustand";

import { palettes, type Palette, type ThemeMode } from "@/constants/theme";

const STORAGE_KEY = "mb.theme.mode";

interface ThemeState {
  mode: ThemeMode;
  /** True once the persisted preference has been loaded. */
  hydrated: boolean;
  hydrate: () => Promise<void>;
  setMode: (mode: ThemeMode) => void;
  toggle: () => void;
}

/**
 * Theme preference — dark is the brand default; the user's choice is
 * persisted and restored on every app start (survives logout, which only
 * clears session data).
 */
export const useTheme = create<ThemeState>((set, get) => ({
  mode: "dark",
  hydrated: false,

  hydrate: async () => {
    try {
      const saved = await SecureStore.getItemAsync(STORAGE_KEY);
      if (saved === "light" || saved === "dark") {
        set({ mode: saved, hydrated: true });
        return;
      }
    } catch {
      // fall through to default
    }
    set({ hydrated: true });
  },

  setMode: (mode) => {
    set({ mode });
    SecureStore.setItemAsync(STORAGE_KEY, mode).catch(() => {});
  },

  toggle: () => get().setMode(get().mode === "dark" ? "light" : "dark"),
}));

/** Active palette for programmatic colors (charts, icons, gradients). */
export function useThemeColors(): Palette {
  const mode = useTheme((s) => s.mode);
  return palettes[mode];
}
