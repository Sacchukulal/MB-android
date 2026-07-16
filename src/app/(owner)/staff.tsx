/** Staff management — owner-only. Add staff, control every permission, PINs. */
import * as Clipboard from "expo-clipboard";
import {
  Copy,
  KeyRound,
  Plus,
  ShieldOff,
  Trash2,
  UserRoundPlus,
  Users,
} from "lucide-react-native";
import { useCallback, useState } from "react";
import { Pressable, RefreshControl, ScrollView, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { OwnerHeader } from "@/components/owner-header";
import { PermissionEditor } from "@/components/permission-editor";
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
import { useCachedQuery } from "@/hooks/use-cached-query";
import { timeAgo } from "@/lib/format";
import type { PermissionMap } from "@/lib/permissions";
import {
  createStaff,
  ensureRestaurantCode,
  generatePin,
  listStaff,
  removeStaff,
  resetStaffPin,
  StaffError,
  updateStaff,
  type StaffRow,
} from "@/lib/staff";
import { useAuth } from "@/stores/auth";
import { useThemeColors } from "@/stores/theme";

interface EditorState {
  staff: StaffRow | null; // null = creating new
  name: string;
  roleLabel: string;
  pin: string; // only for create
  permissions: PermissionMap;
}

export default function StaffTab() {
  const insets = useSafeAreaInsets();
  const colors = useThemeColors();
  const toast = useToast();
  const restaurant = useAuth((s) => s.restaurant);
  const licenseKey = restaurant?.licenseKey ?? null;

  const q = useCachedQuery(
    licenseKey ? `stafflist.${licenseKey}` : null,
    () => listStaff(licenseKey!)
  );

  const [editor, setEditor] = useState<EditorState | null>(null);
  const [saving, setSaving] = useState(false);
  const [pinReveal, setPinReveal] = useState<{ name: string; pin: string } | null>(null);
  const [confirmRemove, setConfirmRemove] = useState<StaffRow | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const openCreate = () =>
    setEditor({
      staff: null,
      name: "",
      roleLabel: "",
      pin: generatePin(),
      permissions: { view_dashboard: true },
    });

  const openEdit = (s: StaffRow) =>
    setEditor({
      staff: s,
      name: s.name,
      roleLabel: s.role_label,
      pin: "",
      permissions: { ...s.permissions },
    });

  const save = async () => {
    if (!editor || !licenseKey) return;
    if (!editor.name.trim()) {
      toast.show("Enter a name", "error");
      return;
    }
    if (!editor.staff && !/^\d{4}$/.test(editor.pin)) {
      toast.show("PIN must be exactly 4 digits", "error");
      return;
    }
    setSaving(true);
    try {
      if (editor.staff) {
        await updateStaff(licenseKey, editor.staff.id, {
          name: editor.name.trim(),
          roleLabel: editor.roleLabel.trim() || "Staff",
          permissions: editor.permissions,
        });
        toast.show("Staff updated", "success");
        setEditor(null);
      } else {
        const created = await createStaff(licenseKey, {
          name: editor.name.trim(),
          roleLabel: editor.roleLabel.trim() || "Staff",
          pin: editor.pin,
          permissions: editor.permissions,
        });
        setEditor(null);
        setPinReveal({ name: created.name, pin: editor.pin });
      }
      await q.refresh();
    } catch (e) {
      toast.show(e instanceof StaffError ? e.message : "Save failed", "error");
    } finally {
      setSaving(false);
    }
  };

  const toggleActive = async (s: StaffRow) => {
    if (!licenseKey) return;
    setBusyId(s.id);
    try {
      await updateStaff(licenseKey, s.id, { isActive: !s.is_active });
      toast.show(
        s.is_active ? `${s.name} deactivated — access revoked` : `${s.name} reactivated`,
        "success"
      );
      await q.refresh();
      setEditor(null);
    } catch (e) {
      toast.show(e instanceof StaffError ? e.message : "Failed", "error");
    } finally {
      setBusyId(null);
    }
  };

  const doResetPin = async (s: StaffRow) => {
    if (!licenseKey) return;
    const pin = generatePin();
    setBusyId(s.id);
    try {
      await resetStaffPin(licenseKey, s.id, pin);
      setEditor(null);
      setPinReveal({ name: s.name, pin });
    } catch (e) {
      toast.show(e instanceof StaffError ? e.message : "Failed", "error");
    } finally {
      setBusyId(null);
    }
  };

  const doRemove = async () => {
    if (!licenseKey || !confirmRemove) return;
    const target = confirmRemove;
    setConfirmRemove(null);
    try {
      await removeStaff(licenseKey, target.id);
      toast.show(`${target.name} removed`, "success");
      setEditor(null);
      await q.refresh();
    } catch (e) {
      toast.show(e instanceof StaffError ? e.message : "Failed", "error");
    }
  };

  const copyCode = useCallback(async () => {
    let code = q.data?.restaurantCode;
    if (!code && licenseKey) {
      try {
        code = await ensureRestaurantCode(licenseKey);
        await q.refresh();
      } catch {
        toast.show("Couldn't generate code", "error");
        return;
      }
    }
    if (code) {
      await Clipboard.setStringAsync(code);
      toast.show("Restaurant code copied", "success");
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [q.data?.restaurantCode, licenseKey]);

  return (
    <View className="flex-1 bg-bg">
      <ScrollView
        refreshControl={
          <RefreshControl
            refreshing={q.refreshing && !q.loading}
            onRefresh={q.refresh}
            tintColor={colors.accentBright}
            colors={[colors.accent]}
            progressBackgroundColor={colors.surface}
          />
        }
        contentContainerStyle={{
          paddingTop: insets.top + 12,
          paddingBottom: 110,
          paddingHorizontal: 20,
        }}>
        <OwnerHeader title="Staff" />

        {/* Restaurant code */}
        <Card className="mb-4">
          <View className="flex-row items-center justify-between">
            <View className="flex-1 pr-3">
              <AppText variant="caption">RESTAURANT CODE</AppText>
              <AppText className="mt-0.5 font-sans-extrabold text-2xl tracking-widest text-accent-bright">
                {q.data?.restaurantCode ?? "——"}
              </AppText>
            </View>
            <Button
              title="Copy"
              variant="secondary"
              size="sm"
              icon={<Copy size={14} color={colors.accentBright} />}
              onPress={copyCode}
            />
          </View>
          <AppText variant="caption" className="mt-3">
            Give this code and the staff member's PIN to them. They enter it in
            the Magic Bill app to log in.
          </AppText>
        </Card>

        {q.loading ? (
          <LoadingSpinner label="Loading staff…" />
        ) : q.data && q.data.staff.length === 0 ? (
          <Card flush>
            <EmptyState
              icon={Users}
              title="No staff yet"
              message="Add your first staff member — you control exactly what they can see."
              action={
                <Button
                  title="Add Staff"
                  size="sm"
                  icon={<UserRoundPlus size={16} color={colors.bgDeep} />}
                  onPress={openCreate}
                />
              }
            />
          </Card>
        ) : q.data ? (
          <View className="gap-3">
            {q.data.staff.map((s) => (
              <Pressable key={s.id} onPress={() => openEdit(s)}>
                <Card
                  className={`flex-row items-center gap-3 ${s.is_active ? "" : "opacity-60"}`}>
                  <Avatar name={s.name} />
                  <View className="flex-1">
                    <AppText className="font-sans-semibold text-base text-ink">
                      {s.name}
                    </AppText>
                    <View className="mt-1 flex-row items-center gap-2">
                      <Badge label={s.role_label} tone="accent" />
                      {!s.is_active ? (
                        <Badge label="Inactive" tone="danger" />
                      ) : null}
                    </View>
                  </View>
                  <AppText variant="caption">
                    {s.last_login
                      ? `seen ${timeAgo(new Date(s.last_login).getTime())}`
                      : "never logged in"}
                  </AppText>
                </Card>
              </Pressable>
            ))}
          </View>
        ) : null}
      </ScrollView>

      {/* FAB */}
      <Pressable
        onPress={openCreate}
        accessibilityLabel="Add staff"
        className="absolute bottom-6 right-5 h-14 w-14 items-center justify-center rounded-2xl bg-accent shadow-lg"
        style={({ pressed }) => ({ transform: [{ scale: pressed ? 0.94 : 1 }] })}>
        <Plus size={26} color={colors.bgDeep} strokeWidth={2.5} />
      </Pressable>

      {/* Add / Edit sheet */}
      <BottomSheet
        visible={editor !== null}
        onClose={() => setEditor(null)}
        title={editor?.staff ? `Edit ${editor.staff.name}` : "Add staff member"}>
        {editor ? (
          <View className="gap-4 pb-2">
            <Input
              label="Name"
              placeholder="e.g. Priya"
              value={editor.name}
              onChangeText={(t) => setEditor({ ...editor, name: t })}
            />
            <Input
              label="Role label"
              placeholder='Anything — "Waiter", "Manager", "Brother"'
              value={editor.roleLabel}
              onChangeText={(t) => setEditor({ ...editor, roleLabel: t })}
            />
            {!editor.staff ? (
              <Input
                label="4-digit PIN"
                keyboardType="number-pad"
                maxLength={4}
                value={editor.pin}
                onChangeText={(t) =>
                  setEditor({ ...editor, pin: t.replace(/\D/g, "") })
                }
                hint="Auto-generated — change it if you like"
              />
            ) : null}

            <View>
              <AppText variant="label" className="mb-2">
                Permissions
              </AppText>
              <PermissionEditor
                value={editor.permissions}
                onChange={(permissions) => setEditor({ ...editor, permissions })}
              />
            </View>

            <Button
              title={editor.staff ? "Save changes" : "Add staff"}
              loading={saving}
              onPress={save}
            />

            {editor.staff ? (
              <View className="gap-3 border-t border-line pt-4">
                <Button
                  title="Reset PIN (generates a new one)"
                  variant="secondary"
                  size="sm"
                  loading={busyId === editor.staff.id}
                  icon={<KeyRound size={15} color={colors.accentBright} />}
                  onPress={() => doResetPin(editor.staff!)}
                />
                <Button
                  title={editor.staff.is_active ? "Deactivate (revoke access)" : "Reactivate"}
                  variant="secondary"
                  size="sm"
                  icon={<ShieldOff size={15} color={colors.warning} />}
                  onPress={() => toggleActive(editor.staff!)}
                />
                <Button
                  title="Remove permanently"
                  variant="danger"
                  size="sm"
                  icon={<Trash2 size={15} color={colors.danger} />}
                  onPress={() => setConfirmRemove(editor.staff)}
                />
              </View>
            ) : null}
          </View>
        ) : null}
      </BottomSheet>

      {/* PIN reveal */}
      <AppModal
        visible={pinReveal !== null}
        onClose={() => setPinReveal(null)}
        title={pinReveal ? `PIN for ${pinReveal.name}` : ""}
        actions={
          <Button
            title="Done"
            size="sm"
            onPress={() => setPinReveal(null)}
          />
        }>
        <View className="items-center py-2">
          <AppText className="font-sans-extrabold text-5xl tracking-[8px] text-accent-bright">
            {pinReveal?.pin}
          </AppText>
          <AppText variant="caption" className="mt-3 text-center">
            Write this down and share it with {pinReveal?.name}.{"\n"}
            For security it won't be shown again — but you can always reset it.
          </AppText>
        </View>
      </AppModal>

      {/* Remove confirm */}
      <AppModal
        visible={confirmRemove !== null}
        onClose={() => setConfirmRemove(null)}
        title={`Remove ${confirmRemove?.name}?`}
        actions={
          <>
            <Button
              title="Cancel"
              variant="ghost"
              size="sm"
              onPress={() => setConfirmRemove(null)}
            />
            <Button title="Remove" variant="danger" size="sm" onPress={doRemove} />
          </>
        }>
        <AppText className="text-sm text-ink-muted">
          They lose access immediately and their PIN stops working. This can't
          be undone.
        </AppText>
      </AppModal>
    </View>
  );
}
