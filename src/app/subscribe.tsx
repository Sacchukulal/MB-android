/**
 * No-subscription onboarding — a signed-in owner with no linked restaurant
 * lands here instead of a broken dashboard. Subscribing happens on the
 * website via Chrome Custom Tab with automatic sign-in (session handoff).
 */
import { Image } from "expo-image";
import { useRouter } from "expo-router";
import { LogOut, RefreshCw, Rocket } from "lucide-react-native";
import { useState } from "react";
import { Pressable, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { AppText, Button, Card, useToast } from "@/components/ui";
import { openBillingPortal } from "@/lib/billing";
import { logoutOwner } from "@/lib/logout";
import { loadOwnerRestaurants } from "@/lib/owner";
import { saveOwnerRestaurant } from "@/lib/session-store";
import { useAuth } from "@/stores/auth";
import { useThemeColors } from "@/stores/theme";

export default function Subscribe() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const colors = useThemeColors();
  const toast = useToast();
  const setOwnerSession = useAuth((s) => s.setOwnerSession);
  const [checking, setChecking] = useState(false);

  const recheck = async () => {
    setChecking(true);
    try {
      const restaurants = await loadOwnerRestaurants();
      if (restaurants.length > 0) {
        await saveOwnerRestaurant(restaurants[0].licenseKey);
        setOwnerSession(restaurants, restaurants[0]);
        router.replace("/dashboard");
        return;
      }
      toast.show("No subscription found yet — finish checkout first", "info");
    } catch {
      toast.show("Couldn't check — verify your internet", "error");
    } finally {
      setChecking(false);
    }
  };

  const subscribe = async () => {
    await openBillingPortal("/pricing");
    // They may have just paid — check right away.
    recheck();
  };

  return (
    <View
      className="flex-1 bg-bg px-6"
      style={{ paddingTop: insets.top, paddingBottom: insets.bottom + 24 }}>
      <View className="flex-1 items-center justify-center">
        <Image
          source={require("@/assets/images/logo.png")}
          style={{ width: 96, height: 96 }}
          contentFit="contain"
        />
        <Card className="mt-8 w-full items-center px-6 py-8">
          <View className="h-14 w-14 items-center justify-center rounded-2xl bg-accent-soft">
            <Rocket size={26} color={colors.accentBright} />
          </View>
          <AppText variant="title" className="mt-4 text-center">
            Almost there!
          </AppText>
          <AppText className="mt-2 text-center text-sm leading-5 text-ink-muted">
            You don't have an active subscription yet. Subscribe to start
            using Magic Bill — reports, staff access and more.
          </AppText>
          <Button
            title="View Plans & Subscribe"
            size="lg"
            className="mt-6 w-full"
            onPress={subscribe}
          />
          <Button
            title={checking ? "Checking…" : "I've subscribed — check again"}
            variant="ghost"
            size="sm"
            className="mt-3"
            loading={checking}
            icon={<RefreshCw size={14} color={colors.accentBright} />}
            onPress={recheck}
          />
        </Card>
      </View>

      <Pressable
        onPress={async () => {
          await logoutOwner();
          router.replace("/welcome");
        }}
        className="flex-row items-center justify-center gap-2 py-3">
        <LogOut size={14} color={colors.textFaint} />
        <AppText variant="caption">Sign out</AppText>
      </Pressable>
    </View>
  );
}
