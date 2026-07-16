/** Staff login — Restaurant Code + 4-digit PIN (owner-managed, no email). */
import * as Device from "expo-device";
import { useRouter } from "expo-router";
import { ArrowLeft } from "lucide-react-native";
import { useState } from "react";
import {
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  View,
} from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { AppText, Button, Input } from "@/components/ui";
import { callFunction } from "@/lib/api";
import { saveStaffSession, type StoredStaffSession } from "@/lib/session-store";
import { useAuth } from "@/stores/auth";
import { useThemeColors } from "@/stores/theme";

interface LoginResponse {
  ok: boolean;
  reason?: string;
  token?: string;
  staff?: StoredStaffSession["staff"];
  restaurant?: StoredStaffSession["restaurant"];
}

export default function StaffLogin() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const colors = useThemeColors();
  const setStaffSession = useAuth((s) => s.setStaffSession);

  const [code, setCode] = useState("");
  const [pin, setPin] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const signIn = async () => {
    const trimmedCode = code.trim().toUpperCase();
    if (!trimmedCode || pin.length !== 4) {
      setError("Enter your restaurant code and 4-digit PIN");
      return;
    }
    setError(null);
    setLoading(true);
    try {
      const res = await callFunction<LoginResponse>("staff-login", {
        restaurantCode: trimmedCode,
        pin,
        deviceInfo: `${Device.brand ?? ""} ${Device.modelName ?? ""}`.trim(),
      });

      if (!res.ok || !res.token || !res.staff || !res.restaurant) {
        setError("Invalid code or PIN");
        return;
      }

      const session: StoredStaffSession = {
        token: res.token,
        staff: res.staff,
        restaurant: res.restaurant,
      };
      await saveStaffSession(session);
      setStaffSession(session.staff, {
        licenseKey: "",
        name: session.restaurant.name,
        code: session.restaurant.code,
      });
      router.replace("/home");
    } catch {
      setError("Couldn't reach the server. Check your internet and try again.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <KeyboardAvoidingView
      className="flex-1 bg-bg"
      behavior={Platform.OS === "ios" ? "padding" : undefined}>
      <ScrollView
        contentContainerStyle={{
          paddingTop: insets.top + 8,
          paddingBottom: insets.bottom + 24,
          paddingHorizontal: 24,
          flexGrow: 1,
        }}
        keyboardShouldPersistTaps="handled">
        <Pressable
          onPress={() => router.back()}
          hitSlop={12}
          className="h-11 w-11 items-center justify-center rounded-full border border-line bg-surface">
          <ArrowLeft size={20} color={colors.textMuted} />
        </Pressable>

        <View className="mt-8 mb-10">
          <AppText variant="title">Staff sign in</AppText>
          <AppText className="mt-2 text-sm text-ink-muted">
            Ask your manager for the restaurant code and your PIN.
          </AppText>
        </View>

        <View className="gap-4">
          <Input
            label="Restaurant Code"
            placeholder="e.g. HH-4829"
            autoCapitalize="characters"
            autoCorrect={false}
            value={code}
            onChangeText={(t) => setCode(t.toUpperCase())}
          />
          <Input
            label="4-digit PIN"
            placeholder="••••"
            secureTextEntry
            keyboardType="number-pad"
            maxLength={4}
            value={pin}
            onChangeText={(t) => setPin(t.replace(/\D/g, ""))}
            error={error ?? undefined}
            onSubmitEditing={signIn}
          />
          <Button
            title="Sign in"
            size="lg"
            loading={loading}
            onPress={signIn}
            className="mt-2"
          />
        </View>

        <View className="mt-auto items-center pt-10">
          <AppText variant="caption" className="text-center">
            Your access is set by the restaurant owner.{"\n"}
            Lost your PIN? Ask your manager to reset it.
          </AppText>
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}
