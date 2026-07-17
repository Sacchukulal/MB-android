/**
 * Owner phone registration — POS-style device identity in the DB.
 * Called after owner login and on app open (fire-and-forget): records
 * ANDROID_ID + model on the owner's `owners` row via the mobile-device
 * Edge Function and keeps mobile_last_seen fresh.
 */
import * as Application from "expo-application";
import * as Device from "expo-device";
import { Platform } from "react-native";

import { callFunction } from "@/lib/api";
import { supabase } from "@/lib/supabase";
import { installedVersion } from "@/lib/update";

export async function registerMobileDevice(licenseKey: string): Promise<void> {
  try {
    const { data } = await supabase.auth.getSession();
    const token = data.session?.access_token;
    if (!token || !licenseKey) return;

    const deviceId = Application.getAndroidId() ?? "unknown";
    await callFunction(
      "mobile-device",
      {
        licenseKey,
        deviceId,
        deviceName: `${Device.brand ?? ""} ${Device.modelName ?? ""}`.trim(),
        platform: `${Platform.OS} ${Device.osVersion ?? ""}`.trim(),
        appVersion: installedVersion(),
      },
      { token }
    );
  } catch {
    // Best effort — registration retries on the next app open.
  }
}
