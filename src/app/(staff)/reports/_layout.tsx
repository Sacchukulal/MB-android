import { Stack } from "expo-router";

import { useThemeColors } from "@/stores/theme";

export default function StaffReportsLayout() {
  const colors = useThemeColors();
  return (
    <Stack
      screenOptions={{
        headerShown: false,
        contentStyle: { backgroundColor: colors.bg },
        animation: "slide_from_right",
      }}
    />
  );
}
