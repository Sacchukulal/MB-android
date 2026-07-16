import type { ReactNode } from "react";

import type { PermissionKey } from "@/lib/permissions";
import { useCan } from "@/stores/auth";

export interface PermissionGateProps {
  /** The permission required to render children. Owners always pass. */
  permission: PermissionKey;
  children: ReactNode;
  /** Rendered when the permission is missing (default: nothing). */
  fallback?: ReactNode;
}

/**
 * Client-side permission gating — hides UI the staff member can't use.
 * NOTE: this is UX only, never security. Every privileged call is also
 * checked server-side in the Edge Functions.
 */
export function PermissionGate({
  permission,
  children,
  fallback = null,
}: PermissionGateProps) {
  const allowed = useCan(permission);
  return <>{allowed ? children : fallback}</>;
}
