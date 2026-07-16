/** "Offline — last updated X ago" chip shown when data came from cache. */
import { WifiOff } from "lucide-react-native";
import { View } from "react-native";

import { AppText } from "@/components/ui";
import { timeAgo } from "@/lib/format";
import { useThemeColors } from "@/stores/theme";

export function CacheChip({
  stale,
  updatedAt,
}: {
  stale: boolean;
  updatedAt: number | null;
}) {
  const colors = useThemeColors();
  if (!stale || !updatedAt) return null;
  return (
    <View className="mb-4 flex-row items-center justify-center gap-2 self-center rounded-full bg-warning/15 px-3.5 py-1.5">
      <WifiOff size={13} color={colors.warning} />
      <AppText className="font-sans-medium text-xs text-warning">
        Offline — last updated {timeAgo(updatedAt)}
      </AppText>
    </View>
  );
}
