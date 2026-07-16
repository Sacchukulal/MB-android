import { Text as RNText, type TextProps } from "react-native";

import { cn } from "@/lib/cn";

type Variant =
  | "display" // big numbers, hero stats
  | "title" // screen titles
  | "heading" // section headings
  | "body" // default copy
  | "label" // form labels, emphasized small text
  | "caption" // secondary small text
  | "mono"; // receipt / tabular text

const variantClasses: Record<Variant, string> = {
  display: "font-sans-extrabold text-4xl text-ink tracking-tight",
  title: "font-sans-bold text-2xl text-ink tracking-tight",
  heading: "font-sans-semibold text-lg text-ink",
  body: "font-sans text-base text-ink",
  label: "font-sans-medium text-sm text-ink-muted",
  caption: "font-sans text-xs text-ink-faint",
  mono: "font-mono text-sm text-ink",
};

export interface AppTextProps extends TextProps {
  variant?: Variant;
  className?: string;
}

export function AppText({
  variant = "body",
  className,
  ...props
}: AppTextProps) {
  return (
    <RNText className={cn(variantClasses[variant], className)} {...props} />
  );
}
