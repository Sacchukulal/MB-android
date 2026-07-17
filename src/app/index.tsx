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
import { registerMobileDevice } from "@/lib/device";
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
        // OPTIMISTIC: enter immediately with the stored session so the app
        // opens instantly (even offline). Server verification runs in the
        // background — a definitive "revoked" boots the user via markRevoked
        // + the staff layout guard; fresh permissions get swapped in live.
        setStaffSession(stored.staff, {
          licenseKey: "",
          name: stored.restaurant.name,
          code: stored.restaurant.code,
        });
        router.replace("/home");

        callFunction<VerifyResponse>("staff-session", {
          action: "verify",
          token: stored.token,
        })
          .then(async (res) => {
            if (!res.ok) {
              await clearStaffSession();
              useAuth.getState().markRevoked();
              return;
            }
            if (res.staff && res.restaurant) {
              const fresh: StoredStaffSession = {
                token: stored.token,
                staff: res.staff,
                restaurant: res.restaurant,
              };
              await saveStaffSession(fresh);
              useAuth.getState().setStaffSession(fresh.staff, {
                licenseKey: "",
                name: fresh.restaurant.name,
                code: fresh.restaurant.code,
              });
            }
          })
          .catch(() => {}); // offline — cached session stands
        return;
      }

      // ---------- Owner door ----------
      // getSession() silently refreshes an expired token when possible;
      // only a truly revoked session comes back null.
      const { data } = await supabase.auth.getSession().catch(() => ({ data: { session: null } }));
      if (cancelled) return;

      if (data.session) {
        // OPTIMISTIC: cached restaurant list gets the owner in instantly;
        // the network refresh updates the store in the background.
        const cached = await cachedOwnerRestaurants();
        if (cancelled) return;

        if (cached && cached.length > 0) {
          const savedKey = await loadOwnerRestaurant();
          const selected = cached.find((r) => r.licenseKey === savedKey);
          setOwnerSession(cached, selected);
          router.replace(
            !selected && cached.length > 1 ? "/auth/pick-restaurant" : "/dashboard"
          );
          registerMobileDevice((selected ?? cached[0]).licenseKey);
          loadOwnerRestaurants()
            .then((fresh) => {
              if (fresh.length > 0) {
                const sel = fresh.find((r) => r.licenseKey === savedKey);
                useAuth.getState().setOwnerSession(fresh, sel ?? fresh[0]);
              }
            })
            .catch(() => {});
          return;
        }

        // No cache (first run on this device): one network try.
        let restaurants: RestaurantInfo[] | null = null;
        try {
          restaurants = await loadOwnerRestaurants();
        } catch {
          restaurants = null;
        }
        if (cancelled) return;

        if (restaurants && restaurants.length > 0) {
          const savedKey = await loadOwnerRestaurant();
          const selected = restaurants.find((r) => r.licenseKey === savedKey);
          setOwnerSession(restaurants, selected);
          router.replace(
            !selected && restaurants.length > 1
              ? "/auth/pick-restaurant"
              : "/dashboard"
          );
          registerMobileDevice((selected ?? restaurants[0]).licenseKey);
          return;
        }
        if (restaurants && restaurants.length === 0) {
          // Signed in but no subscription/restaurant yet — onboarding.
          router.replace("/subscribe");
          return;
        }
        // Network failed with no cache: fall through to welcome so the
        // user isn't stuck on a spinner.
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
