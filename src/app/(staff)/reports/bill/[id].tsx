/** Staff bill receipt — via staff-data (view_bills); share gated by export_reports. */
import { useLocalSearchParams, useRouter } from "expo-router";
import { ArrowLeft, ReceiptText, Share2 } from "lucide-react-native";
import { useState } from "react";
import { Pressable, ScrollView, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import {
  AppText,
  Button,
  Card,
  EmptyState,
  LoadingSpinner,
  useToast,
} from "@/components/ui";
import { useCachedQuery } from "@/hooks/use-cached-query";
import { shareReceiptPdf } from "@/lib/export";
import { billTime, formatINR } from "@/lib/format";
import { fetchStaffBill } from "@/lib/staff-data";
import { useAuth, useCan } from "@/stores/auth";
import { useThemeColors } from "@/stores/theme";

function Dashes() {
  return (
    <AppText variant="mono" className="text-ink-faint" numberOfLines={1}>
      {"- ".repeat(40)}
    </AppText>
  );
}

function Row({ left, right, bold }: { left: string; right: string; bold?: boolean }) {
  return (
    <View className="flex-row justify-between">
      <AppText variant="mono" className={bold ? "font-sans-bold text-base" : ""}>
        {left}
      </AppText>
      <AppText variant="mono" className={bold ? "font-sans-bold text-base" : ""}>
        {right}
      </AppText>
    </View>
  );
}

export default function StaffBillReceipt() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const colors = useThemeColors();
  const toast = useToast();
  const restaurant = useAuth((s) => s.restaurant);
  const canShare = useCan("export_reports");
  const [sharing, setSharing] = useState(false);

  const q = useCachedQuery(id ? `staffbill.${id}` : null, () =>
    fetchStaffBill(id!)
  );
  const bill = q.data ?? null;

  const doShare = async () => {
    if (!bill) return;
    setSharing(true);
    try {
      await shareReceiptPdf(bill, restaurant?.name ?? "");
    } catch {
      toast.show("Couldn't share receipt", "error");
    } finally {
      setSharing(false);
    }
  };

  return (
    <ScrollView
      className="flex-1 bg-bg"
      contentContainerStyle={{
        paddingTop: insets.top + 8,
        paddingBottom: 32,
        paddingHorizontal: 20,
      }}>
      <View className="mb-5 flex-row items-center justify-between">
        <Pressable
          onPress={() => router.back()}
          hitSlop={12}
          className="h-11 w-11 items-center justify-center rounded-full border border-line bg-surface">
          <ArrowLeft size={20} color={colors.textMuted} />
        </Pressable>
        <AppText variant="heading">Receipt</AppText>
        <View className="w-11" />
      </View>

      {q.loading ? (
        <LoadingSpinner label="Loading bill…" />
      ) : !bill ? (
        <Card flush>
          <EmptyState
            icon={ReceiptText}
            title="Bill not available"
            message={q.error ?? "You may not have permission to view this bill."}
          />
        </Card>
      ) : (
        <>
          <Card className="px-5 py-6">
            <View className="items-center gap-0.5">
              <AppText className="font-sans-bold text-lg text-ink">
                {restaurant?.name ?? "Restaurant"}
              </AppText>
              <AppText variant="mono">
                {bill.bill_number ? `Bill #${bill.bill_number}` : ""}
                {bill.token_number != null ? `  ·  Token ${bill.token_number}` : ""}
              </AppText>
              <AppText variant="mono">{billTime(bill.billed_at)}</AppText>
              {bill.order_type ? (
                <AppText variant="mono">
                  {bill.order_type}
                  {bill.table_number ? `  ·  Table ${bill.table_number}` : ""}
                </AppText>
              ) : null}
              {bill.customer_name ? (
                <AppText variant="mono">Customer: {bill.customer_name}</AppText>
              ) : null}
            </View>

            <View className="my-3">
              <Dashes />
            </View>

            <View className="gap-1.5">
              {(bill.items ?? []).map((it, i) => {
                const price = Number(it.price) || 0;
                const qty = Number(it.quantity) || 0;
                return (
                  <View key={`${it.name}-${i}`}>
                    <Row left={it.name} right={formatINR(price * qty)} />
                    <AppText variant="mono" className="text-ink-faint">
                      {`  ${qty} × ${formatINR(price)}`}
                    </AppText>
                  </View>
                );
              })}
            </View>

            <View className="my-3">
              <Dashes />
            </View>

            <View className="gap-1">
              <Row left="Subtotal" right={formatINR(bill.subtotal ?? 0)} />
              <Row left="GST" right={formatINR(bill.gst ?? 0)} />
              <Row left="TOTAL" right={formatINR(bill.total ?? 0)} bold />
              {bill.payment_mode ? (
                <Row left="Paid by" right={bill.payment_mode} />
              ) : null}
            </View>

            <View className="my-3">
              <Dashes />
            </View>

            <View className="items-center">
              <AppText variant="mono">Thank you! Visit again.</AppText>
              <AppText variant="mono" className="text-ink-faint">
                Powered by Magic Bill
              </AppText>
            </View>
          </Card>

          {canShare ? (
            <Button
              title="Share receipt"
              size="lg"
              className="mt-4"
              loading={sharing}
              icon={<Share2 size={18} color={colors.bgDeep} />}
              onPress={doShare}
            />
          ) : null}
        </>
      )}
    </ScrollView>
  );
}
