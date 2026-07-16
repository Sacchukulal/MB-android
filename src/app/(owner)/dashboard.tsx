/** Owner dashboard — placeholder shell; Phase C builds the real reports UI. */
import { useRouter } from "expo-router";
import { ChevronDown, LayoutDashboard, LogOut } from "lucide-react-native";
import { useState } from "react";
import { Pressable, ScrollView, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import {
  AppModal,
  AppText,
  Button,
  Card,
  EmptyState,
} from "@/components/ui";
import { logoutOwner } from "@/lib/logout";
import { useAuth } from "@/stores/auth";
import { useThemeColors } from "@/stores/theme";

export default function OwnerDashboard() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
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
    <ScrollView
      className="flex-1 bg-bg"
      contentContainerStyle={{
        paddingTop: insets.top + 12,
        paddingBottom: insets.bottom + 24,
        paddingHorizontal: 20,
      }}>
      {/* Header */}
      <View className="mb-6 flex-row items-center justify-between">
        <Pressable
          disabled={restaurants.length < 2}
          onPress={() => router.push("/auth/pick-restaurant")}
          className="flex-row items-center gap-1.5">
          <View>
            <AppText variant="caption">Restaurant</AppText>
            <View className="flex-row items-center gap-1">
              <AppText variant="heading">
                {restaurant?.name ?? "My Restaurant"}
              </AppText>
              {restaurants.length > 1 ? (
                <ChevronDown size={16} color={colors.textMuted} />
              ) : null}
            </View>
          </View>
        </Pressable>
        <Pressable
          onPress={() => setConfirmLogout(true)}
          accessibilityLabel="Log out"
          className="h-11 w-11 items-center justify-center rounded-full border border-line bg-surface">
          <LogOut size={18} color={colors.danger} />
        </Pressable>
      </View>

      <Card flush>
        <EmptyState
          icon={LayoutDashboard}
          title="Dashboard coming in Phase C"
          message="Today's sales, trends, payment split and reports will live here."
        />
      </Card>

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
    </ScrollView>
  );
}
