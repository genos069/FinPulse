import AsyncStorage from "@react-native-async-storage/async-storage";
import { stateSchema, type AppState } from "../types/models";
const KEY = "@finpulse/state/v1";
let queue: Promise<unknown> = Promise.resolve();
export async function loadState(): Promise<AppState | null> {
  const raw = await AsyncStorage.getItem(KEY);
  if (!raw) return null;
  const parsed = stateSchema.safeParse(JSON.parse(raw));
  if (!parsed.success)
    throw new Error(
      "The saved data could not be read. It has not been overwritten. Restore a valid backup or explicitly reset it.",
    );
  return parsed.data;
}
export function saveState(state: AppState): Promise<void> {
  const snapshot = JSON.stringify(state);
  const write = queue
    .catch(() => {})
    .then(() => AsyncStorage.setItem(KEY, snapshot));
  queue = write;
  return write;
}
export async function clearState() {
  await queue.catch(() => {});
  await AsyncStorage.removeItem(KEY);
}
