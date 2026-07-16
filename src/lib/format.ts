/** Formatting + IST date helpers (POS day boundary = Asia/Kolkata). */

const IST_OFFSET_MS = 5.5 * 60 * 60 * 1000; // no DST in India

/** ₹ with Indian digit grouping: 123456.7 -> "₹1,23,456.70" */
export function formatINR(value: number, opts?: { decimals?: number }): string {
  const decimals = opts?.decimals ?? (Number.isInteger(value) ? 0 : 2);
  const neg = value < 0;
  const [intPart, fracPart] = Math.abs(value).toFixed(decimals).split(".");
  // Indian grouping: last 3 digits, then groups of 2
  const last3 = intPart.slice(-3);
  const rest = intPart.slice(0, -3);
  const grouped = rest
    ? rest.replace(/\B(?=(\d{2})+(?!\d))/g, ",") + "," + last3
    : last3;
  return `${neg ? "-" : ""}₹${grouped}${fracPart ? "." + fracPart : ""}`;
}

/** Short form for chart labels: 1234 -> "1.2k", 245000 -> "2.4L" */
export function formatShortINR(value: number): string {
  const abs = Math.abs(value);
  if (abs >= 1e7) return `₹${(value / 1e7).toFixed(1)}Cr`;
  if (abs >= 1e5) return `₹${(value / 1e5).toFixed(1)}L`;
  if (abs >= 1e3) return `₹${(value / 1e3).toFixed(1)}k`;
  return `₹${Math.round(value)}`;
}

/** "3 min ago" / "2 hr ago" / "yesterday" for the cache-age chip. */
export function timeAgo(epochMs: number): string {
  const s = Math.max(0, Math.floor((Date.now() - epochMs) / 1000));
  if (s < 60) return "just now";
  const m = Math.floor(s / 60);
  if (m < 60) return `${m} min ago`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h} hr ago`;
  const d = Math.floor(h / 24);
  return d === 1 ? "yesterday" : `${d} days ago`;
}

/** The IST calendar date ("YYYY-MM-DD") for a given instant. */
export function istDayString(date: Date = new Date()): string {
  return new Date(date.getTime() + IST_OFFSET_MS).toISOString().slice(0, 10);
}

/** UTC instant at which the given IST day ("YYYY-MM-DD") starts. */
export function istDayStartUtc(day: string): Date {
  return new Date(new Date(`${day}T00:00:00.000Z`).getTime() - IST_OFFSET_MS);
}

/** UTC instant at which the given IST day ends (exclusive). */
export function istDayEndUtc(day: string): Date {
  return new Date(istDayStartUtc(day).getTime() + 24 * 60 * 60 * 1000);
}

/** Shift an IST day string by n days. */
export function shiftDay(day: string, n: number): string {
  const d = new Date(`${day}T00:00:00.000Z`);
  d.setUTCDate(d.getUTCDate() + n);
  return d.toISOString().slice(0, 10);
}

/** "2026-07-16" -> "16 Jul" (labels) */
export function shortDate(day: string): string {
  const d = new Date(`${day}T00:00:00.000Z`);
  return d.toLocaleDateString("en-IN", {
    day: "numeric",
    month: "short",
    timeZone: "UTC",
  });
}

/** "2026-07-16" -> "Wed 16 Jul 2026" (headers) */
export function longDate(day: string): string {
  const d = new Date(`${day}T00:00:00.000Z`);
  return d.toLocaleDateString("en-IN", {
    weekday: "short",
    day: "numeric",
    month: "short",
    year: "numeric",
    timeZone: "UTC",
  });
}

/** Timestamp -> "16 Jul, 2:35 pm" in IST (bill rows, receipts). */
export function billTime(iso: string): string {
  const d = new Date(iso);
  return d.toLocaleString("en-IN", {
    day: "numeric",
    month: "short",
    hour: "numeric",
    minute: "2-digit",
    hour12: true,
    timeZone: "Asia/Kolkata",
  });
}
