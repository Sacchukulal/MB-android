/** Owner account data — license row (RLS-scoped) + plan details. */
import { supabase } from "@/lib/supabase";

export interface LicenseInfo {
  key: string;
  display_name: string;
  email: string;
  mobile_number: string;
  restaurant_name: string;
  restaurant_code: string | null;
  plan_id: string;
  status: string;
  next_billing_date: string | null;
  device_id: string | null;
  device_name: string | null;
  device_platform: string | null;
  device_bound_at: string | null;
  device_last_seen: string | null;
  created_at: string;
}

export interface PlanInfo {
  id: string;
  name: string;
  amount_paise: number;
  interval_unit: string;
  description: string | null;
}

export interface VisiblePlan extends PlanInfo {
  features: string[] | null;
  display_order: number;
}

/** All plans the website shows (public RLS: visible + active only). */
export async function fetchVisiblePlans(): Promise<VisiblePlan[]> {
  const { data, error } = await supabase
    .from("plans")
    .select("id, name, amount_paise, interval_unit, description, features, display_order")
    .order("display_order", { ascending: true });
  if (error) throw error;
  return (data ?? []) as VisiblePlan[];
}

export interface AccountData {
  license: LicenseInfo;
  plan: PlanInfo | null;
}

export async function fetchAccount(licenseKey: string): Promise<AccountData> {
  const { data: license, error } = await supabase
    .from("licenses")
    .select(
      "key, display_name, email, mobile_number, restaurant_name, restaurant_code, plan_id, status, next_billing_date, device_id, device_name, device_platform, device_bound_at, device_last_seen, created_at"
    )
    .eq("key", licenseKey)
    .maybeSingle();
  if (error) throw error;
  if (!license) throw new Error("License not found");

  let plan: PlanInfo | null = null;
  if (license.plan_id) {
    // Public read policy covers visible+active plans; archived plans fall
    // back to showing the raw plan id.
    const { data } = await supabase
      .from("plans")
      .select("id, name, amount_paise, interval_unit, description")
      .eq("id", license.plan_id)
      .maybeSingle();
    plan = (data as PlanInfo) ?? null;
  }

  return { license: license as LicenseInfo, plan };
}

/** Maps a license status to a badge tone (mirrors the website's StatusBadge). */
export function statusTone(
  status: string | null | undefined
): "success" | "info" | "warning" | "danger" | "neutral" {
  const s = (status ?? "").toLowerCase();
  if (s === "active") return "success";
  if (s === "trial") return "info";
  if (s === "grace" || s === "halted" || s === "pending" || s === "created")
    return "warning";
  if (s === "cancelled" || s === "expired" || s === "revoked" || s === "suspended" || s === "completed")
    return "danger";
  return "neutral";
}

export function daysUntil(iso: string | null): number | null {
  if (!iso) return null;
  const target = new Date(iso).getTime();
  if (Number.isNaN(target)) return null;
  return Math.ceil((target - Date.now()) / (24 * 60 * 60 * 1000));
}
