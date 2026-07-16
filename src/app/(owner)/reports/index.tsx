/** Reports — date-range sales analysis with PDF/CSV export. */
import DateTimePicker from "@react-native-community/datetimepicker";
import { useRouter } from "expo-router";
import {
  CalendarDays,
  ChevronRight,
  FileDown,
  FileText,
  ReceiptText,
} from "lucide-react-native";
import { useMemo, useState } from "react";
import { Pressable, RefreshControl, ScrollView, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { CacheChip } from "@/components/cache-chip";
import { PaymentSplitBar } from "@/components/charts/payment-split";
import { OwnerHeader } from "@/components/owner-header";
import {
  AppText,
  Button,
  Card,
  EmptyState,
  LoadingSpinner,
  useToast,
} from "@/components/ui";
import { useCachedQuery } from "@/hooks/use-cached-query";
import { fetchReport } from "@/lib/data";
import { shareReportCsv, shareReportPdf } from "@/lib/export";
import {
  billTime,
  formatINR,
  istDayString,
  longDate,
  shiftDay,
  shortDate,
} from "@/lib/format";
import { cn } from "@/lib/cn";
import { useAuth } from "@/stores/auth";
import { useThemeColors } from "@/stores/theme";

type PresetKey = "today" | "yesterday" | "week" | "month" | "custom";

function presetRange(key: Exclude<PresetKey, "custom">): {
  from: string;
  to: string;
} {
  const today = istDayString();
  switch (key) {
    case "today":
      return { from: today, to: today };
    case "yesterday": {
      const y = shiftDay(today, -1);
      return { from: y, to: y };
    }
    case "week": {
      // Monday-start week
      const dow = new Date(`${today}T00:00:00Z`).getUTCDay(); // 0 = Sun
      const back = dow === 0 ? 6 : dow - 1;
      return { from: shiftDay(today, -back), to: today };
    }
    case "month":
      return { from: today.slice(0, 8) + "01", to: today };
  }
}

const PRESETS: Array<{ key: PresetKey; label: string }> = [
  { key: "today", label: "Today" },
  { key: "yesterday", label: "Yesterday" },
  { key: "week", label: "This week" },
  { key: "month", label: "This month" },
  { key: "custom", label: "Custom" },
];

const BILLS_SHOWN = 50;

export default function Reports() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const colors = useThemeColors();
  const toast = useToast();
  const restaurant = useAuth((s) => s.restaurant);
  const licenseKey = restaurant?.licenseKey ?? null;

  const [preset, setPreset] = useState<PresetKey>("today");
  const [custom, setCustom] = useState(() => presetRange("today"));
  const [picking, setPicking] = useState<"from" | "to" | null>(null);
  const [exporting, setExporting] = useState<"pdf" | "csv" | null>(null);

  const range = useMemo(
    () => (preset === "custom" ? custom : presetRange(preset)),
    [preset, custom]
  );

  const q = useCachedQuery(
    licenseKey ? `report.${licenseKey}.${range.from}.${range.to}` : null,
    () => fetchReport(licenseKey!, range.from, range.to)
  );

  const doExport = async (kind: "pdf" | "csv") => {
    if (!q.data) return;
    setExporting(kind);
    try {
      if (kind === "pdf") await shareReportPdf(q.data, restaurant?.name ?? "");
      else await shareReportCsv(q.data, restaurant?.name ?? "");
    } catch {
      toast.show("Export failed — try again", "error");
    } finally {
      setExporting(null);
    }
  };

  return (
    <ScrollView
      className="flex-1 bg-bg"
      refreshControl={
        <RefreshControl
          refreshing={q.refreshing && !q.loading}
          onRefresh={q.refresh}
          tintColor={colors.accentBright}
          colors={[colors.accent]}
          progressBackgroundColor={colors.surface}
        />
      }
      contentContainerStyle={{
        paddingTop: insets.top + 12,
        paddingBottom: 32,
        paddingHorizontal: 20,
      }}>
      <OwnerHeader title="Reports" />

      {/* Range presets */}
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        className="-mx-5 mb-4"
        contentContainerStyle={{ paddingHorizontal: 20, gap: 8 }}>
        {PRESETS.map((p) => {
          const active = preset === p.key;
          return (
            <Pressable
              key={p.key}
              onPress={() => {
                if (p.key === "custom") setCustom(range);
                setPreset(p.key);
              }}
              className={cn(
                "h-9 items-center justify-center rounded-full border px-4",
                active
                  ? "border-accent bg-accent-soft"
                  : "border-line-strong bg-surface"
              )}>
              <AppText
                className={cn(
                  "font-sans-medium text-sm",
                  active ? "text-accent-bright" : "text-ink-muted"
                )}>
                {p.label}
              </AppText>
            </Pressable>
          );
        })}
      </ScrollView>

      {/* Custom range row */}
      {preset === "custom" ? (
        <View className="mb-4 flex-row items-center gap-3">
          {(["from", "to"] as const).map((side) => (
            <Pressable
              key={side}
              onPress={() => setPicking(side)}
              className="flex-1 flex-row items-center gap-2 rounded-xl border border-line-strong bg-surface px-3.5 py-3">
              <CalendarDays size={16} color={colors.textMuted} />
              <View>
                <AppText variant="caption">{side === "from" ? "From" : "To"}</AppText>
                <AppText className="font-sans-semibold text-sm text-ink">
                  {shortDate(side === "from" ? custom.from : custom.to)}
                </AppText>
              </View>
            </Pressable>
          ))}
        </View>
      ) : null}
      {picking ? (
        <DateTimePicker
          value={new Date(`${picking === "from" ? custom.from : custom.to}T00:00:00`)}
          mode="date"
          maximumDate={new Date()}
          onChange={(event, date) => {
            setPicking(null);
            if (event.type !== "set" || !date) return;
            const day = istDayString(date);
            setCustom((c) => {
              const next = { ...c, [picking]: day };
              if (next.from > next.to) {
                return picking === "from"
                  ? { from: day, to: day }
                  : { from: day, to: day };
              }
              return next;
            });
          }}
        />
      ) : null}

      <CacheChip stale={q.stale} updatedAt={q.updatedAt} />

      {q.loading ? (
        <LoadingSpinner label="Crunching numbers…" />
      ) : q.error ? (
        <Card flush>
          <EmptyState icon={ReceiptText} title="Couldn't load report" message={q.error} />
        </Card>
      ) : q.data ? (
        <View className="gap-4">
          {/* Summary */}
          <Card>
            <AppText variant="caption">
              {range.from === range.to
                ? longDate(range.from)
                : `${longDate(range.from)} — ${longDate(range.to)}`}
            </AppText>
            <AppText variant="display" className="mt-1.5">
              {formatINR(q.data.total)}
            </AppText>
            <View className="mt-4 flex-row">
              <View className="flex-1">
                <AppText variant="caption">Bills</AppText>
                <AppText className="font-sans-bold text-lg text-ink">
                  {q.data.billCount}
                </AppText>
              </View>
              <View className="flex-1">
                <AppText variant="caption">Avg bill</AppText>
                <AppText className="font-sans-bold text-lg text-ink">
                  {formatINR(Math.round(q.data.avg))}
                </AppText>
              </View>
              <View className="flex-1">
                <AppText variant="caption">GST</AppText>
                <AppText className="font-sans-bold text-lg text-ink">
                  {formatINR(q.data.gst)}
                </AppText>
              </View>
            </View>
            {q.data.expenseTotal > 0 ? (
              <View className="mt-4 flex-row justify-between rounded-xl bg-surface-2 px-3.5 py-2.5">
                <AppText className="text-sm text-ink-muted">
                  Expenses {formatINR(q.data.expenseTotal)}
                </AppText>
                <AppText className="font-sans-semibold text-sm text-ink">
                  Net {formatINR(q.data.total - q.data.expenseTotal)}
                </AppText>
              </View>
            ) : null}
          </Card>

          {/* Export */}
          <View className="flex-row gap-3">
            <Button
              title="Share PDF"
              variant="secondary"
              size="sm"
              className="flex-1"
              loading={exporting === "pdf"}
              icon={<FileText size={16} color={colors.accentBright} />}
              onPress={() => doExport("pdf")}
            />
            <Button
              title="Export CSV"
              variant="secondary"
              size="sm"
              className="flex-1"
              loading={exporting === "csv"}
              icon={<FileDown size={16} color={colors.accentBright} />}
              onPress={() => doExport("csv")}
            />
          </View>

          {/* Payment split */}
          <Card>
            <AppText variant="heading" className="mb-4">
              Payment modes
            </AppText>
            <PaymentSplitBar split={q.data.split} />
          </Card>

          {/* Item-wise */}
          <Card>
            <AppText variant="heading" className="mb-3">
              Item-wise sales
            </AppText>
            {q.data.items.length === 0 ? (
              <AppText variant="caption">No items in this range.</AppText>
            ) : (
              <View>
                <View className="mb-2 flex-row border-b border-line pb-2">
                  <AppText variant="caption" className="flex-1">
                    ITEM
                  </AppText>
                  <AppText variant="caption" className="w-12 text-right">
                    QTY
                  </AppText>
                  <AppText variant="caption" className="w-24 text-right">
                    AMOUNT
                  </AppText>
                </View>
                <View className="gap-2.5">
                  {q.data.items.map((it) => (
                    <View key={it.name} className="flex-row items-center">
                      <AppText
                        className="flex-1 font-sans-medium text-sm text-ink"
                        numberOfLines={1}>
                        {it.name}
                      </AppText>
                      <AppText className="w-12 text-right text-sm text-ink-muted">
                        {it.quantity}
                      </AppText>
                      <AppText className="w-24 text-right font-sans-semibold text-sm text-ink">
                        {formatINR(it.amount)}
                      </AppText>
                    </View>
                  ))}
                </View>
              </View>
            )}
          </Card>

          {/* Bills */}
          <Card>
            <AppText variant="heading" className="mb-3">
              Bills
            </AppText>
            {q.data.bills.length === 0 ? (
              <AppText variant="caption">No bills in this range.</AppText>
            ) : (
              <View className="gap-1">
                {q.data.bills.slice(0, BILLS_SHOWN).map((b) => (
                  <Pressable
                    key={b.id}
                    onPress={() => router.push(`/reports/bill/${b.id}`)}
                    className="flex-row items-center gap-3 rounded-xl px-1 py-2.5 active:bg-surface-2">
                    <View className="h-9 w-9 items-center justify-center rounded-lg bg-surface-2">
                      <ReceiptText size={16} color={colors.textMuted} />
                    </View>
                    <View className="flex-1">
                      <AppText className="font-sans-medium text-sm text-ink">
                        {b.bill_number ? `Bill #${b.bill_number}` : "Bill"}
                        {b.table_number ? ` · Table ${b.table_number}` : ""}
                      </AppText>
                      <AppText variant="caption">
                        {billTime(b.billed_at)}
                        {b.payment_mode ? ` · ${b.payment_mode}` : ""}
                      </AppText>
                    </View>
                    <AppText className="font-sans-semibold text-sm text-ink">
                      {formatINR(b.total ?? 0)}
                    </AppText>
                    <ChevronRight size={16} color={colors.textFaint} />
                  </Pressable>
                ))}
                {q.data.bills.length > BILLS_SHOWN ? (
                  <AppText variant="caption" className="mt-2 text-center">
                    Showing {BILLS_SHOWN} of {q.data.bills.length} bills — narrow
                    the range to see more.
                  </AppText>
                ) : null}
              </View>
            )}
          </Card>
        </View>
      ) : null}
    </ScrollView>
  );
}
