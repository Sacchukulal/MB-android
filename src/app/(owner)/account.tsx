/** Account — restaurant info, subscription status, device lock, settings. */
import {
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
import { useState } from "react";
import {
  Linking,
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
import { daysUntil, fetchAccount, statusTone } from "@/lib/account";
import { formatINR, longDate, timeAgo } from "@/lib/format";
import { installedVersion, useUpdate } from "@/lib/update";
import { useAuth } from "@/stores/auth";
import { useTheme, useThemeColors } from "@/stores/theme";

const BILLING_URL = "https://magicbill.in/dashboard/billing";

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
            <Button
              title="Manage subscription on magicbill.in"
              variant="secondary"
              size="sm"
              className="mt-4"
              icon={<ExternalLink size={14} color={colors.accentBright} />}
              onPress={() => Linking.openURL(BILLING_URL)}
            />
            <AppText variant="caption" className="mt-2 text-center">
              Plans & payment are handled on the website.
            </AppText>
          </Card>

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
