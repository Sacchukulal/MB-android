import * as SQLite from "expo-sqlite";

/**
 * Offline cache — a simple key/value store over expo-sqlite.
 * Every successful network fetch writes here; when offline, screens read the
 * cached payload and show a "Last updated X ago" indicator instead of an
 * empty screen.
 */

let dbPromise: Promise<SQLite.SQLiteDatabase> | null = null;

function getDb(): Promise<SQLite.SQLiteDatabase> {
  if (!dbPromise) {
    dbPromise = (async () => {
      const db = await SQLite.openDatabaseAsync("magicbill-cache.db");
      await db.execAsync(`
        pragma journal_mode = WAL;
        create table if not exists kv_cache (
          key         text primary key not null,
          value       text not null,
          updated_at  integer not null
        );
      `);
      return db;
    })();
  }
  return dbPromise;
}

export interface CachedValue<T> {
  data: T;
  /** Epoch millis of when this value was cached. */
  updatedAt: number;
}

export async function cacheSet(key: string, data: unknown): Promise<void> {
  const db = await getDb();
  await db.runAsync(
    `insert into kv_cache (key, value, updated_at) values (?, ?, ?)
     on conflict(key) do update set value = excluded.value, updated_at = excluded.updated_at`,
    key,
    JSON.stringify(data),
    Date.now()
  );
}

export async function cacheGet<T>(key: string): Promise<CachedValue<T> | null> {
  const db = await getDb();
  const row = await db.getFirstAsync<{ value: string; updated_at: number }>(
    `select value, updated_at from kv_cache where key = ?`,
    key
  );
  if (!row) return null;
  try {
    return { data: JSON.parse(row.value) as T, updatedAt: row.updated_at };
  } catch {
    return null;
  }
}

/** Clears the entire offline cache (used on logout). */
export async function cacheClear(): Promise<void> {
  const db = await getDb();
  await db.runAsync(`delete from kv_cache`);
}
