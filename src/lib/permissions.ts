/**
 * The staff permission system. Permissions are a flat map of key -> boolean
 * stored per staff row (jsonb in the `staff` table). The OWNER decides every
 * toggle — there are no hardcoded role defaults. Unknown/missing keys are
 * always treated as false, which gives clean forward-compatibility when new
 * keys (e.g. take_orders in Phase 6) are added later.
 *
 * Owners never consult this map — they implicitly have every permission.
 */

export const PERMISSION_KEYS = [
  "view_dashboard",
  "view_reports",
  "view_bills",
  "view_expenses",
  "view_plan_status",
  "manage_staff",
  "take_orders",
  "view_revenue_totals",
  "export_reports",
] as const;

export type PermissionKey = (typeof PERMISSION_KEYS)[number];

export type PermissionMap = Partial<Record<PermissionKey, boolean>>;

export interface PermissionMeta {
  key: PermissionKey;
  label: string;
  description: string;
  comingSoon?: boolean;
}

/** Human-readable labels shown to the owner in the staff permission editor. */
export const PERMISSIONS: PermissionMeta[] = [
  {
    key: "view_dashboard",
    label: "View today's sales summary",
    description: "See the dashboard with today's totals and trends",
  },
  {
    key: "view_reports",
    label: "View detailed reports",
    description: "Date-range reports, item-wise sales, payment breakdowns",
  },
  {
    key: "view_bills",
    label: "View individual bills",
    description: "Open any bill and see its full receipt",
  },
  {
    key: "view_expenses",
    label: "View expenses",
    description: "See recorded expense data",
  },
  {
    key: "view_plan_status",
    label: "View plan & subscription",
    description: "See the restaurant's Magic Bill plan and billing status",
  },
  {
    key: "manage_staff",
    label: "Manage other staff",
    description: "Add, edit, or deactivate staff members (trusted managers)",
  },
  {
    key: "take_orders",
    label: "Take orders",
    description: "Take customer orders from this app",
    comingSoon: true,
  },
  {
    key: "view_revenue_totals",
    label: "See revenue amounts",
    description: "Show actual rupee amounts (off = counts and % only)",
  },
  {
    key: "export_reports",
    label: "Export & share reports",
    description: "Download or share reports as PDF/CSV",
  },
];

/** Quick-setup presets — they only pre-fill toggles, the owner can customize. */
export const PERMISSION_PRESETS: Record<string, PermissionMap> = {
  "Full access": Object.fromEntries(
    PERMISSION_KEYS.map((k) => [k, true])
  ) as PermissionMap,
  "Reports only": {
    view_dashboard: true,
    view_reports: true,
    view_bills: true,
    view_revenue_totals: true,
  },
  Minimal: {
    view_dashboard: true,
  },
};

/** The single permission check used everywhere. Missing keys are false. */
export function hasPermission(
  permissions: PermissionMap | null | undefined,
  key: PermissionKey
): boolean {
  return permissions?.[key] === true;
}
