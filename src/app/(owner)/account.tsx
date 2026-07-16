/** Account tab — Phase F builds plan status; logout lives here meanwhile. */
import { CircleUserRound } from "lucide-react-native";
import { ScrollView } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { OwnerHeader } from "@/components/owner-header";
import { Card, EmptyState } from "@/components/ui";

export default function AccountTab() {
  const insets = useSafeAreaInsets();
  return (
    <ScrollView
      className="flex-1 bg-bg"
      contentContainerStyle={{
        paddingTop: insets.top + 12,
        paddingBottom: 32,
        paddingHorizontal: 20,
      }}>
      <OwnerHeader title="Account" showLogout />
      <Card flush>
        <EmptyState
          icon={CircleUserRound}
          title="Plan & subscription coming in Phase F"
          message="Restaurant info, plan status and device binding will appear here."
        />
      </Card>
    </ScrollView>
  );
}
