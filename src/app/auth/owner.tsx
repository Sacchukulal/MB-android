/** Owner login — Supabase Auth (same identity as the magicbill.in portal). */
import { useRouter } from "expo-router";
import { ArrowLeft, ExternalLink } from "lucide-react-native";
import { useState } from "react";
import {
  KeyboardAvoidingView,
  Linking,
  Platform,
  Pressable,
  ScrollView,
  View,
} from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { AppText, Button, Input } from "@/components/ui";
import { registerMobileDevice } from "@/lib/device";
import { loadOwnerRestaurants } from "@/lib/owner";
import { loadOwnerRestaurant, saveOwnerRestaurant } from "@/lib/session-store";
import { supabase } from "@/lib/supabase";
import { useAuth } from "@/stores/auth";
import { useThemeColors } from "@/stores/theme";

export default function OwnerLogin() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const colors = useThemeColors();
  const setOwnerSession = useAuth((s) => s.setOwnerSession);

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const signIn = async () => {
    const trimmed = email.trim().toLowerCase();
    if (!trimmed || !password) {
      setError("Enter your email and password");
      return;
    }
    setError(null);
    setLoading(true);
    try {
      const { error: authErr } = await supabase.auth.signInWithPassword({
        email: trimmed,
        password,
      });
      if (authErr) {
        setError(
          authErr.message === "Invalid login credentials"
            ? "Wrong email or password. New to Magic Bill? Sign up on magicbill.in"
            : authErr.message
        );
        return;
      }

      const restaurants = await loadOwnerRestaurants();
      if (restaurants.length === 0) {
        // Signed in but nothing linked: onboarding screen, not an error.
        router.replace("/subscribe");
        return;
      }

      const savedKey = await loadOwnerRestaurant();
      const selected = restaurants.find((r) => r.licenseKey === savedKey);

      if (restaurants.length === 1) {
        await saveOwnerRestaurant(restaurants[0].licenseKey);
        setOwnerSession(restaurants, restaurants[0]);
        registerMobileDevice(restaurants[0].licenseKey);
        router.replace("/dashboard");
      } else if (selected) {
        setOwnerSession(restaurants, selected);
        registerMobileDevice(selected.licenseKey);
        router.replace("/dashboard");
      } else {
        setOwnerSession(restaurants);
        router.replace("/auth/pick-restaurant");
      }
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
          <AppText variant="title">Owner sign in</AppText>
          <AppText className="mt-2 text-sm text-ink-muted">
            Use the same email & password as the Magic Bill website.
          </AppText>
        </View>

        <View className="gap-4">
          <Input
            label="Email"
            placeholder="owner@restaurant.com"
            keyboardType="email-address"
            autoCapitalize="none"
            autoComplete="email"
            value={email}
            onChangeText={setEmail}
          />
          <Input
            label="Password"
            placeholder="Your password"
            secureTextEntry
            autoComplete="password"
            value={password}
            onChangeText={setPassword}
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
          <AppText variant="caption" className="mb-2">
            Don't have an account?
          </AppText>
          <Pressable
            onPress={() => Linking.openURL("https://magicbill.in")}
            className="flex-row items-center gap-1.5">
            <AppText className="font-sans-semibold text-sm text-accent-bright">
              Sign up on magicbill.in
            </AppText>
            <ExternalLink size={14} color={colors.accentBright} />
          </Pressable>
          <AppText variant="caption" className="mt-2 text-center">
            Accounts are created on the website, where plans & payment live.
          </AppText>
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}
