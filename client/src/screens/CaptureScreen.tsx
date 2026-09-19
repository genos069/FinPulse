import { useEffect, useState } from "react";
import { Image, Platform, View, AppState } from "react-native";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import {
  AudioModule,
  RecordingPresets,
  setAudioModeAsync,
  useAudioRecorder,
  useAudioRecorderState,
} from "expo-audio";
import * as ImagePicker from "expo-image-picker";
import type { RootStackParams } from "../navigation/types";
import {
  Screen,
  T,
  Card,
  Field,
  Button,
  Note,
  ErrorText,
  Row,
} from "../components/ui";
import { useApp } from "../store/AppProvider";
import { captureApiUrl, extractText } from "../services/captureApi";
import { parseAlert } from "../utils/imports";
export function CaptureScreen({
  route,
  navigation,
}: NativeStackScreenProps<RootStackParams, "Capture">) {
  const { mode } = route.params,
    { state } = useApp(),
    [text, setText] = useState(""),
    [error, setError] = useState<string | null>(null),
    [busy, setBusy] = useState(false),
    [asset, setAsset] = useState<ImagePicker.ImagePickerAsset | null>(null);
  const recorder = useAudioRecorder(RecordingPresets.HIGH_QUALITY),
    recording = useAudioRecorderState(recorder);
  useEffect(() => {
    const sub = AppState.addEventListener("change", (value) => {
      if (value === "background" && recorder.isRecording)
        void recorder.stop().catch(() => {});
    });
    return () => {
      sub.remove();
      if (recorder.isRecording) void recorder.stop().catch(() => {});
      void setAudioModeAsync({ allowsRecording: false }).catch(() => {});
    };
  }, [recorder]);
  async function toggleRecording() {
    setError(null);
    setBusy(true);
    try {
      if (recording.isRecording) {
        await recorder.stop();
        await setAudioModeAsync({ allowsRecording: false });
        if (!recorder.uri) throw new Error("No recording was captured.");
        setText(
          await extractText(
            "voice",
            recorder.uri,
            Platform.OS === "web" ? "audio/webm" : "audio/mp4",
            Platform.OS === "web" ? "expense.webm" : "expense.m4a",
          ),
        );
      } else {
        const permission = await AudioModule.requestRecordingPermissionsAsync();
        if (!permission.granted)
          throw new Error(
            "Microphone permission was not granted. You can still type the expense.",
          );
        await setAudioModeAsync({
          allowsRecording: true,
          playsInSilentMode: true,
        });
        await recorder.prepareToRecordAsync();
        recorder.record();
      }
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  async function pick(camera: boolean) {
    setError(null);
    setBusy(true);
    try {
      if (camera) {
        const permission = await ImagePicker.requestCameraPermissionsAsync();
        if (!permission.granted)
          throw new Error(
            "Camera permission was not granted. Choose an existing receipt instead.",
          );
      }
      const options: ImagePicker.ImagePickerOptions = {
        mediaTypes: ["images"],
        quality: 0.8,
      };
      const result = camera
        ? await ImagePicker.launchCameraAsync(options)
        : await ImagePicker.launchImageLibraryAsync(options);
      if (!result.canceled) setAsset(result.assets[0]);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  async function scan() {
    if (!asset) return;
    setBusy(true);
    setError(null);
    try {
      setText(
        await extractText(
          "receipt",
          asset.uri,
          asset.mimeType ?? "image/jpeg",
          asset.fileName ?? "receipt.jpg",
        ),
      );
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  function review() {
    const row = parseAlert(
      text,
      state.accounts.find((a) => a.type === "bank")?.id ?? state.accounts[0].id,
    );
    if (!row) {
      setError(
        "Include an amount and description, for example “paid 420 rupees to Swiggy by UPI”. You can also open a manual entry below.",
      );
      return;
    }
    navigation.navigate("Transaction", {
      draft: { ...row, source: mode, importKey: undefined },
    });
  }
  return (
    <Screen>
      <T size={27} bold style={{ marginBottom: 12 }}>
        {mode === "voice"
          ? "Say it. Then review it."
          : "Turn a receipt into an entry."}
      </T>
      <Note>
        {captureApiUrl
          ? "When you tap Transcribe or Read receipt, the recording or photo is sent to your configured capture service. Review the extracted text before saving."
          : "Capture service is not connected. You can type or paste the transaction below; nothing will be uploaded."}
      </Note>
      {mode === "voice" ? (
        <Card>
          <T size={42} style={{ textAlign: "center" }}>
            🎙️
          </T>
          <T
            size={12}
            muted
            style={{ textAlign: "center", marginVertical: 15 }}
          >
            {recording.isRecording
              ? `Recording · ${Math.round(recording.durationMillis / 1000)}s`
              : "“Paid four hundred and twenty rupees to Swiggy by UPI.”"}
          </T>
          <Button
            title={
              busy
                ? "Working…"
                : recording.isRecording
                  ? "Stop & transcribe"
                  : "Record voice"
            }
            disabled={!captureApiUrl || busy}
            onPress={() => void toggleRecording()}
          />
        </Card>
      ) : (
        <Card>
          <Row>
            <Button
              title="Camera"
              variant="secondary"
              disabled={busy}
              onPress={() => void pick(true)}
              style={{ flex: 1 }}
            />
            <Button
              title="Choose image"
              variant="secondary"
              disabled={busy}
              onPress={() => void pick(false)}
              style={{ flex: 1 }}
            />
          </Row>
          {asset ? (
            <View style={{ marginTop: 14 }}>
              <Image
                source={{ uri: asset.uri }}
                accessibilityLabel="Selected receipt"
                style={{
                  height: 230,
                  width: "100%",
                  borderRadius: 12,
                  marginBottom: 14,
                }}
                resizeMode="contain"
              />
              <Button
                title={busy ? "Reading…" : "Read receipt"}
                disabled={!captureApiUrl || busy}
                onPress={() => void scan()}
              />
            </View>
          ) : null}
        </Card>
      )}
      <Field
        label="Transaction text — edit if needed"
        value={text}
        onChangeText={setText}
        multiline
        style={{ minHeight: 120 }}
        placeholder="Paid 420 rupees to Swiggy by UPI"
      />
      <ErrorText message={error} />
      <Button
        title="Review extracted details"
        disabled={busy || recording.isRecording}
        onPress={review}
      />
      <Button
        title="Enter details manually"
        variant="secondary"
        style={{ marginTop: 12 }}
        onPress={() => navigation.navigate("Transaction", { type: "expense" })}
      />
    </Screen>
  );
}
