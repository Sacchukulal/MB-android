import { useCallback, useEffect, useRef, useState } from "react";

import { cacheGet, cacheSet } from "@/lib/db";

export interface CachedQueryState<T> {
  data: T | null;
  /** True only while there is nothing to show yet (no cache, first fetch). */
  loading: boolean;
  /** True during any fetch (used by pull-to-refresh). */
  refreshing: boolean;
  /** Set when the shown data is from cache (network pending or failed). */
  stale: boolean;
  /** Epoch millis of when `data` was fetched (cache age chip). */
  updatedAt: number | null;
  error: string | null;
  refresh: () => Promise<void>;
}

/**
 * CACHE-FIRST query: cached data renders instantly (offline included), then
 * a background network fetch replaces it when it lands. Screens never sit on
 * a spinner just because the network is slow or down.
 */
export function useCachedQuery<T>(
  cacheKey: string | null,
  fetcher: () => Promise<T>
): CachedQueryState<T> {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [stale, setStale] = useState(false);
  const [updatedAt, setUpdatedAt] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const fetcherRef = useRef(fetcher);
  fetcherRef.current = fetcher;
  const hasDataRef = useRef(false);

  const run = useCallback(async () => {
    if (!cacheKey) return;
    setRefreshing(true);
    try {
      const fresh = await fetcherRef.current();
      hasDataRef.current = true;
      setData(fresh);
      setStale(false);
      setUpdatedAt(Date.now());
      setError(null);
      cacheSet(cacheKey, fresh).catch(() => {});
    } catch (e) {
      // Keep whatever is showing (cache) as stale; error only when empty.
      if (!hasDataRef.current) {
        setError(e instanceof Error ? e.message : "Couldn't load data");
      }
    } finally {
      setRefreshing(false);
      setLoading(false);
    }
  }, [cacheKey]);

  useEffect(() => {
    let cancelled = false;
    hasDataRef.current = false;
    setLoading(true);
    setData(null);
    setError(null);
    setStale(false);

    (async () => {
      if (!cacheKey) return;
      // 1. Serve cache instantly.
      const cached = await cacheGet<T>(cacheKey);
      if (cancelled) return;
      if (cached) {
        hasDataRef.current = true;
        setData(cached.data);
        setUpdatedAt(cached.updatedAt);
        setStale(true); // until the background fetch confirms
        setLoading(false);
      }
      // 2. Refresh from network in the background.
      await run();
    })();

    return () => {
      cancelled = true;
    };
  }, [cacheKey, run]);

  return { data, loading, refreshing, stale, updatedAt, error, refresh: run };
}
