import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import type { RootStackParams } from "../navigation/types";
import { HealthTool } from "./tools/HealthTool";
import { LessonsTool } from "./tools/LessonsTool";
import { RiskTool } from "./tools/RiskTool";
import { ReportsTool } from "./tools/ReportsTool";
import { NotificationsTool } from "./tools/NotificationsTool";
import { FestivalsTool, SplitsTool } from "./tools/PlanningTools";
import { AskTool } from "./tools/AskTool";
import { AllocationTool } from "./tools/AllocationTool";
import { PrivacyTool } from "./tools/PrivacyTool";
import { SecurityTool } from "./tools/SecurityTool";
const screens = {
  health: HealthTool,
  lessons: LessonsTool,
  risk: RiskTool,
  reports: ReportsTool,
  notifications: NotificationsTool,
  festivals: FestivalsTool,
  splits: SplitsTool,
  ask: AskTool,
  allocation: AllocationTool,
  privacy: PrivacyTool,
  security: SecurityTool,
};
export function ToolsScreen({
  route,
}: NativeStackScreenProps<RootStackParams, "Tools">) {
  const Component = screens[route.params.tool];
  return <Component />;
}
