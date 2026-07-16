/**
 * The owner's permission checklist — every permission key as a labeled
 * toggle, plus quick presets that pre-fill (never lock) the switches.
 * No hardcoded roles anywhere: what's checked is what the staff member gets.
 */
import { Pressable, Switch, View } from "react-native";

import { AppText, Badge } from "@/components/ui";
import { cn } from "@/lib/cn";
import {
  PERMISSIONS,
  PERMISSION_PRESETS,
  type PermissionMap,
} from "@/lib/permissions";
import { useThemeColors } from "@/stores/theme";

export function PermissionEditor({
  value,
  onChange,
}: {
  value: PermissionMap;
  onChange: (next: PermissionMap) => void;
}) {
  const colors = useThemeColors();

  return (
    <View>
      {/* Presets */}
      <View className="mb-4 flex-row flex-wrap gap-2">
        {Object.entries(PERMISSION_PRESETS).map(([label, preset]) => (
          <Pressable
            key={label}
            onPress={() => onChange({ ...preset })}
            className="h-8 items-center justify-center rounded-full border border-line-strong bg-surface-2 px-3.5"
            style={({ pressed }) => ({ opacity: pressed ? 0.7 : 1 })}>
            <AppText className="font-sans-medium text-xs text-ink-muted">
              {label}
            </AppText>
          </Pressable>
        ))}
      </View>

      <View className="gap-1">
        {PERMISSIONS.map((p) => {
          const enabled = value[p.key] === true;
          return (
            <Pressable
              key={p.key}
              onPress={() => onChange({ ...value, [p.key]: !enabled })}
              className={cn(
                "flex-row items-center gap-3 rounded-xl px-2 py-2.5",
                enabled && "bg-accent-soft/50"
              )}>
              <View className="flex-1">
                <View className="flex-row items-center gap-2">
                  <AppText className="font-sans-medium text-sm text-ink">
                    {p.label}
                  </AppText>
                  {p.comingSoon ? (
                    <Badge label="Coming soon" tone="info" />
                  ) : null}
                </View>
                <AppText variant="caption" className="mt-0.5">
                  {p.description}
                </AppText>
              </View>
              <Switch
                value={enabled}
                onValueChange={(v) => onChange({ ...value, [p.key]: v })}
                trackColor={{ false: colors.surface2, true: colors.accentDeep }}
                thumbColor={enabled ? colors.accentBright : colors.textFaint}
              />
            </Pressable>
          );
        })}
      </View>
    </View>
  );
}
