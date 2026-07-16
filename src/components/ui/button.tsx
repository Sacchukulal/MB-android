import { LinearGradient } from "expo-linear-gradient";
import type { ReactNode } from "react";
import {
  ActivityIndicator,
  Pressable,
  Text,
  View,
  type PressableProps,
} from "react-native";

import { cn } from "@/lib/cn";
import { useThemeColors } from "@/stores/theme";

type Variant = "primary" | "secondary" | "ghost" | "danger";
type Size = "sm" | "md" | "lg";

export interface ButtonProps extends Omit<PressableProps, "children"> {
  title: string;
  variant?: Variant;
  size?: Size;
  loading?: boolean;
  /** Optional leading icon element (e.g. a lucide icon). */
  icon?: ReactNode;
  className?: string;
}

const sizeClasses: Record<Size, string> = {
  sm: "h-10 px-4 rounded-xl",
  md: "h-12 px-5 rounded-xl",
  lg: "h-14 px-6 rounded-2xl",
};

const textSize: Record<Size, string> = {
  sm: "text-sm",
  md: "text-base",
  lg: "text-lg",
};

export function Button({
  title,
  variant = "primary",
  size = "md",
  loading = false,
  icon,
  disabled,
  className,
  ...props
}: ButtonProps) {
  const colors = useThemeColors();
  const isDisabled = disabled || loading;

  const label = (pressed: boolean) => (
    <View className="flex-row items-center justify-center gap-2">
      {loading ? (
        <ActivityIndicator
          size="small"
          color={variant === "primary" ? colors.bg : colors.accentBright}
        />
      ) : (
        icon
      )}
      <Text
        className={cn(
          "font-sans-semibold",
          textSize[size],
          variant === "primary" && "text-bg-deep",
          variant === "secondary" && "text-ink",
          variant === "ghost" && "text-accent-bright",
          variant === "danger" && "text-danger",
          pressed && variant !== "primary" && "opacity-80"
        )}>
        {title}
      </Text>
    </View>
  );

  if (variant === "primary") {
    return (
      <Pressable
        accessibilityRole="button"
        disabled={isDisabled}
        className={cn("overflow-hidden", sizeClasses[size], className)}
        style={({ pressed }) => ({
          opacity: isDisabled ? 0.5 : 1,
          transform: [{ scale: pressed ? 0.98 : 1 }],
        })}
        {...props}>
        {({ pressed }) => (
          <LinearGradient
            colors={
              pressed
                ? [colors.accentDeep, colors.accentDeep]
                : [colors.accentBright, colors.accentDeep]
            }
            start={{ x: 0, y: 0 }}
            end={{ x: 1, y: 1 }}
            className="absolute inset-0"
            style={{ position: "absolute", top: 0, left: 0, right: 0, bottom: 0 }}>
            <View className="flex-1 items-center justify-center">
              {label(pressed)}
            </View>
          </LinearGradient>
        )}
      </Pressable>
    );
  }

  return (
    <Pressable
      accessibilityRole="button"
      disabled={isDisabled}
      className={cn(
        "items-center justify-center",
        sizeClasses[size],
        variant === "secondary" && "bg-surface-2 border border-line-strong",
        variant === "ghost" && "bg-transparent",
        variant === "danger" && "bg-danger/10 border border-danger/30",
        className
      )}
      style={({ pressed }) => ({
        opacity: isDisabled ? 0.5 : 1,
        transform: [{ scale: pressed ? 0.98 : 1 }],
      })}
      {...props}>
      {({ pressed }) => label(pressed)}
    </Pressable>
  );
}
