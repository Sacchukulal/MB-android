/**
 * Owner data fetchers (RLS-scoped reads via supabase-js).
 * - "Today" is always computed live from `bills` (daily_summaries freshness
 *   depends on external refresh calls; bills sync continuously).
 * - History (yesterday and older) comes from `daily_summaries`.
 * Staff data access does NOT use these — it goes through Edge Functions
 * (Phase E) so the license key never reaches staff clients.
 */
import {
  istDayEndUtc,
  istDayStartUtc,
  istDayString,
  shiftDay,
} from "@/lib/format";
import { supabase } from "@/lib/supabase";

export interface BillItem {
  id?: number;
  name: string;
  price: number;
  quantity: number;
  category_id?: number;
}

export interface BillRow {
  id: string;
  bill_number: string | null;
  token_number: number | null;
  order_type: string | null;
  table_number: string | null;
  payment_mode: string | null;
  subtotal: number | null;
  gst: number | null;
  total: number | null;
  items: BillItem[] | null;
  billed_at: string;
}

export interface BillDetail extends BillRow {
  customer_name: string | null;
  customer_phone: string | null;
}

export interface DaySummary {
  day: string;
  bill_count: number;
  subtotal: number;
  gst: number;
  total: number;
  cash_total: number;
  card_total: number;
  upi_total: number;
  credit_total: number;
  expense_total: number;
}

export interface PaymentSplit {
  cash: number;
  card: number;
  upi: number;
  credit: number;
}

export interface ItemAgg {
  name: string;
  quantity: number;
  amount: number;
}

export interface DashboardData {
  today: {
    day: string;
    total: number;
    gst: number;
    billCount: number;
    avg: number;
    split: PaymentSplit;
  };
  yesterdayTotal: number | null;
  /** Oldest-first daily totals for the trend chart (includes today, live). */
  trend: Array<{ day: string; total: number }>;
  topItems: ItemAgg[];
}

export interface ReportData {
  fromDay: string;
  toDay: string;
  total: number;
  subtotal: number;
  gst: number;
  billCount: number;
  avg: number;
  split: PaymentSplit;
  items: ItemAgg[];
  expenseTotal: number;
  bills: BillRow[];
}

const BILL_COLUMNS =
  "id, bill_number, token_number, order_type, table_number, payment_mode, subtotal, gst, total, items, billed_at";

function aggregate(bills: BillRow[]) {
  const split: PaymentSplit = { cash: 0, card: 0, upi: 0, credit: 0 };
  const itemMap = new Map<string, ItemAgg>();
  let total = 0;
  let subtotal = 0;
  let gst = 0;

  for (const b of bills) {
    total += b.total ?? 0;
    subtotal += b.subtotal ?? 0;
    gst += b.gst ?? 0;
    const mode = (b.payment_mode ?? "").toLowerCase();
    if (mode === "cash") split.cash += b.total ?? 0;
    else if (mode === "card") split.card += b.total ?? 0;
    else if (mode === "upi") split.upi += b.total ?? 0;
    else if (mode === "credit") split.credit += b.total ?? 0;

    for (const it of b.items ?? []) {
      if (!it?.name) continue;
      const key = it.name.trim().toLowerCase();
      const prev = itemMap.get(key);
      const qty = Number(it.quantity) || 0;
      const amount = (Number(it.price) || 0) * qty;
      if (prev) {
        prev.quantity += qty;
        prev.amount += amount;
      } else {
        itemMap.set(key, { name: it.name.trim(), quantity: qty, amount });
      }
    }
  }

  const items = [...itemMap.values()].sort((a, b) => b.amount - a.amount);
  return { total, subtotal, gst, split, items };
}

async function fetchBillsInRange(
  licenseKey: string,
  fromDay: string,
  toDay: string
): Promise<BillRow[]> {
  const { data, error } = await supabase
    .from("bills")
    .select(BILL_COLUMNS)
    .eq("license_key", licenseKey)
    .gte("billed_at", istDayStartUtc(fromDay).toISOString())
    .lt("billed_at", istDayEndUtc(toDay).toISOString())
    .order("billed_at", { ascending: false })
    .limit(2000);
  if (error) throw error;
  return (data ?? []) as unknown as BillRow[];
}

export async function fetchDashboard(licenseKey: string): Promise<DashboardData> {
  const today = istDayString();
  const trendStart = shiftDay(today, -13);

  const [todayBills, summariesRes] = await Promise.all([
    fetchBillsInRange(licenseKey, today, today),
    supabase
      .from("daily_summaries")
      .select("day, total")
      .eq("license_key", licenseKey)
      .gte("day", trendStart)
      .lt("day", today)
      .order("day", { ascending: true }),
  ]);
  if (summariesRes.error) throw summariesRes.error;

  const agg = aggregate(todayBills);
  const summaryByDay = new Map(
    (summariesRes.data ?? []).map((s) => [s.day as string, Number(s.total) || 0])
  );

  const trend: Array<{ day: string; total: number }> = [];
  for (let i = 13; i >= 1; i--) {
    const day = shiftDay(today, -i);
    trend.push({ day, total: summaryByDay.get(day) ?? 0 });
  }
  trend.push({ day: today, total: agg.total });

  return {
    today: {
      day: today,
      total: agg.total,
      gst: agg.gst,
      billCount: todayBills.length,
      avg: todayBills.length ? agg.total / todayBills.length : 0,
      split: agg.split,
    },
    yesterdayTotal: summaryByDay.get(shiftDay(today, -1)) ?? null,
    trend,
    topItems: agg.items.slice(0, 5),
  };
}

export async function fetchReport(
  licenseKey: string,
  fromDay: string,
  toDay: string
): Promise<ReportData> {
  const [bills, expensesRes] = await Promise.all([
    fetchBillsInRange(licenseKey, fromDay, toDay),
    supabase
      .from("expenses")
      .select("amount")
      .eq("license_key", licenseKey)
      .gte("spent_at", istDayStartUtc(fromDay).toISOString())
      .lt("spent_at", istDayEndUtc(toDay).toISOString()),
  ]);
  if (expensesRes.error) throw expensesRes.error;

  const agg = aggregate(bills);
  const expenseTotal = (expensesRes.data ?? []).reduce(
    (sum, e) => sum + (Number(e.amount) || 0),
    0
  );

  return {
    fromDay,
    toDay,
    total: agg.total,
    subtotal: agg.subtotal,
    gst: agg.gst,
    billCount: bills.length,
    avg: bills.length ? agg.total / bills.length : 0,
    split: agg.split,
    items: agg.items,
    expenseTotal,
    bills,
  };
}

/** Full bill (incl. customer fields) for the receipt view. */
export async function fetchBill(billId: string): Promise<BillDetail | null> {
  const { data, error } = await supabase
    .from("bills")
    .select(`${BILL_COLUMNS}, customer_name, customer_phone`)
    .eq("id", billId)
    .maybeSingle();
  if (error) throw error;
  return (data as unknown as BillDetail) ?? null;
}
