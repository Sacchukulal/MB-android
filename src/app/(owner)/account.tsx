/** Account — restaurant info, subscription status, device lock, settings. */
import {
  ArrowLeftRight,
  Check,
  ExternalLink,
  KeyRound,
  Mail,
  MonitorSmartphone,
  Moon,
  Phone,
  RefreshCw,
  Store,
  Sun,
} from "lucide-react-native";
import { useEffect, useRef, useState } from "react";
import {
  AppState,
  Pressable,
  RefreshControl,
  ScrollView,
  Switch,
  View,
} from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { CacheChip } from "@/components/cache-chip";
import { OwnerHeader } from "@/components/owner-header";
import {
  AppText,
  Badge,
  Button,
  Card,
  EmptyState,
  LoadingSpinner,
  useToast,
} from "@/components/ui";
import { useCachedQuery } from "@/hooks/use-cached-query";
import {
  daysUntil,
  fetchAccount,
  fetchVisiblePlans,
  statusTone,
  type VisiblePlan,
} from "@/lib/account";
import { openBillingPortal, type BillingDestination } from "@/lib/billing";
import { formatINR, longDate, timeAgo } from "@/lib/format";
import { installedVersion, useUpdate } from "@/lib/update";
import { useAuth } from "@/stores/auth";
import { useTheme, useThemeColors } from "@/stores/theme";

/** Statuses that mean "payments are lapsed — offer resubscribe + plans". */
const LAPSED = ["cancelled", "expired", "halted", "completed", "revoked", "suspended"];
const ACTIVE_LIKE = ["active", "trial", "grace"];

function InfoRow({
  icon,
  label,
  value,
  mono = false,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
  mono?: boolean;
}) {
  return (
    <View className="flex-row items-center gap-3 py-1.5">
      {icon}
      <AppText variant="caption" className="w-20">
        {label}
      </AppText>
      <AppText
        className={
          mono
            ? "flex-1 font-mono text-sm text-ink"
            : "flex-1 font-sans-medium text-sm text-ink"
        }
        numberOfLines={1}>
        {value}
      </AppText>
    </View>
  );
}

export default function AccountTab() {
  const insets = useSafeAreaInsets();
  const colors = useThemeColors();
  const toast = useToast();
  const restaurant = useAuth((s) => s.restaurant);
  const licenseKey = restaurant?.licenseKey ?? null;
  const mode = useTheme((s) => s.mode);
  const toggleTheme = useTheme((s) => s.toggle);
  const checkUpdate = useUpdate((s) => s.check);
  const [checkingUpdate, setCheckingUpdate] = useState(false);

  const q = useCachedQuery(
    licenseKey ? `account.${licenseKey}` : null,
    () => fetchAccount(licenseKey!)
  );
  const plansQ = useCachedQuery("plans.visible", fetchVisiblePlans);

  const [showPlans, setShowPlans] = useState(false);
  const [refreshingPlan, setRefreshingPlan] = useState(false);
  const qRefresh = useRef(q.refresh);
  qRefresh.current = q.refresh;

  /** Open billing in a Chrome Custom Tab; on return, re-fetch the license. */
  const goToBilling = async (destination: BillingDestination) => {
    await openBillingPortal(destination);
    setRefreshingPlan(true);
    try {
      await qRefresh.current();
    } finally {
      setRefreshingPlan(false);
    }
  };

  // Safety net: some payment flows background the app (UPI apps) — refetch
  // whenever we come back to the foreground so the new status shows fast.
  useEffect(() => {
    const sub = AppState.addEventListener("change", (state) => {
      if (state === "active") qRefresh.current();
    });
    return () => sub.remove();
  }, []);

  const doCheckUpdate = async () => {
    setCheckingUpdate(true);
    const result = await checkUpdate();
    setCheckingUpdate(false);
    if (result === "up-to-date") {
      toast.show(`You're on the latest version (v${installedVersion()})`, "success");
    } else if (result === "error") {
      toast.show("Couldn't check for updates", "error");
    }
    // when an update exists, the global UpdatePrompt modal takes over
  };

  const lic = q.data?.license ?? null;
  const plan = q.data?.plan ?? null;
  const days = daysUntil(lic?.next_billing_date ?? null);

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
      <OwnerHeader title="Account" showLogout />
      <CacheChip stale={q.stale} updatedAt={q.updatedAt} />

      {q.loading ? (
        <LoadingSpinner label="Loading account…" />
      ) : q.error || !lic ? (
        <Card flush>
          <EmptyState
            icon={Store}
            title="Couldn't load account"
            message={q.error ?? "Try again"}
          />
        </Card>
      ) : (
        <View className="gap-4">
          {/* Restaurant */}
          <Card>
            <AppText variant="heading" className="mb-3">
              Restaurant
            </AppText>
            <InfoRow
              icon={<Store size={15} color={colors.textMuted} />}
              label="Name"
              value={lic.restaurant_name || "—"}
            />
            <InfoRow
              icon={<Mail size={15} color={colors.textMuted} />}
              label="Email"
              value={lic.email || "—"}
            />
            <InfoRow
              icon={<Phone size={15} color={colors.textMuted} />}
              label="Mobile"
              value={lic.mobile_number || "—"}
            />
            <InfoRow
              icon={<KeyRound size={15} color={colors.textMuted} />}
              label="License"
              value={lic.key}
              mono
            />
            {lic.restaurant_code ? (
              <InfoRow
                icon={<KeyRound size={15} color={colors.accentBright} />}
                label="Staff code"
                value={lic.restaurant_code}
                mono
              />
            ) : null}
          </Card>

          {/* Subscription */}
          <Card>
            <View className="mb-3 flex-row items-center justify-between">
              <AppText variant="heading">Subscription</AppText>
              <Badge
                label={(lic.status || "unknown").toUpperCase()}
                tone={statusTone(lic.status)}
              />
            </View>
            <AppText className="font-sans-bold text-xl text-ink">
              {plan?.name ?? lic.plan_id ?? "Custom plan"}
              {plan ? (
                <AppText className="font-sans text-base text-ink-muted">
                  {"  "}
                  {formatINR(plan.amount_paise / 100)}/{plan.interval_unit}
                </AppText>
              ) : null}
            </AppText>
            <View className="mt-2 flex-row items-center gap-2">
              <AppText variant="caption">
                Next billing:{" "}
                {lic.next_billing_date
                  ? longDate(lic.next_billing_date.slice(0, 10))
                  : "—"}
              </AppText>
              {days != null && days >= 0 ? (
                <Badge
                  label={`${days} days left`}
                  tone={days <= 5 ? "warning" : "neutral"}
                />
              ) : days != null ? (
                <Badge label="Overdue" tone="danger" />
              ) : null}
            </View>
            {refreshingPlan ? (
              <View className="mt-4 flex-row items-center justify-center gap-2 rounded-xl bg-accent-soft px-3 py-2.5">
                <RefreshCw size={14} color={colors.accentBright} />
                <AppText className="font-sans-medium text-xs text-accent-bright">
                  Refreshing your plan status…
                </AppText>
              </View>
            ) : null}

            {/* Context-aware billing actions (Chrome Custom Tab + handoff) */}
            {(() => {
              const s = (lic.status || "").toLowerCase();
              if (ACTIVE_LIKE.includes(s)) {
                return (
                  <View className="mt-4 gap-2.5">
                    <Button
                      title="Manage Subscription"
                      variant="secondary"
                      size="sm"
                      icon={<ExternalLink size={14} color={colors.accentBright} />}
                      onPress={() => goToBilling("/dashboard/billing")}
                    />
                    <Button
                      title={showPlans ? "Hide other plans" : "Switch Plan"}
                      variant="ghost"
                      size="sm"
                      icon={<ArrowLeftRight size={14} color={colors.accentBright} />}
                      onPress={() => setShowPlans((v) => !v)}
                    />
                  </View>
                );
              }
              if (LAPSED.includes(s)) {
                return (
                  <Button
                    title="Resubscribe"
                    size="sm"
                    className="mt-4"
                    onPress={() => goToBilling("/dashboard/billing")}
                  />
                );
              }
              return (
                <Button
                  title="Subscribe Now"
                  size="sm"
                  className="mt-4"
                  onPress={() => goToBilling("/pricing")}
                />
              );
            })()}
            <AppText variant="caption" className="mt-2 text-center">
              Opens magicbill.in in a secure tab — you're signed in
              automatically, and payment happens on the website.
            </AppText>
          </Card>

          {/* Plan options: always for lapsed accounts, on demand when active */}
          {(showPlans ||
            LAPSED.includes((lic.status || "").toLowerCase())) &&
          plansQ.data &&
          plansQ.data.length > 0 ? (
            <Card>
              <AppText variant="heading" className="mb-1">
                {LAPSED.includes((lic.status || "").toLowerCase())
                  ? "Pick a plan to get going again"
                  : "Available plans"}
              </AppText>
              <AppText variant="caption" className="mb-4">
                Choosing a plan opens secure checkout on magicbill.in.
              </AppText>
              <View className="gap-3">
                {plansQ.data.map((p: VisiblePlan) => {
                  const current = p.id === lic.plan_id;
                  const features = Array.isArray(p.features) ? p.features : [];
                  return (
                    <Pressable
                      key={p.id}
                      disabled={current}
                      onPress={() => goToBilling("/dashboard/billing")}
                      className={
                        current
                          ? "rounded-xl border border-accent bg-accent-soft p-4"
                          : "rounded-xl border border-line-strong bg-surface-2 p-4 active:border-accent"
                      }>
                      <View className="flex-row items-center justify-between">
                        <AppText className="font-sans-bold text-base text-ink">
                          {p.name}
                        </AppText>
                        {current ? (
                          <Badge label="Current" tone="accent" />
                        ) : (
                          <AppText className="font-sans-semibold text-sm text-accent-bright">
                            {formatINR(p.amount_paise / 100)}/{p.interval_unit}
                          </AppText>
                        )}
                      </View>
                      {p.description ? (
                        <AppText variant="caption" className="mt-1">
                          {p.description}
                        </AppText>
                      ) : null}
                      {features.length > 0 ? (
                        <View className="mt-2.5 gap-1.5">
                          {features.slice(0, 4).map((f) => (
                            <View key={f} className="flex-row items-center gap-2">
                              <Check size={13} color={colors.success} />
                              <AppText className="flex-1 text-xs text-ink-muted">
                                {f}
                              </AppText>
                            </View>
                          ))}
                        </View>
                      ) : null}
                    </Pressable>
                  );
                })}
              </View>
            </Card>
          ) : null}

          {/* Bound device */}
          <Card>
            <View className="mb-3 flex-row items-center justify-between">
              <AppText variant="heading">Billing PC</AppText>
              <MonitorSmartphone size={18} color={colors.accentBright} />
            </View>
            {lic.device_id ? (
              <>
                <InfoRow
                  icon={<MonitorSmartphone size={15} color={colors.textMuted} />}
                  label="Device"
                  value={lic.device_name || "Unnamed PC"}
                />
                <InfoRow
                  icon={<KeyRound size={15} color={colors.textMuted} />}
                  label="HWID"
                  value={`${lic.device_id.slice(0, 10)}…`}
                  mono
                />
                <InfoRow
                  icon={<RefreshCw size={15} color={colors.textMuted} />}
                  label="Last seen"
                  value={
                    lic.device_last_seen
                      ? timeAgo(new Date(lic.device_last_seen).getTime())
                      : "—"
                  }
                />
              </>
            ) : (
              <AppText variant="caption">
                No desktop PC is bound to this license yet.
              </AppText>
            )}
          </Card>

          {/* Settings */}
          <Card flush>
            <View className="flex-row items-center gap-3 px-4 py-3.5">
              {mode === "dark" ? (
                <Moon size={18} color={colors.indigo} />
              ) : (
                <Sun size={18} color={colors.warning} />
              )}
              <AppText className="flex-1 font-sans-medium text-sm text-ink">
                Dark theme
              </AppText>
              <Switch
                value={mode === "dark"}
                onValueChange={toggleTheme}
                trackColor={{ false: colors.surface2, true: colors.accentDeep }}
                thumbColor={mode === "dark" ? colors.accentBright : colors.textFaint}
              />
            </View>
            <View className="mx-4 border-t border-line" />
            <Pressable
              onPress={doCheckUpdate}
              className="flex-row items-center gap-3 px-4 py-3.5 active:bg-surface-2">
              <RefreshCw
                size={18}
                color={checkingUpdate ? colors.textFaint : colors.accentBright}
              />
              <AppText className="flex-1 font-sans-medium text-sm text-ink">
                {checkingUpdate ? "Checking…" : "Check for updates"}
              </AppText>
              <AppText variant="caption">v{installedVersion()}</AppText>
            </Pressable>
          </Card>
        </View>
      )}
    </ScrollView>
  );
}
