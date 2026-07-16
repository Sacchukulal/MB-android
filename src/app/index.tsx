/**
 * Phase A review screen — a living gallery of the Magic Bill design system.
 * This is temporary: Phase B replaces it with the welcome / two-door login.
 */
import { Image } from "expo-image";
import {
  BarChart3,
  IndianRupee,
  Moon,
  Plus,
  Receipt,
  Sun,
  Users,
} from "lucide-react-native";
import { useState } from "react";
import { Pressable, ScrollView, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import {
  AppModal,
  AppText,
  Avatar,
  Badge,
  BottomSheet,
  Button,
  Card,
  EmptyState,
  Input,
  LoadingSpinner,
  useToast,
} from "@/components/ui";
import type { Palette } from "@/constants/theme";
import { useTheme, useThemeColors } from "@/stores/theme";

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <View className="mb-8">
      <AppText variant="label" className="mb-3 uppercase tracking-widest text-ink-faint">
        {title}
      </AppText>
      {children}
    </View>
  );
}

const swatches = (colors: Palette): Array<{ name: string; color: string }> => [
  { name: "bg", color: colors.bg },
  { name: "surface", color: colors.surface },
  { name: "surface-2", color: colors.surface2 },
  { name: "accent", color: colors.accent },
  { name: "accent-bright", color: colors.accentBright },
  { name: "indigo", color: colors.indigo },
  { name: "success", color: colors.success },
  { name: "warning", color: colors.warning },
  { name: "danger", color: colors.danger },
  { name: "info", color: colors.info },
];

export default function DesignGallery() {
  const insets = useSafeAreaInsets();
  const toast = useToast();
  const colors = useThemeColors();
  const mode = useTheme((s) => s.mode);
  const toggleTheme = useTheme((s) => s.toggle);
  const [modalOpen, setModalOpen] = useState(false);
  const [sheetOpen, setSheetOpen] = useState(false);
  const [email, setEmail] = useState("");
  const [pin, setPin] = useState("");

  return (
    <ScrollView
      className="flex-1 bg-bg"
      contentContainerStyle={{
        paddingTop: insets.top + 16,
        paddingBottom: insets.bottom + 40,
        paddingHorizontal: 20,
      }}>
      {/* Header */}
      <View className="mb-8 flex-row items-center gap-4">
        <Image
          source={require("@/assets/images/logo.png")}
          style={{ width: 56, height: 56 }}
          contentFit="contain"
        />
        <View className="flex-1">
          <AppText variant="title">Magic Bill</AppText>
          <AppText variant="caption">Design system — Phase A review</AppText>
        </View>
        <Pressable
          onPress={toggleTheme}
          accessibilityLabel="Toggle light/dark theme"
          className="h-11 w-11 items-center justify-center rounded-full border border-line-strong bg-surface"
          style={({ pressed }) => ({ opacity: pressed ? 0.7 : 1 })}>
          {mode === "dark" ? (
            <Sun size={20} color={colors.warning} />
          ) : (
            <Moon size={20} color={colors.indigo} />
          )}
        </Pressable>
      </View>

      <Section title="Colors">
        <View className="flex-row flex-wrap gap-3">
          {swatches(colors).map((s) => (
            <View key={s.name} className="items-center gap-1.5">
              <View
                className="h-14 w-14 rounded-xl border border-line-strong"
                style={{ backgroundColor: s.color }}
              />
              <AppText variant="caption">{s.name}</AppText>
            </View>
          ))}
        </View>
      </Section>

      <Section title="Typography">
        <Card className="gap-2">
          <AppText variant="display">₹12,480</AppText>
          <AppText variant="title">Screen title</AppText>
          <AppText variant="heading">Section heading</AppText>
          <AppText variant="body">
            Body text — today's sales at a glance, readable and calm.
          </AppText>
          <AppText variant="label">Form label / emphasized small</AppText>
          <AppText variant="caption">Caption — last updated 2 min ago</AppText>
          <AppText variant="mono">MONO 0123456789 ────── ₹450.00</AppText>
        </Card>
      </Section>

      <Section title="Buttons">
        <View className="gap-3">
          <Button
            title="Primary — Sign in"
            onPress={() => toast.show("Primary pressed", "success")}
          />
          <Button
            title="Secondary — Export CSV"
            variant="secondary"
            onPress={() => toast.show("Secondary pressed")}
          />
          <View className="flex-row gap-3">
            <Button
              title="Ghost"
              variant="ghost"
              size="sm"
              className="flex-1"
              onPress={() => toast.show("Ghost pressed")}
            />
            <Button
              title="Danger"
              variant="danger"
              size="sm"
              className="flex-1"
              onPress={() => toast.show("Careful!", "error")}
            />
          </View>
          <Button title="Loading state" loading onPress={() => {}} />
          <Button
            title="Add Staff"
            size="lg"
            icon={<Plus size={20} color={colors.bgDeep} strokeWidth={2.5} />}
            onPress={() => toast.show("With icon", "success")}
          />
        </View>
      </Section>

      <Section title="Inputs">
        <Card className="gap-4">
          <Input
            label="Email"
            placeholder="owner@restaurant.com"
            keyboardType="email-address"
            autoCapitalize="none"
            value={email}
            onChangeText={setEmail}
          />
          <Input
            label="4-digit PIN"
            placeholder="••••"
            secureTextEntry
            keyboardType="number-pad"
            maxLength={4}
            value={pin}
            onChangeText={setPin}
            hint="Staff enter this PIN to log in"
          />
          <Input
            label="Restaurant Code"
            placeholder="HH-4829"
            error="Invalid code or PIN"
          />
        </Card>
      </Section>

      <Section title="Badges & Avatars">
        <Card className="gap-4">
          <View className="flex-row flex-wrap gap-2">
            <Badge label="Active" tone="success" />
            <Badge label="Grace period" tone="warning" />
            <Badge label="Expired" tone="danger" />
            <Badge label="Trial" tone="info" />
            <Badge label="Manager" tone="accent" />
            <Badge label="Inactive" tone="neutral" />
          </View>
          <View className="flex-row items-center gap-3">
            <Avatar name="Sachin Kulal" size="lg" />
            <Avatar name="Priya M" />
            <Avatar name="Ravi" />
            <Avatar name="Anu D" size="sm" />
          </View>
        </Card>
      </Section>

      <Section title="Stat cards (dashboard preview)">
        <View className="flex-row gap-3">
          <Card className="flex-1">
            <View className="mb-2 h-9 w-9 items-center justify-center rounded-lg bg-accent-soft">
              <IndianRupee size={18} color={colors.accentBright} />
            </View>
            <AppText variant="caption">Today's revenue</AppText>
            <AppText className="font-sans-extrabold text-2xl text-ink">
              ₹12,480
            </AppText>
            <AppText variant="caption" className="text-success">
              ▲ 14% vs yesterday
            </AppText>
          </Card>
          <Card className="flex-1">
            <View className="mb-2 h-9 w-9 items-center justify-center rounded-lg bg-info/15">
              <Receipt size={18} color={colors.info} />
            </View>
            <AppText variant="caption">Bills</AppText>
            <AppText className="font-sans-extrabold text-2xl text-ink">
              86
            </AppText>
            <AppText variant="caption">avg ₹145 / bill</AppText>
          </Card>
        </View>
      </Section>

      <Section title="Overlays">
        <View className="gap-3">
          <Button
            title="Open modal"
            variant="secondary"
            onPress={() => setModalOpen(true)}
          />
          <Button
            title="Open bottom sheet"
            variant="secondary"
            onPress={() => setSheetOpen(true)}
          />
          <Button
            title="Show toast"
            variant="secondary"
            onPress={() => toast.show("Report exported as PDF", "success")}
          />
        </View>
      </Section>

      <Section title="Empty state">
        <Card flush>
          <EmptyState
            icon={BarChart3}
            title="No sales yet today"
            message="Bills from your POS will appear here as they sync."
            action={
              <Button title="Refresh" variant="ghost" size="sm" onPress={() => {}} />
            }
          />
        </Card>
      </Section>

      <Section title="Loading">
        <Card>
          <LoadingSpinner label="Fetching reports…" />
        </Card>
      </Section>

      {/* Overlay instances */}
      <AppModal
        visible={modalOpen}
        onClose={() => setModalOpen(false)}
        title="Remove staff member?"
        actions={
          <>
            <Button
              title="Cancel"
              variant="ghost"
              size="sm"
              onPress={() => setModalOpen(false)}
            />
            <Button
              title="Remove"
              variant="danger"
              size="sm"
              onPress={() => {
                setModalOpen(false);
                toast.show("Staff removed", "success");
              }}
            />
          </>
        }>
        <AppText className="text-sm text-ink-muted">
          Priya will lose access immediately. Their PIN will stop working on
          all devices.
        </AppText>
      </AppModal>

      <BottomSheet
        visible={sheetOpen}
        onClose={() => setSheetOpen(false)}
        title="Add staff member">
        <View className="gap-4 pb-2">
          <Input label="Name" placeholder="e.g. Priya" />
          <Input label="Role label" placeholder='e.g. "Manager", "Waiter", "Brother"' />
          <View className="flex-row items-center gap-3">
            <View className="h-10 w-10 items-center justify-center rounded-lg bg-accent-soft">
              <Users size={18} color={colors.accentBright} />
            </View>
            <AppText className="flex-1 text-sm text-ink-muted">
              Permissions checklist will appear here in Phase D
            </AppText>
          </View>
          <Button
            title="Save"
            onPress={() => {
              setSheetOpen(false);
              toast.show("Saved", "success");
            }}
          />
        </View>
      </BottomSheet>
    </ScrollView>
  );
}
