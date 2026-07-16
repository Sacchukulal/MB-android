/**
 * In-app auto-update for direct-APK distribution (no Play Store).
 *
 * Every GitHub release of Sacchukulal/MB-android carries a `version.json`
 * asset: { "version": "1.0.0", "apk_url": "https://github.com/.../magic-bill.apk",
 *          "release_notes": "..." }.
 * `releases/latest/download/version.json` always points at the newest one.
 *
 * The app checks on launch (and manually from Account). Updates are never
 * forced: the modal can be dismissed per-version, after which screens may
 * show a subtle banner instead.
 */
import * as Application from "expo-application";
import Constants from "expo-constants";
import * as SecureStore from "expo-secure-store";
import { create } from "zustand";

const VERSION_JSON_URL =
  "https://github.com/Sacchukulal/MB-android/releases/latest/download/version.json";
const DISMISSED_KEY = "mb.update.dismissed";

export interface UpdateInfo {
  version: string;
  apk_url: string;
  release_notes?: string;
}

/** Installed app version — native versionName in builds, app.json in dev. */
export function installedVersion(): string {
  return (
    Application.nativeApplicationVersion ??
    Constants.expoConfig?.version ??
    "0.0.0"
  );
}

/** True if `a` is a newer semver than `b`. Non-numeric parts compare as 0. */
export function isNewerVersion(a: string, b: string): boolean {
  const pa = a.replace(/^v/, "").split(".").map((n) => parseInt(n, 10) || 0);
  const pb = b.replace(/^v/, "").split(".").map((n) => parseInt(n, 10) || 0);
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const x = pa[i] ?? 0;
    const y = pb[i] ?? 0;
    if (x !== y) return x > y;
  }
  return false;
}

interface UpdateState {
  /** A newer release, when found. */
  available: UpdateInfo | null;
  /** True while the launch/manual check runs. */
  checking: boolean;
  /** The user dismissed the modal for this version (banner-only mode). */
  dismissed: boolean;
  check: () => Promise<UpdateInfo | null | "up-to-date" | "error">;
  dismiss: () => void;
}

export const useUpdate = create<UpdateState>((set, get) => ({
  available: null,
  checking: false,
  dismissed: false,

  check: async () => {
    set({ checking: true });
    try {
      const res = await fetch(VERSION_JSON_URL, {
        headers: { Accept: "application/json" },
      });
      if (!res.ok) return "error";
      const info = (await res.json()) as UpdateInfo;
      if (!info?.version || !info?.apk_url) return "error";

      if (isNewerVersion(info.version, installedVersion())) {
        const dismissedVersion = await SecureStore.getItemAsync(DISMISSED_KEY);
        set({ available: info, dismissed: dismissedVersion === info.version });
        return info;
      }
      set({ available: null, dismissed: false });
      return "up-to-date";
    } catch {
      return "error";
    } finally {
      set({ checking: false });
    }
  },

  dismiss: () => {
    const v = get().available?.version;
    if (v) SecureStore.setItemAsync(DISMISSED_KEY, v).catch(() => {});
    set({ dismissed: true });
  },
}));
