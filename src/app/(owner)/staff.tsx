/** Staff management tab — Phase D builds this. */
import { Users } from "lucide-react-native";
import { ScrollView } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { OwnerHeader } from "@/components/owner-header";
import { Card, EmptyState } from "@/components/ui";

export default function StaffTab() {
  const insets = useSafeAreaInsets();
  return (
    <ScrollView
      className="flex-1 bg-bg"
      contentContainerStyle={{
        paddingTop: insets.top + 12,
        paddingBottom: 32,
        paddingHorizontal: 20,
      }}>
      <OwnerHeader title="Staff" />
      <Card flush>
        <EmptyState
          icon={Users}
          title="Staff management coming in Phase D"
          message="Add staff, set their PINs and control exactly what each person can see."
        />
      </Card>
    </ScrollView>
  );
}
