/**
 * Staff home — today's picture, shaped entirely by owner-set permissions.
 * Without view_revenue_totals: counts and percentages, never rupee amounts.
 */
import { LayoutDashboard, Receipt, Sparkles, TrendingUp } from "lucide-react-native";
import { RefreshControl, ScrollView, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { CacheChip } from "@/components/cache-chip";
import { PaymentSplitBar } from "@/components/charts/payment-split";
import { TrendBars } from "@/components/charts/trend-bars";
import {
  AppText,
  Avatar,
  Badge,
  Card,
  EmptyState,
  LoadingSpinner,
  PermissionGate,
} from "@/components/ui";
import { useCachedQuery } from "@/hooks/use-cached-query";
import { formatINR, formatShortINR } from "@/lib/format";
import { fetchStaffDashboard } from "@/lib/staff-data";
import { useAuth, useCan } from "@/stores/auth";
import { useThemeColors } from "@/stores/theme";

const MASK = "••••";

export default function StaffHome() {
  const insets = useSafeAreaInsets();
  const colors = useThemeColors();
  const staff = useAuth((s) => s.staff);
  const restaurant = useAuth((s) => s.restaurant);
  const canDashboard = useCan("view_dashboard");
  const canOrders = useCan("take_orders");

  const q = useCachedQuery(
    canDashboard && staff ? `staffdash.${staff.id}` : null,
    fetchStaffDashboard
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
      {/* Greeting */}
      <View className="mb-5 flex-row items-center gap-3">
        <Avatar name={staff?.name ?? "?"} />
        <View className="flex-1">
          <AppText variant="heading">Hi, {staff?.name ?? "there"}</AppText>
          <View className="mt-0.5 flex-row items-center gap-2">
            <Badge label={staff?.roleLabel ?? "Staff"} tone="accent" />
            <AppText variant="caption" numberOfLines={1}>
              {restaurant?.name ?? ""}
            </AppText>
          </View>
        </View>
      </View>

      <CacheChip stale={q.stale} updatedAt={q.updatedAt} />

      {!canDashboard ? (
        <View className="gap-4">
          <Card className="items-center py-10">
            <LayoutDashboard size={28} color={colors.textMuted} />
            <AppText variant="heading" className="mt-3 text-center">
              You're all set!
            </AppText>
            <AppText className="mt-2 text-center text-sm text-ink-muted">
              You have access to this app. Your manager will enable features
              for you as needed.
            </AppText>
          </Card>
          {canOrders ? <OrdersTeaser /> : null}
        </View>
      ) : q.loading ? (
        <LoadingSpinner label="Loading today's summary…" />
      ) : q.error ? (
        <Card flush>
          <EmptyState icon={Receipt} title="Couldn't load data" message={q.error} />
        </Card>
      ) : q.data ? (
        <View className="gap-4">
          {/* Today */}
          <Card>
            <AppText variant="label">Today</AppText>
            <View className="mt-3 flex-row">
              <View className="flex-1">
                <AppText variant="caption">Bills</AppText>
                <AppText className="font-sans-extrabold text-3xl text-ink">
                  {q.data.billCount}
                </AppText>
              </View>
              <View className="flex-1">
                <AppText variant="caption">Revenue</AppText>
                <AppText className="font-sans-extrabold text-3xl text-ink">
                  {q.data.total != null ? formatINR(q.data.total) : MASK}
                </AppText>
              </View>
            </View>
            {q.data.avg != null ? (
              <View className="mt-3 flex-row items-center gap-1.5">
                <TrendingUp size={13} color={colors.textMuted} />
                <AppText variant="caption">
                  avg {formatINR(Math.round(q.data.avg))} per bill
                </AppText>
              </View>
            ) : null}
          </Card>

          {/* Payment split */}
          <Card>
            <AppText variant="heading" className="mb-4">
              Payment split
            </AppText>
            {q.data.split.kind === "amounts" ? (
              <PaymentSplitBar split={q.data.split} />
            ) : (
              <PaymentSplitBar pct={q.data.split} />
            )}
          </Card>

          {/* Trend */}
          <Card>
            <AppText variant="heading" className="mb-3">
              {q.data.trendIsRelative ? "Busy days (relative)" : "Revenue trend"}
            </AppText>
            <TrendBars
              points={q.data.trend.map((t) => ({ day: t.day, total: t.value }))}
              formatValue={
                q.data.trendIsRelative ? (v) => `${Math.round(v)}%` : formatShortINR
              }
            />
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
                      <AppText variant="caption">{it.quantity} sold</AppText>
                    </View>
                    {it.amount != null ? (
                      <AppText className="font-sans-semibold text-sm text-ink">
                        {formatINR(it.amount)}
                      </AppText>
                    ) : null}
                  </View>
                ))}
              </View>
            )}
          </Card>

          <PermissionGate permission="take_orders">
            <OrdersTeaser />
          </PermissionGate>
        </View>
      ) : null}
    </ScrollView>
  );
}

function OrdersTeaser() {
  const colors = useThemeColors();
  return (
    <Card raised className="items-center py-8">
      <Sparkles size={28} color={colors.accentBright} />
      <AppText variant="heading" className="mt-3">
        Mobile ordering is coming soon!
      </AppText>
      <AppText variant="caption" className="mt-1 text-center">
        You'll be able to take orders directly from this app.
      </AppText>
    </Card>
  );
}
