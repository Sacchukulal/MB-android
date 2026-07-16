/**
 * Staff-side data access — everything goes through the staff-data Edge
 * Function with the stored session token. Every response carries fresh
 * permissions (owner edits apply immediately); a "revoked" response clears
 * the session so the staff guard can boot the user to the welcome screen.
 */
import { callFunction } from "@/lib/api";
import { cacheClear } from "@/lib/db";
import type { BillDetail } from "@/lib/data";
import type { PermissionMap } from "@/lib/permissions";
import {
  clearStaffSession,
  loadStaffSession,
  saveStaffSession,
} from "@/lib/session-store";
import { useAuth } from "@/stores/auth";

export class RevokedError extends Error {
  constructor() {
    super("Access revoked — contact your manager");
  }
}

export interface PctSplit {
  cash: number;
  card: number;
  upi: number;
  credit: number;
}

export type StaffSplit =
  | ({ kind: "amounts" } & PctSplit)
  | ({ kind: "pct" } & PctSplit);

export interface StaffDashboard {
  day: string;
  billCount: number;
  total: number | null;
  avg: number | null;
  split: StaffSplit;
  trend: Array<{ day: string; value: number }>;
  trendIsRelative: boolean;
  topItems: Array<{ name: string; quantity: number; amount: number | null }>;
}

export interface StaffBillRow {
  id: string;
  bill_number: string | null;
  table_number: string | null;
  payment_mode: string | null;
  total: number | null;
  billed_at: string;
}

export interface StaffReport {
  from: string;
  to: string;
  billCount: number;
  total: number | null;
  subtotal: number | null;
  gst: number | null;
  avg: number | null;
  split: StaffSplit;
  items: Array<{ name: string; quantity: number; amount: number | null }>;
  expenseTotal: number | null;
  bills: StaffBillRow[] | null;
}

interface StaffDataResponse<T> {
  ok: boolean;
  reason?: string;
  permissions?: PermissionMap;
  data?: T;
}

async function staffCall<T>(view: string, payload: object = {}): Promise<T> {
  const stored = await loadStaffSession();
  if (!stored) throw new RevokedError();

  const res = await callFunction<StaffDataResponse<T>>("staff-data", {
    token: stored.token,
    view,
    ...payload,
  });

  if (!res.ok) {
    if (res.reason === "revoked") {
      // Boot immediately: clear everything; the staff layout guard redirects.
      await clearStaffSession();
      await cacheClear().catch(() => {});
      useAuth.getState().markRevoked();
      throw new RevokedError();
    }
    throw new Error(res.reason ?? "server");
  }

  // Keep permissions fresh everywhere (store + persisted session).
  if (res.permissions) {
    const auth = useAuth.getState();
    if (auth.kind === "staff" && auth.staff) {
      auth.setStaffSession(
        { ...auth.staff, permissions: res.permissions },
        auth.restaurant ?? { licenseKey: "", name: stored.restaurant.name }
      );
    }
    await saveStaffSession({
      ...stored,
      staff: { ...stored.staff, permissions: res.permissions },
    });
  }

  return res.data as T;
}

export const fetchStaffDashboard = () =>
  staffCall<StaffDashboard>("dashboard");

export const fetchStaffReport = (from: string, to: string) =>
  staffCall<StaffReport>("report", { from, to });

export const fetchStaffBill = async (billId: string) => {
  const res = await staffCall<{ bill: BillDetail }>("bill", { billId });
  return res.bill;
};
