/**
 * Staff shell — tabs appear only for what the owner enabled:
 * Home (always), Reports (view_reports), Orders (take_orders), Profile.
 * A guard boots revoked sessions back to the welcome screen.
 */
import { Tabs, useRouter } from "expo-router";
import {
  BarChart3,
  CircleUserRound,
  ClipboardList,
  LayoutDashboard,
} from "lucide-react-native";
import { useEffect } from "react";

import { fonts } from "@/constants/theme";
import { useAuth, useCan } from "@/stores/auth";
import { useThemeColors } from "@/stores/theme";

export default function StaffLayout() {
  const colors = useThemeColors();
  const router = useRouter();
  const kind = useAuth((s) => s.kind);
  const revoked = useAuth((s) => s.revoked);
  const canReports = useCan("view_reports");
  const canOrders = useCan("take_orders");

  // Session guard: server-side revocation (marked by staff-data) shows the
  // "Access revoked" message; a voluntary logout goes to welcome silently.
  useEffect(() => {
    if (kind === null) {
      if (revoked) {
        useAuth.getState().ackRevoked();
        router.replace({ pathname: "/welcome", params: { revoked: "1" } });
      } else {
        router.replace("/welcome");
      }
    }
  }, [kind, revoked, router]);

  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        sceneStyle: { backgroundColor: colors.bg },
        tabBarStyle: {
          backgroundColor: colors.surface,
          borderTopColor: colors.line,
          borderTopWidth: 1,
          height: 64,
          paddingTop: 6,
          paddingBottom: 8,
        },
        tabBarActiveTintColor: colors.accentBright,
        tabBarInactiveTintColor: colors.textFaint,
        tabBarLabelStyle: {
          fontFamily: fonts.medium,
          fontSize: 11,
        },
      }}>
      <Tabs.Screen
        name="home"
        options={{
          title: "Home",
          tabBarIcon: ({ color, size }) => (
            <LayoutDashboard size={size - 2} color={color} />
          ),
        }}
      />
      <Tabs.Screen
        name="reports"
        options={{
          title: "Reports",
          href: canReports ? "/(staff)/reports" : null,
          tabBarIcon: ({ color, size }) => (
            <BarChart3 size={size - 2} color={color} />
          ),
        }}
      />
      <Tabs.Screen
        name="orders"
        options={{
          title: "Orders",
          href: canOrders ? "/orders" : null,
          tabBarIcon: ({ color, size }) => (
            <ClipboardList size={size - 2} color={color} />
          ),
        }}
      />
      <Tabs.Screen
        name="profile"
        options={{
          title: "Profile",
          tabBarIcon: ({ color, size }) => (
            <CircleUserRound size={size - 2} color={color} />
          ),
        }}
      />
    </Tabs>
  );
}
