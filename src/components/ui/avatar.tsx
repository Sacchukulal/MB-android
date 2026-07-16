import { View } from "react-native";

import { cn } from "@/lib/cn";

import { AppText } from "./text";

type Size = "sm" | "md" | "lg";

const sizeClasses: Record<Size, { box: string; text: string }> = {
  sm: { box: "h-8 w-8", text: "text-xs" },
  md: { box: "h-11 w-11", text: "text-base" },
  lg: { box: "h-16 w-16", text: "text-2xl" },
};

/** Deterministic tint per name so each staff member keeps their color. */
const tints = [
  "bg-accent-soft",
  "bg-info/15",
  "bg-warning/15",
  "bg-success/15",
  "bg-brand-indigo/20",
  "bg-danger/15",
];
const textTints = [
  "text-accent-bright",
  "text-info",
  "text-warning",
  "text-success",
  "text-ink",
  "text-danger",
];

function initials(name: string): string {
  const parts = name.trim().split(/\s+/).slice(0, 2);
  return parts.map((p) => p[0]?.toUpperCase() ?? "").join("") || "?";
}

function hash(name: string): number {
  let h = 0;
  for (let i = 0; i < name.length; i++) h = (h * 31 + name.charCodeAt(i)) | 0;
  return Math.abs(h);
}

export interface AvatarProps {
  name: string;
  size?: Size;
  className?: string;
}

export function Avatar({ name, size = "md", className }: AvatarProps) {
  const idx = hash(name) % tints.length;
  const s = sizeClasses[size];
  return (
    <View
      className={cn(
        "items-center justify-center rounded-full",
        s.box,
        tints[idx],
        className
      )}>
      <AppText className={cn("font-sans-bold", s.text, textTints[idx])}>
        {initials(name)}
      </AppText>
    </View>
  );
}
