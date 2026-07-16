/** Staff profile — identity, theme, logout. No self-service permission/PIN edits. */
import { useRouter } from "expo-router";
import { Info, LogOut, Moon, Store, Sun } from "lucide-react-native";
import { useState } from "react";
import { Pressable, ScrollView, Switch, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import {
  AppModal,
  AppText,
  Avatar,
  Badge,
  Button,
  Card,
} from "@/components/ui";
import { logoutStaff } from "@/lib/logout";
import { useAuth } from "@/stores/auth";
import { useTheme, useThemeColors } from "@/stores/theme";

export default function StaffProfile() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const colors = useThemeColors();
  const staff = useAuth((s) => s.staff);
  const restaurant = useAuth((s) => s.restaurant);
  const mode = useTheme((s) => s.mode);
  const toggleTheme = useTheme((s) => s.toggle);
  const [confirmLogout, setConfirmLogout] = useState(false);

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
        paddingBottom: 32,
        paddingHorizontal: 20,
      }}>
      <AppText variant="title" className="mb-6">
        Profile
      </AppText>

      {/* Identity */}
      <Card className="items-center py-8">
        <Avatar name={staff?.name ?? "?"} size="lg" />
        <AppText variant="title" className="mt-3">
          {staff?.name ?? ""}
        </AppText>
        <Badge label={staff?.roleLabel ?? "Staff"} tone="accent" className="mt-2" />
        <View className="mt-4 flex-row items-center gap-2">
          <Store size={14} color={colors.textMuted} />
          <AppText variant="caption">{restaurant?.name ?? ""}</AppText>
        </View>
      </Card>

      {/* Settings */}
      <Card className="mt-4 gap-1" flush>
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
        <View className="flex-row items-start gap-3 px-4 py-3.5">
          <Info size={18} color={colors.textMuted} style={{ marginTop: 1 }} />
          <AppText className="flex-1 text-sm leading-5 text-ink-muted">
            Your access is managed by the restaurant owner. Need more features
            or a new PIN? Ask your manager.
          </AppText>
        </View>
      </Card>

      <Pressable
        onPress={() => setConfirmLogout(true)}
        className="mt-4 flex-row items-center justify-center gap-2 rounded-xl border border-danger/30 bg-danger/10 py-3.5">
        <LogOut size={16} color={colors.danger} />
        <AppText className="font-sans-semibold text-sm text-danger">
          Log out
        </AppText>
      </Pressable>

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
