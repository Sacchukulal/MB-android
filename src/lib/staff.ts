/** Owner-side staff management — typed wrapper over the staff-manage function. */
import { callFunction } from "@/lib/api";
import type { PermissionMap } from "@/lib/permissions";
import { supabase } from "@/lib/supabase";

export interface StaffRow {
  id: string;
  name: string;
  role_label: string;
  permissions: PermissionMap;
  is_active: boolean;
  created_at: string;
  last_login: string | null;
}

export interface StaffListResult {
  staff: StaffRow[];
  restaurantCode: string | null;
}

interface ManageResponse {
  ok: boolean;
  reason?: string;
  staff?: StaffRow | StaffRow[];
  restaurantCode?: string | null;
}

export class StaffError extends Error {
  constructor(public reason: string) {
    super(
      reason === "pin-taken"
        ? "That PIN is already used by another staff member — pick a different one."
        : reason === "forbidden" || reason === "unauthorized"
          ? "You don't have access to this restaurant."
          : "Something went wrong — try again."
    );
  }
}

async function manage(action: string, licenseKey: string, payload: object = {}) {
  const { data } = await supabase.auth.getSession();
  const token = data.session?.access_token;
  if (!token) throw new StaffError("unauthorized");
  const res = await callFunction<ManageResponse>(
    "staff-manage",
    { action, licenseKey, ...payload },
    { token }
  );
  if (!res.ok) throw new StaffError(res.reason ?? "server");
  return res;
}

export async function listStaff(licenseKey: string): Promise<StaffListResult> {
  const res = await manage("list", licenseKey);
  return {
    staff: (res.staff as StaffRow[]) ?? [],
    restaurantCode: res.restaurantCode ?? null,
  };
}

export async function ensureRestaurantCode(licenseKey: string): Promise<string> {
  const res = await manage("ensure_code", licenseKey);
  return res.restaurantCode!;
}

export async function createStaff(
  licenseKey: string,
  input: { name: string; roleLabel: string; pin: string; permissions: PermissionMap }
): Promise<StaffRow> {
  const res = await manage("create", licenseKey, input);
  return res.staff as StaffRow;
}

export async function updateStaff(
  licenseKey: string,
  staffId: string,
  patch: {
    name?: string;
    roleLabel?: string;
    permissions?: PermissionMap;
    isActive?: boolean;
  }
): Promise<void> {
  await manage("update", licenseKey, { staffId, ...patch });
}

export async function resetStaffPin(
  licenseKey: string,
  staffId: string,
  pin: string
): Promise<void> {
  await manage("reset_pin", licenseKey, { staffId, pin });
}

export async function removeStaff(
  licenseKey: string,
  staffId: string
): Promise<void> {
  await manage("remove", licenseKey, { staffId });
}

/** Random 4-digit PIN suggestion (owner can still type their own). */
export function generatePin(): string {
  return String(Math.floor(1000 + Math.random() * 9000));
}
