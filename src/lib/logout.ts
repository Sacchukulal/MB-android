import { callFunction } from "@/lib/api";
import { cacheClear } from "@/lib/db";
import {
  clearOwnerRestaurant,
  clearStaffSession,
  loadStaffSession,
} from "@/lib/session-store";
import { supabase } from "@/lib/supabase";
import { useAuth } from "@/stores/auth";

/** Explicit owner logout — the ONLY way an owner session ends. */
export async function logoutOwner(): Promise<void> {
  await supabase.auth.signOut().catch(() => {});
  await clearOwnerRestaurant();
  await cacheClear().catch(() => {});
  useAuth.getState().clearSession();
}

/** Explicit staff logout — also deletes the session row server-side. */
export async function logoutStaff(): Promise<void> {
  const stored = await loadStaffSession();
  if (stored) {
    // Best effort; the local clear is what matters if we're offline.
    callFunction("staff-session", { action: "logout", token: stored.token }).catch(
      () => {}
    );
  }
  await clearStaffSession();
  await cacheClear().catch(() => {});
  useAuth.getState().clearSession();
}
