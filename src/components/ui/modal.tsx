import type { ReactNode } from "react";
import { useEffect, useRef } from "react";
import {
  Animated,
  Modal as RNModal,
  Pressable,
  View,
} from "react-native";

import { cn } from "@/lib/cn";

import { AppText } from "./text";

export interface AppModalProps {
  visible: boolean;
  onClose: () => void;
  title?: string;
  children: ReactNode;
  /** Footer row, usually Buttons. */
  actions?: ReactNode;
  className?: string;
}

export function AppModal({
  visible,
  onClose,
  title,
  children,
  actions,
  className,
}: AppModalProps) {
  const anim = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    if (visible) {
      Animated.spring(anim, {
        toValue: 1,
        useNativeDriver: true,
        speed: 24,
        bounciness: 4,
      }).start();
    } else {
      anim.setValue(0);
    }
  }, [visible, anim]);

  return (
    <RNModal
      visible={visible}
      transparent
      animationType="fade"
      statusBarTranslucent
      onRequestClose={onClose}>
      <Pressable
        className="flex-1 items-center justify-center bg-black/60 px-6"
        onPress={onClose}>
        <Animated.View
          style={{
            width: "100%",
            opacity: anim,
            transform: [
              {
                translateY: anim.interpolate({
                  inputRange: [0, 1],
                  outputRange: [12, 0],
                }),
              },
              {
                scale: anim.interpolate({
                  inputRange: [0, 1],
                  outputRange: [0.97, 1],
                }),
              },
            ],
          }}>
          {/* Stop backdrop press from closing when tapping the card itself */}
          <Pressable onPress={() => {}}>
            <View
              className={cn(
                "rounded-card border border-line bg-surface p-5",
                className
              )}>
              {title ? (
                <AppText variant="heading" className="mb-3">
                  {title}
                </AppText>
              ) : null}
              {children}
              {actions ? (
                <View className="mt-5 flex-row justify-end gap-3">
                  {actions}
                </View>
              ) : null}
            </View>
          </Pressable>
        </Animated.View>
      </Pressable>
    </RNModal>
  );
}
