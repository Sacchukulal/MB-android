import { ActivityIndicator, View } from "react-native";

import { cn } from "@/lib/cn";
import { useThemeColors } from "@/stores/theme";

import { AppText } from "./text";

export interface LoadingSpinnerProps {
  /** Fills the available space and centers itself. */
  fullscreen?: boolean;
  label?: string;
  size?: "small" | "large";
  className?: string;
}

export function LoadingSpinner({
  fullscreen = false,
  label,
  size = "large",
  className,
}: LoadingSpinnerProps) {
  const colors = useThemeColors();
  return (
    <View
      className={cn(
        "items-center justify-center gap-3",
        fullscreen ? "flex-1 bg-bg" : "py-8",
        className
      )}>
      <ActivityIndicator size={size} color={colors.accentBright} />
      {label ? <AppText variant="caption">{label}</AppText> : null}
    </View>
  );
}
