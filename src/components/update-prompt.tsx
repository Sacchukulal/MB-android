/**
 * Update UI: a modal when a new release is found (dismissable per version)
 * and a slim banner that lingers after dismissal. Mounted once at the root.
 */
import { Download, Rocket } from "lucide-react-native";
import { useEffect } from "react";
import { Linking, Pressable, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { AppModal, AppText, Button } from "@/components/ui";
import { installedVersion, useUpdate } from "@/lib/update";
import { useThemeColors } from "@/stores/theme";

export function UpdatePrompt() {
  const colors = useThemeColors();
  const insets = useSafeAreaInsets();
  const available = useUpdate((s) => s.available);
  const dismissed = useUpdate((s) => s.dismissed);
  const dismiss = useUpdate((s) => s.dismiss);
  const check = useUpdate((s) => s.check);

  // Launch check — fire and forget; failures are silent.
  useEffect(() => {
    check();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const openApk = () => {
    if (available) Linking.openURL(available.apk_url);
  };

  if (!available) return null;

  return (
    <>
      {/* Persistent subtle banner once the modal was dismissed */}
      {dismissed ? (
        <Pressable
          onPress={openApk}
          className="absolute left-4 right-4 z-50 flex-row items-center gap-2.5 rounded-2xl border border-line-strong bg-surface-2 px-4 py-2.5 shadow-lg"
          style={{ top: insets.top + 6 }}>
          <Download size={15} color={colors.accentBright} />
          <AppText className="flex-1 font-sans-medium text-xs text-ink">
            Magic Bill {available.version} is available — tap to update
          </AppText>
        </Pressable>
      ) : null}

      {/* Update modal */}
      <AppModal
        visible={!dismissed}
        onClose={dismiss}
        title={`Update available — v${available.version}`}
        actions={
          <>
            <Button title="Later" variant="ghost" size="sm" onPress={dismiss} />
            <Button
              title="Update now"
              size="sm"
              icon={<Download size={15} color={colors.bgDeep} />}
              onPress={openApk}
            />
          </>
        }>
        <View className="gap-3">
          <View className="flex-row items-center gap-2">
            <Rocket size={15} color={colors.accentBright} />
            <AppText variant="caption">
              You're on v{installedVersion()}
            </AppText>
          </View>
          {available.release_notes ? (
            <AppText className="text-sm leading-5 text-ink-muted">
              {available.release_notes}
            </AppText>
          ) : null}
          <AppText variant="caption">
            The APK downloads in your browser — open it and tap Install. If
            Android asks about unknown sources, allow installs from your
            browser once.
          </AppText>
        </View>
      </AppModal>
    </>
  );
}
