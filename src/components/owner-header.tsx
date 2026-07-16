/** Shared owner screen header: restaurant switcher + optional actions. */
import { useRouter } from "expo-router";
import { ChevronDown, LogOut } from "lucide-react-native";
import { useState, type ReactNode } from "react";
import { Pressable, View } from "react-native";

import { AppModal, AppText, Button } from "@/components/ui";
import { logoutOwner } from "@/lib/logout";
import { useAuth } from "@/stores/auth";
import { useThemeColors } from "@/stores/theme";

export function OwnerHeader({
  title,
  right,
  showLogout = false,
}: {
  /** Screen name shown under the restaurant (e.g. "Reports"). */
  title?: string;
  right?: ReactNode;
  showLogout?: boolean;
}) {
  const router = useRouter();
  const colors = useThemeColors();
  const restaurant = useAuth((s) => s.restaurant);
  const restaurants = useAuth((s) => s.restaurants);
  const [confirmLogout, setConfirmLogout] = useState(false);

  const doLogout = async () => {
    setConfirmLogout(false);
    await logoutOwner();
    router.replace("/welcome");
  };

  return (
    <View className="mb-5 flex-row items-center justify-between">
      <Pressable
        disabled={restaurants.length < 2}
        onPress={() => router.push("/auth/pick-restaurant")}
        className="flex-1 pr-3">
        <View className="flex-row items-center gap-1">
          <AppText
            variant="heading"
            numberOfLines={1}
            className="max-w-[85%]">
            {restaurant?.name ?? "My Restaurant"}
          </AppText>
          {restaurants.length > 1 ? (
            <ChevronDown size={16} color={colors.textMuted} />
          ) : null}
        </View>
        {title ? <AppText variant="caption">{title}</AppText> : null}
      </Pressable>

      <View className="flex-row items-center gap-2">
        {right}
        {showLogout ? (
          <Pressable
            onPress={() => setConfirmLogout(true)}
            accessibilityLabel="Log out"
            className="h-11 w-11 items-center justify-center rounded-full border border-line bg-surface">
            <LogOut size={18} color={colors.danger} />
          </Pressable>
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
          You'll need your email & password to sign back in.
        </AppText>
      </AppModal>
    </View>
  );
}
