/**
 * 14-day revenue trend — View-based bar chart (no chart lib).
 * Mark spec: thin bars, 4px rounded data-ends, 2px gaps, recessive baseline,
 * selective direct labels (peak + today), tap a bar to reveal its value.
 */
import { useMemo, useState } from "react";
import { Pressable, View } from "react-native";

import { AppText } from "@/components/ui";
import { formatShortINR, shortDate } from "@/lib/format";
import { useThemeColors } from "@/stores/theme";

const CHART_HEIGHT = 132;

export interface TrendPoint {
  day: string;
  total: number;
}

export function TrendBars({ points }: { points: TrendPoint[] }) {
  const colors = useThemeColors();
  const [selected, setSelected] = useState<number | null>(null);

  const max = useMemo(
    () => Math.max(...points.map((p) => p.total), 1),
    [points]
  );
  const peakIdx = useMemo(() => {
    let idx = 0;
    points.forEach((p, i) => {
      if (p.total > points[idx].total) idx = i;
    });
    return idx;
  }, [points]);
  const lastIdx = points.length - 1;

  return (
    <View>
      {/* Selected-value readout keeps the label out of the plot area */}
      <View className="mb-2 h-5 flex-row items-center justify-between">
        <AppText variant="caption">
          {selected != null
            ? `${shortDate(points[selected].day)} — ${formatShortINR(points[selected].total)}`
            : "Last 14 days"}
        </AppText>
        {selected == null ? (
          <AppText variant="caption">peak {formatShortINR(max)}</AppText>
        ) : null}
      </View>

      <View
        className="flex-row items-end gap-[2px] border-b border-line pb-0"
        style={{ height: CHART_HEIGHT }}>
        {points.map((p, i) => {
          const h = Math.max(
            p.total > 0 ? 4 : 2,
            Math.round((p.total / max) * (CHART_HEIGHT - 18))
          );
          const isToday = i === lastIdx;
          const isSelected = selected === i;
          return (
            <Pressable
              key={p.day}
              className="flex-1 items-center justify-end"
              style={{ height: "100%" }}
              onPress={() => setSelected(isSelected ? null : i)}>
              {/* direct labels: peak + today only */}
              {(i === peakIdx || isToday) && p.total > 0 && !isSelected ? (
                <AppText
                  variant="caption"
                  className="mb-0.5 text-[9px]"
                  numberOfLines={1}>
                  {formatShortINR(p.total)}
                </AppText>
              ) : null}
              <View
                className="w-full rounded-t"
                style={{
                  height: h,
                  backgroundColor:
                    p.total === 0
                      ? colors.line
                      : isSelected
                        ? colors.accentBright
                        : isToday
                          ? colors.accent
                          : colors.chart.upi,
                  opacity: isToday || isSelected ? 1 : 0.75,
                }}
              />
            </Pressable>
          );
        })}
      </View>

      {/* x labels: first, middle, today */}
      <View className="mt-1.5 flex-row justify-between">
        <AppText variant="caption" className="text-[10px]">
          {shortDate(points[0].day)}
        </AppText>
        <AppText variant="caption" className="text-[10px]">
          {shortDate(points[Math.floor(points.length / 2)].day)}
        </AppText>
        <AppText variant="caption" className="text-[10px] text-ink-muted">
          today
        </AppText>
      </View>
    </View>
  );
}
