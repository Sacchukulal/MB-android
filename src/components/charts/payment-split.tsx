/**
 * Payment-mode split — segmented horizontal bar + legend.
 * Identity is never color-alone: every mode gets a labeled legend row with
 * amount and share; segments keep 2px surface gaps.
 */
import { View } from "react-native";

import { AppText } from "@/components/ui";
import type { PaymentSplit } from "@/lib/data";
import { formatINR } from "@/lib/format";
import { useThemeColors } from "@/stores/theme";

export function PaymentSplitBar({
  split,
  hideAmounts = false,
}: {
  split: PaymentSplit;
  /** For staff without view_revenue_totals: show % only. */
  hideAmounts?: boolean;
}) {
  const colors = useThemeColors();
  const rows = [
    { label: "Cash", value: split.cash, color: colors.chart.cash },
    { label: "UPI", value: split.upi, color: colors.chart.upi },
    { label: "Card", value: split.card, color: colors.chart.card },
    { label: "Credit", value: split.credit, color: colors.chart.credit },
  ];
  const total = rows.reduce((s, r) => s + r.value, 0);
  const active = rows.filter((r) => r.value > 0);

  return (
    <View>
      {total > 0 ? (
        <View className="mb-3 h-3 flex-row gap-[2px] overflow-hidden rounded-full">
          {active.map((r) => (
            <View
              key={r.label}
              style={{
                flex: r.value / total,
                backgroundColor: r.color,
              }}
            />
          ))}
        </View>
      ) : (
        <View className="mb-3 h-3 rounded-full bg-surface-2" />
      )}

      <View className="gap-2">
        {rows.map((r) => {
          const pct = total > 0 ? Math.round((r.value / total) * 100) : 0;
          return (
            <View key={r.label} className="flex-row items-center gap-2.5">
              <View
                className="h-2.5 w-2.5 rounded-full"
                style={{ backgroundColor: r.color }}
              />
              <AppText className="w-14 font-sans-medium text-sm text-ink-muted">
                {r.label}
              </AppText>
              <AppText className="flex-1 font-sans-semibold text-sm text-ink">
                {hideAmounts ? "••••" : formatINR(r.value)}
              </AppText>
              <AppText variant="caption">{pct}%</AppText>
            </View>
          );
        })}
      </View>
    </View>
  );
}
