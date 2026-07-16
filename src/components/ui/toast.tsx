import { CheckCircle2, Info, XCircle } from "lucide-react-native";
import {
  createContext,
  useCallback,
  useContext,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { Animated, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { colors } from "@/constants/theme";

import { AppText } from "./text";

type ToastTone = "success" | "error" | "info";

interface ToastState {
  message: string;
  tone: ToastTone;
}

interface ToastContextValue {
  show: (message: string, tone?: ToastTone) => void;
}

const ToastContext = createContext<ToastContextValue>({ show: () => {} });

export function useToast() {
  return useContext(ToastContext);
}

const toneIcon = (tone: ToastTone) => {
  switch (tone) {
    case "success":
      return <CheckCircle2 size={18} color={colors.success} />;
    case "error":
      return <XCircle size={18} color={colors.danger} />;
    default:
      return <Info size={18} color={colors.info} />;
  }
};

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<ToastState | null>(null);
  const anim = useRef(new Animated.Value(0)).current;
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const insets = useSafeAreaInsets();

  const show = useCallback(
    (message: string, tone: ToastTone = "info") => {
      if (timer.current) clearTimeout(timer.current);
      setToast({ message, tone });
      Animated.spring(anim, {
        toValue: 1,
        useNativeDriver: true,
        speed: 24,
        bounciness: 6,
      }).start();
      timer.current = setTimeout(() => {
        Animated.timing(anim, {
          toValue: 0,
          duration: 160,
          useNativeDriver: true,
        }).start(() => setToast(null));
      }, 2600);
    },
    [anim]
  );

  return (
    <ToastContext.Provider value={{ show }}>
      {children}
      {toast ? (
        <Animated.View
          pointerEvents="none"
          style={{
            position: "absolute",
            left: 16,
            right: 16,
            bottom: insets.bottom + 24,
            opacity: anim,
            transform: [
              {
                translateY: anim.interpolate({
                  inputRange: [0, 1],
                  outputRange: [16, 0],
                }),
              },
            ],
          }}>
          <View className="flex-row items-center gap-2.5 rounded-2xl border border-line-strong bg-surface-2 px-4 py-3.5 shadow-lg">
            {toneIcon(toast.tone)}
            <AppText className="flex-1 font-sans-medium text-sm">
              {toast.message}
            </AppText>
          </View>
        </Animated.View>
      ) : null}
    </ToastContext.Provider>
  );
}
