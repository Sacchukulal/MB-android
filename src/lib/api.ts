/** Thin caller for Supabase Edge Functions (anon key only — never service role). */

const BASE_URL = process.env.EXPO_PUBLIC_SUPABASE_URL!;
const ANON_KEY = process.env.EXPO_PUBLIC_SUPABASE_ANON_KEY!;

export async function callFunction<T>(
  name: string,
  body: unknown,
  opts?: { token?: string }
): Promise<T> {
  const res = await fetch(`${BASE_URL}/functions/v1/${name}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      apikey: ANON_KEY,
      // Owner-authenticated calls pass the user's access token; public calls
      // (staff login/verify) use the anon key.
      Authorization: `Bearer ${opts?.token ?? ANON_KEY}`,
    },
    body: JSON.stringify(body),
  });
  return (await res.json()) as T;
}
