/** Owner dashboard — today's numbers, payment split, 14-day trend, top items. */
import { IndianRupee, Receipt, ReceiptText, TrendingUp } from "lucide-react-native";
import { RefreshControl, ScrollView, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { CacheChip } from "@/components/cache-chip";
import { PaymentSplitBar } from "@/components/charts/payment-split";
import { TrendBars } from "@/components/charts/trend-bars";
import { OwnerHeader } from "@/components/owner-header";
import {
  AppText,
  Card,
  EmptyState,
  LoadingSpinner,
} from "@/components/ui";
import { useCachedQuery } from "@/hooks/use-cached-query";
import { fetchDashboard } from "@/lib/data";
import { formatINR } from "@/lib/format";
import { useAuth } from "@/stores/auth";
import { useThemeColors } from "@/stores/theme";

function DeltaCaption({ today, yesterday }: { today: number; yesterday: number | null }) {
  if (yesterday == null || yesterday === 0) {
    return <AppText variant="caption">vs yesterday: —</AppText>;
  }
  const pct = Math.round(((today - yesterday) / yesterday) * 100);
  const up = pct >= 0;
  return (
    <AppText
      variant="caption"
      className={up ? "text-success" : "text-danger"}>
      {up ? "▲" : "▼"} {Math.abs(pct)}% vs yesterday
    </AppText>
  );
}

export default function OwnerDashboard() {
  const insets = useSafeAreaInsets();
  const colors = useThemeColors();
  const restaurant = useAuth((s) => s.restaurant);
  const licenseKey = restaurant?.licenseKey ?? null;

  const q = useCachedQuery(
    licenseKey ? `dashboard.${licenseKey}` : null,
    () => fetchDashboard(licenseKey!)
  );

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
      <OwnerHeader title="Today at a glance" />
      <CacheChip stale={q.stale} updatedAt={q.updatedAt} />

      {q.loading ? (
        <LoadingSpinner label="Loading today's sales…" />
      ) : q.error ? (
        <Card flush>
          <EmptyState
            icon={ReceiptText}
            title="Couldn't load data"
            message={q.error}
          />
        </Card>
      ) : q.data ? (
        <View className="gap-4">
          {/* Hero: today's revenue */}
          <Card>
            <View className="flex-row items-center gap-2">
              <View className="h-9 w-9 items-center justify-center rounded-lg bg-accent-soft">
                <IndianRupee size={18} color={colors.accentBright} />
              </View>
              <AppText variant="label">Today's revenue</AppText>
            </View>
            <AppText variant="display" className="mt-2">
              {formatINR(q.data.today.total)}
            </AppText>
            <View className="mt-1">
              <DeltaCaption
                today={q.data.today.total}
                yesterday={q.data.yesterdayTotal}
              />
            </View>
          </Card>

          {/* Bills + avg */}
          <View className="flex-row gap-4">
            <Card className="flex-1">
              <View className="mb-1.5 h-8 w-8 items-center justify-center rounded-lg bg-info/15">
                <Receipt size={16} color={colors.info} />
              </View>
              <AppText variant="caption">Bills</AppText>
              <AppText className="font-sans-extrabold text-2xl text-ink">
                {q.data.today.billCount}
              </AppText>
            </Card>
            <Card className="flex-1">
              <View className="mb-1.5 h-8 w-8 items-center justify-center rounded-lg bg-accent-soft">
                <TrendingUp size={16} color={colors.accentBright} />
              </View>
              <AppText variant="caption">Avg bill</AppText>
              <AppText className="font-sans-extrabold text-2xl text-ink">
                {formatINR(Math.round(q.data.today.avg))}
              </AppText>
            </Card>
          </View>

          {/* Payment split */}
          <Card>
            <AppText variant="heading" className="mb-4">
              Payment split
            </AppText>
            <PaymentSplitBar split={q.data.today.split} />
          </Card>

          {/* Trend */}
          <Card>
            <AppText variant="heading" className="mb-3">
              Revenue trend
            </AppText>
            <TrendBars points={q.data.trend} />
          </Card>

          {/* Top items */}
          <Card>
            <AppText variant="heading" className="mb-3">
              Top items today
            </AppText>
            {q.data.topItems.length === 0 ? (
              <AppText variant="caption">No items billed yet today.</AppText>
            ) : (
              <View className="gap-3">
                {q.data.topItems.map((it, i) => (
                  <View key={it.name} className="flex-row items-center gap-3">
                    <AppText className="w-5 font-sans-bold text-sm text-ink-faint">
                      {i + 1}
                    </AppText>
                    <View className="flex-1">
                      <AppText
                        className="font-sans-medium text-sm text-ink"
                        numberOfLines={1}>
                        {it.name}
                      </AppText>
                      <AppText variant="caption">
                        {it.quantity} sold
                      </AppText>
                    </View>
                    <AppText className="font-sans-semibold text-sm text-ink">
                      {formatINR(it.amount)}
                    </AppText>
                  </View>
                ))}
              </View>
            )}
          </Card>
        </View>
      ) : null}
    </ScrollView>
  );
}
