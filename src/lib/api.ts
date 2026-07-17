/** Thin caller for Supabase Edge Functions (anon key only — never service role). */

const BASE_URL = process.env.EXPO_PUBLIC_SUPABASE_URL!;
const ANON_KEY = process.env.EXPO_PUBLIC_SUPABASE_ANON_KEY!;

/** Default network budget — fail fast so offline/flaky UX stays snappy. */
const TIMEOUT_MS = 8000;

/** fetch with a hard timeout (poor connections otherwise hang for 30-60s). */
export function timeoutFetch(
  input: RequestInfo | URL,
  init?: RequestInit,
  timeoutMs: number = TIMEOUT_MS
): Promise<Response> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  return fetch(input, { ...init, signal: controller.signal }).finally(() =>
    clearTimeout(timer)
  );
}

export async function callFunction<T>(
  name: string,
  body: unknown,
  opts?: { token?: string; timeoutMs?: number }
): Promise<T> {
  const res = await timeoutFetch(
    `${BASE_URL}/functions/v1/${name}`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        apikey: ANON_KEY,
        // Owner-authenticated calls pass the user's access token; public
        // calls (staff login/verify) use the anon key.
        Authorization: `Bearer ${opts?.token ?? ANON_KEY}`,
      },
      body: JSON.stringify(body),
    },
    opts?.timeoutMs ?? TIMEOUT_MS
  );
  return (await res.json()) as T;
}
