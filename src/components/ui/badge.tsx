import { View } from "react-native";

import { cn } from "@/lib/cn";

import { AppText } from "./text";

type Tone = "accent" | "success" | "warning" | "danger" | "info" | "neutral";

const toneClasses: Record<Tone, { box: string; text: string }> = {
  accent: { box: "bg-accent-soft", text: "text-accent-bright" },
  success: { box: "bg-success/15", text: "text-success" },
  warning: { box: "bg-warning/15", text: "text-warning" },
  danger: { box: "bg-danger/15", text: "text-danger" },
  info: { box: "bg-info/15", text: "text-info" },
  neutral: { box: "bg-surface-2", text: "text-ink-muted" },
};

export interface BadgeProps {
  label: string;
  tone?: Tone;
  className?: string;
}

export function Badge({ label, tone = "neutral", className }: BadgeProps) {
  const t = toneClasses[tone];
  return (
    <View
      className={cn(
        "self-start rounded-full px-2.5 py-1",
        t.box,
        className
      )}>
      <AppText className={cn("font-sans-semibold text-xs", t.text)}>
        {label}
      </AppText>
    </View>
  );
}
