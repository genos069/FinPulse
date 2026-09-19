import { useNavigation } from "@react-navigation/native";
import type { NativeStackNavigationProp } from "@react-navigation/native-stack";
import type { RootStackParams } from "../navigation/types";
export const useNav = () =>
  useNavigation<NativeStackNavigationProp<RootStackParams>>();
