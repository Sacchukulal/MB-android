import "@/global.css";

import { Inter_400Regular } from "@expo-google-fonts/inter/400Regular";
import { Inter_500Medium } from "@expo-google-fonts/inter/500Medium";
import { Inter_600SemiBold } from "@expo-google-fonts/inter/600SemiBold";
import { Inter_700Bold } from "@expo-google-fonts/inter/700Bold";
import { Inter_800ExtraBold } from "@expo-google-fonts/inter/800ExtraBold";
import { useFonts } from "expo-font";
import { DarkTheme, DefaultTheme, Stack, ThemeProvider } from "expo-router";
import * as SplashScreen from "expo-splash-screen";
import { StatusBar } from "expo-status-bar";
import { useEffect } from "react";
import { View } from "react-native";
import { SafeAreaProvider } from "react-native-safe-area-context";

import { ToastProvider } from "@/components/ui";
import { UpdatePrompt } from "@/components/update-prompt";
import { palettes, themeVars, type ThemeMode } from "@/constants/theme";
import { useTheme } from "@/stores/theme";

SplashScreen.preventAutoHideAsync();

function navTheme(mode: ThemeMode) {
  const base = mode === "dark" ? DarkTheme : DefaultTheme;
  const p = palettes[mode];
  return {
    ...base,
    colors: {
      ...base.colors,
      primary: p.accentBright,
      background: p.bg,
      card: p.surface,
      text: p.text,
      border: p.lineStrong,
      notification: p.accent,
    },
  };
}

export default function RootLayout() {
  const [fontsLoaded] = useFonts({
    Inter_400Regular,
    Inter_500Medium,
    Inter_600SemiBold,
    Inter_700Bold,
    Inter_800ExtraBold,
  });
  const mode = useTheme((s) => s.mode);
  const themeHydrated = useTheme((s) => s.hydrated);
  const hydrateTheme = useTheme((s) => s.hydrate);

  useEffect(() => {
    hydrateTheme();
  }, [hydrateTheme]);

  const ready = fontsLoaded && themeHydrated;

  useEffect(() => {
    if (ready) SplashScreen.hideAsync();
  }, [ready]);

  if (!ready) return null;

  return (
    <SafeAreaProvider>
      <ThemeProvider value={navTheme(mode)}>
        <View style={themeVars[mode]} className="flex-1 bg-bg">
          <ToastProvider>
            <StatusBar style={mode === "dark" ? "light" : "dark"} />
            <Stack
              screenOptions={{
                headerShown: false,
                contentStyle: { backgroundColor: palettes[mode].bg },
                animation: "fade_from_bottom",
              }}
            />
            <UpdatePrompt />
          </ToastProvider>
        </View>
      </ThemeProvider>
    </SafeAreaProvider>
  );
}
