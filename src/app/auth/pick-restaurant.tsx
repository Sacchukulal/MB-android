/** Restaurant picker — multi-outlet owners choose which outlet to open. */
import { useRouter } from "expo-router";
import { ChevronRight, Store } from "lucide-react-native";
import { Pressable, ScrollView, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { AppText, Card } from "@/components/ui";
import { registerMobileDevice } from "@/lib/device";
import { saveOwnerRestaurant } from "@/lib/session-store";
import { useAuth, type RestaurantInfo } from "@/stores/auth";
import { useThemeColors } from "@/stores/theme";

export default function PickRestaurant() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const colors = useThemeColors();
  const restaurants = useAuth((s) => s.restaurants);
  const switchRestaurant = useAuth((s) => s.switchRestaurant);

  const choose = async (r: RestaurantInfo) => {
    switchRestaurant(r);
    await saveOwnerRestaurant(r.licenseKey);
    registerMobileDevice(r.licenseKey);
    router.replace("/dashboard");
  };

  return (
    <ScrollView
      className="flex-1 bg-bg"
      contentContainerStyle={{
        paddingTop: insets.top + 24,
        paddingBottom: insets.bottom + 24,
        paddingHorizontal: 24,
      }}>
      <AppText variant="title">Choose a restaurant</AppText>
      <AppText className="mb-8 mt-2 text-sm text-ink-muted">
        You can switch anytime from the dashboard header.
      </AppText>

      <View className="gap-3">
        {restaurants.map((r) => (
          <Pressable
            key={r.licenseKey}
            onPress={() => choose(r)}
            style={({ pressed }) => ({
              transform: [{ scale: pressed ? 0.98 : 1 }],
            })}>
            <Card raised className="flex-row items-center gap-4">
              <View className="h-11 w-11 items-center justify-center rounded-xl bg-accent-soft">
                <Store size={20} color={colors.accentBright} />
              </View>
              <View className="flex-1">
                <AppText variant="heading">{r.name}</AppText>
                {r.code ? (
                  <AppText variant="caption">Code: {r.code}</AppText>
                ) : null}
              </View>
              <ChevronRight size={20} color={colors.textFaint} />
            </Card>
          </Pressable>
        ))}
      </View>
    </ScrollView>
  );
}
