/**
 * Subscription management via Chrome Custom Tab + session handoff.
 *
 * expo-web-browser's openBrowserAsync uses a Chrome Custom Tab on Android —
 * a real browser engine that slides over the app: Razorpay checkout, UPI
 * intents (GPay/PhonePe) and 3-D Secure all work, unlike a raw WebView, and
 * unlike Linking.openURL it keeps the in-app feel.
 *
 * The owner is already signed in here, so we pass the CURRENT Supabase
 * session (access + refresh token) to the website's /auth/mobile-handoff
 * endpoint, which validates it server-side and sets browser cookies — they
 * land directly on their billing page with no second login.
 */
import * as WebBrowser from "expo-web-browser";

import { palettes } from "@/constants/theme";
import { supabase } from "@/lib/supabase";
import { useTheme } from "@/stores/theme";

const SITE_URL = "https://magicbill.in";

export type BillingDestination =
  | "/dashboard/billing" // manage / switch / resubscribe
  | "/pricing"; // brand-new subscriber

/**
 * Opens the website already authenticated (when a session exists) in a
 * Chrome Custom Tab. Resolves when the owner closes the tab — callers
 * should then re-fetch license data to reflect any payment immediately.
 */
export async function openBillingPortal(
  destination: BillingDestination = "/dashboard/billing"
): Promise<void> {
  const { data } = await supabase.auth.getSession();
  const accessToken = data.session?.access_token;
  const refreshToken = data.session?.refresh_token;

  const url =
    accessToken && refreshToken
      ? `${SITE_URL}/auth/mobile-handoff?token=${encodeURIComponent(
          accessToken
        )}&refresh=${encodeURIComponent(refreshToken)}&redirect=${encodeURIComponent(
          destination
        )}`
      : `${SITE_URL}${destination}`;

  const colors = palettes[useTheme.getState().mode];
  await WebBrowser.openBrowserAsync(url, {
    toolbarColor: colors.surface,
    secondaryToolbarColor: colors.bg,
    controlsColor: colors.accentBright,
    showTitle: true,
    enableDefaultShareMenuItem: false,
    createTask: false,
  });
}
