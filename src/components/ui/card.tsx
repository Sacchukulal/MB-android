import type { ReactNode } from "react";
import { View, type ViewProps } from "react-native";

import { cn } from "@/lib/cn";

export interface CardProps extends ViewProps {
  children: ReactNode;
  /** Removes the default inner padding. */
  flush?: boolean;
  /** Raised variant uses the lighter surface. */
  raised?: boolean;
  className?: string;
}

export function Card({
  children,
  flush = false,
  raised = false,
  className,
  ...props
}: CardProps) {
  return (
    <View
      className={cn(
        "rounded-card border border-line",
        raised ? "bg-surface-2" : "bg-surface",
        !flush && "p-4",
        className
      )}
      {...props}>
      {children}
    </View>
  );
}
