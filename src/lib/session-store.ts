import * as SecureStore from "expo-secure-store";

import type { PermissionMap } from "@/lib/permissions";

/**
 * Persisted session bits beyond what supabase-js stores itself:
 *  - the staff token session (staff door has no Supabase Auth)
 *  - the owner's selected restaurant (multi-outlet switcher)
 * Login persists forever — only explicit logout clears these.
 */

const STAFF_SESSION_KEY = "mb.staff.session";
const OWNER_RESTAURANT_KEY = "mb.owner.restaurant";

export interface StoredStaffSession {
  token: string;
  staff: {
    id: string;
    name: string;
    roleLabel: string;
    permissions: PermissionMap;
  };
  restaurant: {
    code: string;
    name: string;
  };
}

export async function saveStaffSession(s: StoredStaffSession): Promise<void> {
  await SecureStore.setItemAsync(STAFF_SESSION_KEY, JSON.stringify(s));
}

export async function loadStaffSession(): Promise<StoredStaffSession | null> {
  try {
    const raw = await SecureStore.getItemAsync(STAFF_SESSION_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as StoredStaffSession;
    return parsed?.token ? parsed : null;
  } catch {
    return null;
  }
}

export async function clearStaffSession(): Promise<void> {
  await SecureStore.deleteItemAsync(STAFF_SESSION_KEY);
}

export async function saveOwnerRestaurant(licenseKey: string): Promise<void> {
  await SecureStore.setItemAsync(OWNER_RESTAURANT_KEY, licenseKey);
}

export async function loadOwnerRestaurant(): Promise<string | null> {
  try {
    return await SecureStore.getItemAsync(OWNER_RESTAURANT_KEY);
  } catch {
    return null;
  }
}

export async function clearOwnerRestaurant(): Promise<void> {
  await SecureStore.deleteItemAsync(OWNER_RESTAURANT_KEY);
}
