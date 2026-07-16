import type { LucideIcon } from "lucide-react-native";
import type { ReactNode } from "react";
import { View } from "react-native";

import { cn } from "@/lib/cn";
import { useThemeColors } from "@/stores/theme";

import { AppText } from "./text";

export interface EmptyStateProps {
  icon?: LucideIcon;
  title: string;
  message?: string;
  /** Optional action, usually a Button. */
  action?: ReactNode;
  className?: string;
}

export function EmptyState({
  icon: Icon,
  title,
  message,
  action,
  className,
}: EmptyStateProps) {
  const colors = useThemeColors();
  return (
    <View className={cn("items-center px-8 py-12", className)}>
      {Icon ? (
        <View className="mb-4 h-16 w-16 items-center justify-center rounded-full bg-surface-2">
          <Icon size={28} color={colors.textMuted} strokeWidth={1.8} />
        </View>
      ) : null}
      <AppText variant="heading" className="text-center">
        {title}
      </AppText>
      {message ? (
        <AppText
          variant="body"
          className="mt-2 text-center text-sm text-ink-muted">
          {message}
        </AppText>
      ) : null}
      {action ? <View className="mt-6">{action}</View> : null}
    </View>
  );
}
