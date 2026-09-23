import { requireOptionalNativeModule } from "expo";
type Speech =
  typeof import("expo-speech-recognition").ExpoSpeechRecognitionModule;
// Expo Go does not include this native module. Keep the typed-entry fallback usable.
export const speech = requireOptionalNativeModule<Speech>(
  "ExpoSpeechRecognition",
);
