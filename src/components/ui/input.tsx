import { Eye, EyeOff } from "lucide-react-native";
import { useState, type ReactNode } from "react";
import {
  Pressable,
  TextInput,
  View,
  type TextInputProps,
} from "react-native";

import { cn } from "@/lib/cn";
import { useThemeColors } from "@/stores/theme";

import { AppText } from "./text";

export interface InputProps extends TextInputProps {
  label?: string;
  error?: string;
  hint?: string;
  /** Optional leading icon element. */
  icon?: ReactNode;
  className?: string;
}

export function Input({
  label,
  error,
  hint,
  icon,
  secureTextEntry,
  className,
  ...props
}: InputProps) {
  const colors = useThemeColors();
  const [focused, setFocused] = useState(false);
  const [hidden, setHidden] = useState(secureTextEntry ?? false);

  return (
    <View className={className}>
      {label ? (
        <AppText variant="label" className="mb-1.5">
          {label}
        </AppText>
      ) : null}
      <View
        className={cn(
          "h-12 flex-row items-center rounded-xl border bg-surface px-4",
          error
            ? "border-danger/60"
            : focused
              ? "border-accent"
              : "border-line-strong"
        )}>
        {icon ? <View className="mr-3">{icon}</View> : null}
        <TextInput
          className="h-full flex-1 font-sans text-base text-ink"
          placeholderTextColor={colors.textFaint}
          selectionColor={colors.accentBright}
          secureTextEntry={hidden}
          onFocus={(e) => {
            setFocused(true);
            props.onFocus?.(e);
          }}
          onBlur={(e) => {
            setFocused(false);
            props.onBlur?.(e);
          }}
          {...props}
        />
        {secureTextEntry ? (
          <Pressable
            hitSlop={8}
            onPress={() => setHidden((h) => !h)}
            accessibilityLabel={hidden ? "Show password" : "Hide password"}>
            {hidden ? (
              <EyeOff size={18} color={colors.textFaint} />
            ) : (
              <Eye size={18} color={colors.textMuted} />
            )}
          </Pressable>
        ) : null}
      </View>
      {error ? (
        <AppText variant="caption" className="mt-1.5 text-danger">
          {error}
        </AppText>
      ) : hint ? (
        <AppText variant="caption" className="mt-1.5">
          {hint}
        </AppText>
      ) : null}
    </View>
  );
}
