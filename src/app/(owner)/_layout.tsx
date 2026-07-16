/**
 * Owner shell — bottom tabs: Dashboard, Reports, Staff, Account.
 * Phase 6 slots "Orders" in as a 5th Tabs.Screen here; the flexed tab bar
 * needs no layout changes for it.
 */
import { Tabs } from "expo-router";
import {
  BarChart3,
  CircleUserRound,
  LayoutDashboard,
  Users,
} from "lucide-react-native";

import { fonts } from "@/constants/theme";
import { useThemeColors } from "@/stores/theme";

export default function OwnerLayout() {
  const colors = useThemeColors();

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
        name="dashboard"
        options={{
          title: "Dashboard",
          tabBarIcon: ({ color, size }) => (
            <LayoutDashboard size={size - 2} color={color} />
          ),
        }}
      />
      <Tabs.Screen
        name="reports"
        options={{
          title: "Reports",
          tabBarIcon: ({ color, size }) => (
            <BarChart3 size={size - 2} color={color} />
          ),
        }}
      />
      <Tabs.Screen
        name="staff"
        options={{
          title: "Staff",
          tabBarIcon: ({ color, size }) => <Users size={size - 2} color={color} />,
        }}
      />
      <Tabs.Screen
        name="account"
        options={{
          title: "Account",
          tabBarIcon: ({ color, size }) => (
            <CircleUserRound size={size - 2} color={color} />
          ),
        }}
      />
    </Tabs>
  );
}
