/** Staff home — permission-gated shell; Phase E builds the full experience. */
import { useRouter } from "expo-router";
import {
  BarChart3,
  LayoutDashboard,
  LogOut,
  Receipt,
  Sparkles,
} from "lucide-react-native";
import { useState } from "react";
import { Pressable, ScrollView, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import {
  AppModal,
  AppText,
  Avatar,
  Badge,
  Button,
  Card,
  PermissionGate,
} from "@/components/ui";
import { logoutStaff } from "@/lib/logout";
import { useAuth, useCan } from "@/stores/auth";
import { useThemeColors } from "@/stores/theme";

export default function StaffHome() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const colors = useThemeColors();
  const staff = useAuth((s) => s.staff);
  const restaurant = useAuth((s) => s.restaurant);
  const [confirmLogout, setConfirmLogout] = useState(false);

  const hasAnyView =
    useCan("view_dashboard") || useCan("view_reports") || useCan("view_bills");

  const doLogout = async () => {
    setConfirmLogout(false);
    await logoutStaff();
    router.replace("/welcome");
  };

  return (
    <ScrollView
      className="flex-1 bg-bg"
      contentContainerStyle={{
        paddingTop: insets.top + 12,
        paddingBottom: insets.bottom + 24,
        paddingHorizontal: 20,
      }}>
      {/* Header */}
      <View className="mb-6 flex-row items-center gap-3">
        <Avatar name={staff?.name ?? "?"} />
        <View className="flex-1">
          <AppText variant="heading">Hi, {staff?.name ?? "there"}</AppText>
          <View className="mt-0.5 flex-row items-center gap-2">
            <Badge label={staff?.roleLabel ?? "Staff"} tone="accent" />
            <AppText variant="caption">{restaurant?.name ?? ""}</AppText>
          </View>
        </View>
        <Pressable
          onPress={() => setConfirmLogout(true)}
          accessibilityLabel="Log out"
          className="h-11 w-11 items-center justify-center rounded-full border border-line bg-surface">
          <LogOut size={18} color={colors.danger} />
        </Pressable>
      </View>

      <View className="gap-3">
        <PermissionGate permission="view_dashboard">
          <Card className="flex-row items-center gap-4">
            <View className="h-11 w-11 items-center justify-center rounded-xl bg-accent-soft">
              <LayoutDashboard size={20} color={colors.accentBright} />
            </View>
            <View className="flex-1">
              <AppText variant="heading">Today's summary</AppText>
              <AppText variant="caption">Coming in Phase E</AppText>
            </View>
          </Card>
        </PermissionGate>

        <PermissionGate permission="view_reports">
          <Card className="flex-row items-center gap-4">
            <View className="h-11 w-11 items-center justify-center rounded-xl bg-info/15">
              <BarChart3 size={20} color={colors.info} />
            </View>
            <View className="flex-1">
              <AppText variant="heading">Reports</AppText>
              <AppText variant="caption">Coming in Phase E</AppText>
            </View>
          </Card>
        </PermissionGate>

        <PermissionGate permission="view_bills">
          <Card className="flex-row items-center gap-4">
            <View className="h-11 w-11 items-center justify-center rounded-xl bg-success/15">
              <Receipt size={20} color={colors.success} />
            </View>
            <View className="flex-1">
              <AppText variant="heading">Bills</AppText>
              <AppText variant="caption">Coming in Phase E</AppText>
            </View>
          </Card>
        </PermissionGate>

        <PermissionGate permission="take_orders">
          <Card raised className="items-center py-8">
            <Sparkles size={28} color={colors.accentBright} />
            <AppText variant="heading" className="mt-3">
              Mobile ordering is coming soon!
            </AppText>
            <AppText variant="caption" className="mt-1 text-center">
              You'll be able to take orders directly from this app.
            </AppText>
          </Card>
        </PermissionGate>

        {!hasAnyView ? (
          <Card className="items-center py-10">
            <AppText variant="heading" className="text-center">
              You're all set!
            </AppText>
            <AppText className="mt-2 text-center text-sm text-ink-muted">
              You have access to this app. Your manager will enable features
              for you as needed.
            </AppText>
          </Card>
        ) : null}
      </View>

      <AppModal
        visible={confirmLogout}
        onClose={() => setConfirmLogout(false)}
        title="Log out?"
        actions={
          <>
            <Button
              title="Cancel"
              variant="ghost"
              size="sm"
              onPress={() => setConfirmLogout(false)}
            />
            <Button title="Log out" variant="danger" size="sm" onPress={doLogout} />
          </>
        }>
        <AppText className="text-sm text-ink-muted">
          You'll need the restaurant code and your PIN to sign back in.
        </AppText>
      </AppModal>
    </ScrollView>
  );
}
