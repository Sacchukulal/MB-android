import { cacheGet, cacheSet } from "@/lib/db";
import { supabase } from "@/lib/supabase";
import type { RestaurantInfo } from "@/stores/auth";

const CACHE_KEY = "owner.restaurants";

/**
 * Loads the restaurants this owner can access (RLS-scoped read of `owners`
 * joined to `licenses`). Caches the result so the app still opens offline.
 */
export async function loadOwnerRestaurants(): Promise<RestaurantInfo[]> {
  const { data, error } = await supabase
    .from("owners")
    .select("license_key, licenses(restaurant_name, restaurant_code)");

  if (error) throw error;

  const restaurants: RestaurantInfo[] = (data ?? []).map((row) => {
    const lic = Array.isArray(row.licenses) ? row.licenses[0] : row.licenses;
    return {
      licenseKey: row.license_key,
      name: lic?.restaurant_name || "My Restaurant",
      code: lic?.restaurant_code ?? undefined,
    };
  });

  await cacheSet(CACHE_KEY, restaurants);
  return restaurants;
}

/** Offline fallback — last successfully fetched restaurant list. */
export async function cachedOwnerRestaurants(): Promise<RestaurantInfo[] | null> {
  const cached = await cacheGet<RestaurantInfo[]>(CACHE_KEY);
  return cached?.data ?? null;
}
