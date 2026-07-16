/** Orders — intentional, polished placeholder until Phase 6 ships ordering. */
import { LinearGradient } from "expo-linear-gradient";
import { ClipboardList, Sparkles, UtensilsCrossed } from "lucide-react-native";
import { View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { AppText, Badge, Card } from "@/components/ui";
import { useThemeColors } from "@/stores/theme";

export default function OrdersComingSoon() {
  const insets = useSafeAreaInsets();
  const colors = useThemeColors();

  return (
    <View
      className="flex-1 bg-bg px-6"
      style={{ paddingTop: insets.top + 12 }}>
      <AppText variant="title" className="mb-6">
        Orders
      </AppText>

      <Card flush className="overflow-hidden">
        <LinearGradient
          colors={[colors.accentSoft, "transparent"]}
          start={{ x: 0, y: 0 }}
          end={{ x: 0, y: 1 }}>
          <View className="items-center px-6 py-12">
            <View className="h-20 w-20 items-center justify-center rounded-3xl bg-accent-soft">
              <UtensilsCrossed size={36} color={colors.accentBright} />
            </View>
            <Badge label="Coming soon" tone="accent" className="mt-5" />
            <AppText variant="title" className="mt-3 text-center">
              Mobile ordering is on its way
            </AppText>
            <AppText className="mt-3 text-center text-sm leading-5 text-ink-muted">
              Soon you'll take orders right from this phone — tables, items and
              quantities — and they'll appear instantly on the billing counter.
            </AppText>

            <View className="mt-8 w-full gap-3">
              <View className="flex-row items-center gap-3">
                <ClipboardList size={16} color={colors.accentBright} />
                <AppText className="text-sm text-ink-muted">
                  Take orders table-by-table
                </AppText>
              </View>
              <View className="flex-row items-center gap-3">
                <Sparkles size={16} color={colors.accentBright} />
                <AppText className="text-sm text-ink-muted">
                  Synced live with the Magic Bill POS
                </AppText>
              </View>
            </View>
          </View>
        </LinearGradient>
      </Card>
    </View>
  );
}
