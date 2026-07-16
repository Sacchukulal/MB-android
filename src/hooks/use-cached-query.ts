import { useCallback, useEffect, useRef, useState } from "react";

import { cacheGet, cacheSet } from "@/lib/db";

export interface CachedQueryState<T> {
  data: T | null;
  /** True while there is no data at all yet (first load, no cache). */
  loading: boolean;
  /** True during any fetch (used by pull-to-refresh). */
  refreshing: boolean;
  /** Set when the shown data came from cache because the network failed. */
  stale: boolean;
  /** Epoch millis of when `data` was fetched (cache age chip). */
  updatedAt: number | null;
  error: string | null;
  refresh: () => Promise<void>;
}

/**
 * Network-first query with sqlite fallback: every success is cached; when the
 * network fails, the last cached payload is served with `stale: true` so
 * screens never go empty just because internet is down.
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

  const run = useCallback(async () => {
    if (!cacheKey) return;
    setRefreshing(true);
    try {
      const fresh = await fetcherRef.current();
      setData(fresh);
      setStale(false);
      setUpdatedAt(Date.now());
      setError(null);
      await cacheSet(cacheKey, fresh);
    } catch (e) {
      const cached = await cacheGet<T>(cacheKey);
      if (cached) {
        setData(cached.data);
        setStale(true);
        setUpdatedAt(cached.updatedAt);
        setError(null);
      } else {
        setError(e instanceof Error ? e.message : "Couldn't load data");
      }
    } finally {
      setRefreshing(false);
      setLoading(false);
    }
  }, [cacheKey]);

  useEffect(() => {
    setLoading(true);
    setData(null);
    run();
  }, [run]);

  return { data, loading, refreshing, stale, updatedAt, error, refresh: run };
}
