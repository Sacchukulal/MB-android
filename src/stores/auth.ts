import { create } from "zustand";

import type { PermissionKey, PermissionMap } from "@/lib/permissions";
import { hasPermission } from "@/lib/permissions";

/**
 * Global auth/session state. Two kinds of sessions:
 *  - "owner": Supabase Auth session (email+password) — implicitly has every
 *    permission; may own multiple restaurants (license keys).
 *  - "staff": lightweight token session created by the staff-login Edge
 *    Function — access controlled entirely by the owner-set permission map.
 *
 * Populated during Phase B login flows; PermissionGate and screens read it.
 */

export interface RestaurantInfo {
  /** Empty string for staff sessions — staff never see the license key. */
  licenseKey: string;
  name: string;
  /** Human-readable restaurant code (staff login code), when known. */
  code?: string;
}

export interface StaffInfo {
  id: string;
  name: string;
  roleLabel: string;
  permissions: PermissionMap;
}

interface AuthState {
  kind: "owner" | "staff" | null;
  /** Currently selected restaurant (owners can switch between outlets). */
  restaurant: RestaurantInfo | null;
  /** All restaurants this owner can access (single entry for staff). */
  restaurants: RestaurantInfo[];
  /** Present only for staff sessions. */
  staff: StaffInfo | null;
  /** True when the session was killed server-side (vs voluntary logout). */
  revoked: boolean;

  setOwnerSession: (restaurants: RestaurantInfo[], active?: RestaurantInfo) => void;
  setStaffSession: (staff: StaffInfo, restaurant: RestaurantInfo) => void;
  switchRestaurant: (restaurant: RestaurantInfo) => void;
  clearSession: () => void;
  markRevoked: () => void;
  ackRevoked: () => void;
}

export const useAuth = create<AuthState>((set) => ({
  kind: null,
  restaurant: null,
  restaurants: [],
  staff: null,
  revoked: false,

  setOwnerSession: (restaurants, active) =>
    set({
      kind: "owner",
      restaurants,
      restaurant: active ?? restaurants[0] ?? null,
      staff: null,
    }),

  setStaffSession: (staff, restaurant) =>
    set({ kind: "staff", staff, restaurant, restaurants: [restaurant] }),

  switchRestaurant: (restaurant) => set({ restaurant }),

  clearSession: () =>
    set({
      kind: null,
      restaurant: null,
      restaurants: [],
      staff: null,
      revoked: false,
    }),

  markRevoked: () =>
    set({
      kind: null,
      restaurant: null,
      restaurants: [],
      staff: null,
      revoked: true,
    }),

  ackRevoked: () => set({ revoked: false }),
}));

/**
 * The one permission check used across the app.
 * Owners always pass; staff pass only if the owner enabled the key.
 */
export function useCan(key: PermissionKey): boolean {
  const kind = useAuth((s) => s.kind);
  const staff = useAuth((s) => s.staff);
  if (kind === "owner") return true;
  if (kind === "staff") return hasPermission(staff?.permissions, key);
  return false;
}
