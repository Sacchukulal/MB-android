/**
 * Session bootstrap — the app's front door logic.
 * Login persists forever: if a stored session exists we go straight to the
 * right dashboard, silently refreshing/verifying. The login screens only
 * appear when there is truly no session (or access was revoked).
 */
import { useRouter } from "expo-router";
import { useEffect } from "react";

import { LoadingSpinner } from "@/components/ui";
import { callFunction } from "@/lib/api";
import { loadOwnerRestaurants, cachedOwnerRestaurants } from "@/lib/owner";
import {
  clearStaffSession,
  loadOwnerRestaurant,
  loadStaffSession,
  saveStaffSession,
  type StoredStaffSession,
} from "@/lib/session-store";
import { supabase } from "@/lib/supabase";
import { useAuth, type RestaurantInfo } from "@/stores/auth";

interface VerifyResponse {
  ok: boolean;
  reason?: string;
  staff?: StoredStaffSession["staff"];
  restaurant?: StoredStaffSession["restaurant"];
}

export default function Bootstrap() {
  const router = useRouter();
  const setOwnerSession = useAuth((s) => s.setOwnerSession);
  const setStaffSession = useAuth((s) => s.setStaffSession);

  useEffect(() => {
    let cancelled = false;

    (async () => {
      // ---------- Staff door ----------
      const stored = await loadStaffSession();
      if (stored) {
        // Verify server-side so owner changes (revocation, permission edits)
        // apply immediately. Network failure -> proceed with cached session
        // (offline mode); a definitive "revoked" -> clear and explain.
        const res = await callFunction<VerifyResponse>("staff-session", {
          action: "verify",
          token: stored.token,
        }).catch(() => null);

        if (cancelled) return;

        if (res && !res.ok) {
          await clearStaffSession();
          router.replace({ pathname: "/welcome", params: { revoked: "1" } });
          return;
        }

        const fresh: StoredStaffSession =
          res?.ok && res.staff && res.restaurant
            ? { token: stored.token, staff: res.staff, restaurant: res.restaurant }
            : stored;
        if (res?.ok) await saveStaffSession(fresh);

        setStaffSession(fresh.staff, {
          licenseKey: "",
          name: fresh.restaurant.name,
          code: fresh.restaurant.code,
        });
        router.replace("/home");
        return;
      }

      // ---------- Owner door ----------
      // getSession() silently refreshes an expired token when possible;
      // only a truly revoked session comes back null.
      const { data } = await supabase.auth.getSession().catch(() => ({ data: { session: null } }));
      if (cancelled) return;

      if (data.session) {
        let restaurants: RestaurantInfo[] | null = null;
        try {
          restaurants = await loadOwnerRestaurants();
        } catch {
          restaurants = await cachedOwnerRestaurants();
        }
        if (cancelled) return;

        if (restaurants && restaurants.length > 0) {
          const savedKey = await loadOwnerRestaurant();
          const selected = restaurants.find((r) => r.licenseKey === savedKey);
          setOwnerSession(restaurants, selected);
          if (!selected && restaurants.length > 1) {
            router.replace("/auth/pick-restaurant");
          } else {
            router.replace("/dashboard");
          }
          return;
        }
        // Signed in but nothing linked (or first load failed with no cache):
        // fall through to welcome so the user isn't stuck on a spinner.
      }

      router.replace("/welcome");
    })();

    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return <LoadingSpinner fullscreen />;
}
