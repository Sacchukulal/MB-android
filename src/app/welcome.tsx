/** Welcome — the two-door entry: Owner or Staff. */
import { Image } from "expo-image";
import { useLocalSearchParams, useRouter } from "expo-router";
import { KeyRound, Moon, Store, Sun, UserRound } from "lucide-react-native";
import { useEffect } from "react";
import { Pressable, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { AppText, Card, useToast } from "@/components/ui";
import { useTheme, useThemeColors } from "@/stores/theme";

function DoorCard({
  icon,
  title,
  subtitle,
  onPress,
}: {
  icon: React.ReactNode;
  title: string;
  subtitle: string;
  onPress: () => void;
}) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      style={({ pressed }) => ({ transform: [{ scale: pressed ? 0.98 : 1 }] })}>
      <Card raised className="flex-row items-center gap-4 p-5">
        <View className="h-14 w-14 items-center justify-center rounded-2xl bg-accent-soft">
          {icon}
        </View>
        <View className="flex-1">
          <AppText variant="heading">{title}</AppText>
          <AppText variant="caption" className="mt-0.5">
            {subtitle}
          </AppText>
        </View>
      </Card>
    </Pressable>
  );
}

export default function Welcome() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const colors = useThemeColors();
  const mode = useTheme((s) => s.mode);
  const toggleTheme = useTheme((s) => s.toggle);
  const toast = useToast();
  const { revoked } = useLocalSearchParams<{ revoked?: string }>();

  useEffect(() => {
    if (revoked === "1") {
      toast.show("Access revoked — contact your manager", "error");
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [revoked]);

  return (
    <View
      className="flex-1 bg-bg px-6"
      style={{ paddingTop: insets.top, paddingBottom: insets.bottom + 24 }}>
      {/* Theme toggle */}
      <View className="flex-row justify-end pt-3">
        <Pressable
          onPress={toggleTheme}
          accessibilityLabel="Toggle light/dark theme"
          className="h-11 w-11 items-center justify-center rounded-full border border-line-strong bg-surface"
          style={({ pressed }) => ({ opacity: pressed ? 0.7 : 1 })}>
          {mode === "dark" ? (
            <Sun size={20} color={colors.warning} />
          ) : (
            <Moon size={20} color={colors.indigo} />
          )}
        </Pressable>
      </View>

      {/* Brand */}
      <View className="flex-1 items-center justify-center">
        <Image
          source={require("@/assets/images/logo.png")}
          style={{ width: 120, height: 120 }}
          contentFit="contain"
        />
        <AppText variant="display" className="mt-4">
          Magic Bill
        </AppText>
        <AppText variant="body" className="mt-1 text-ink-muted">
          Your restaurant, in your pocket
        </AppText>
      </View>

      {/* Doors */}
      <View className="gap-4 pb-4">
        <DoorCard
          icon={<Store size={26} color={colors.accentBright} />}
          title="I'm the Owner"
          subtitle="Sign in with email & password"
          onPress={() => router.push("/auth/owner")}
        />
        <DoorCard
          icon={<UserRound size={26} color={colors.accentBright} />}
          title="I'm Staff"
          subtitle="Sign in with restaurant code & PIN"
          onPress={() => router.push("/auth/staff")}
        />
      </View>

      {__DEV__ ? (
        <Pressable
          onPress={() => router.push("/dev-gallery")}
          className="flex-row items-center justify-center gap-1.5 py-2">
          <KeyRound size={12} color={colors.textFaint} />
          <AppText variant="caption">Design gallery (dev)</AppText>
        </Pressable>
      ) : null}
    </View>
  );
}
