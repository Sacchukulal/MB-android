import type { ReactNode } from "react";
import { useEffect, useRef } from "react";
import {
  Animated,
  Dimensions,
  Modal,
  PanResponder,
  Pressable,
  ScrollView,
  View,
} from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { AppText } from "./text";

export interface BottomSheetProps {
  visible: boolean;
  onClose: () => void;
  title?: string;
  children: ReactNode;
  /** Max height as a fraction of the screen (default 0.85). */
  maxHeightRatio?: number;
}

export function BottomSheet({
  visible,
  onClose,
  title,
  children,
  maxHeightRatio = 0.85,
}: BottomSheetProps) {
  const screenH = Dimensions.get("window").height;
  const translateY = useRef(new Animated.Value(screenH)).current;
  const insets = useSafeAreaInsets();

  useEffect(() => {
    if (visible) {
      Animated.spring(translateY, {
        toValue: 0,
        useNativeDriver: true,
        speed: 18,
        bounciness: 2,
      }).start();
    } else {
      translateY.setValue(screenH);
    }
  }, [visible, translateY, screenH]);

  const close = () => {
    Animated.timing(translateY, {
      toValue: screenH,
      duration: 180,
      useNativeDriver: true,
    }).start(onClose);
  };

  const pan = useRef(
    PanResponder.create({
      onMoveShouldSetPanResponder: (_e, g) =>
        g.dy > 6 && Math.abs(g.dy) > Math.abs(g.dx),
      onPanResponderMove: (_e, g) => {
        if (g.dy > 0) translateY.setValue(g.dy);
      },
      onPanResponderRelease: (_e, g) => {
        if (g.dy > 120 || g.vy > 0.8) {
          close();
        } else {
          Animated.spring(translateY, {
            toValue: 0,
            useNativeDriver: true,
            speed: 20,
            bounciness: 4,
          }).start();
        }
      },
    })
  ).current;

  return (
    <Modal
      visible={visible}
      transparent
      animationType="fade"
      statusBarTranslucent
      onRequestClose={close}>
      <View className="flex-1 justify-end bg-black/60">
        <Pressable className="flex-1" onPress={close} />
        <Animated.View
          style={{
            transform: [{ translateY }],
            maxHeight: screenH * maxHeightRatio,
          }}>
          <View className="rounded-t-3xl border border-b-0 border-line bg-surface">
            {/* Drag handle */}
            <View {...pan.panHandlers} className="items-center pb-2 pt-3">
              <View className="h-1.5 w-12 rounded-full bg-line-strong" />
            </View>
            {title ? (
              <View className="border-b border-line px-5 pb-3">
                <AppText variant="heading">{title}</AppText>
              </View>
            ) : null}
            <ScrollView
              className="px-5 pt-4"
              contentContainerStyle={{ paddingBottom: insets.bottom + 24 }}
              keyboardShouldPersistTaps="handled">
              {children}
            </ScrollView>
          </View>
        </Animated.View>
      </View>
    </Modal>
  );
}
