import { Platform } from "react-native";
import { z } from "zod";
export const captureApiUrl = (process.env.EXPO_PUBLIC_API_URL ?? "").replace(
  /\/$/,
  "",
);
const responseSchema = z.object({ text: z.string().min(1).max(20000) });
export async function extractText(
  kind: "voice" | "receipt",
  uri: string,
  mime: string,
  name: string,
): Promise<string> {
  if (!captureApiUrl)
    throw new Error(
      "No capture service is configured. Type or paste the transaction below.",
    );
  const parsed = new URL(captureApiUrl);
  if (
    parsed.protocol !== "https:" &&
    !(
      __DEV__ &&
      ["localhost", "127.0.0.1", "10.0.2.2"].includes(parsed.hostname)
    )
  )
    throw new Error("The capture service must use HTTPS.");
  const body = new FormData();
  if (Platform.OS === "web") {
    const blob = await (await fetch(uri)).blob();
    body.append("file", blob, name);
  } else body.append("file", { uri, type: mime, name } as unknown as Blob);
  const controller = new AbortController(),
    timer = setTimeout(() => controller.abort(), 30000);
  try {
    const result = await fetch(
      `${captureApiUrl}/${kind === "voice" ? "speech/transcribe" : "receipts/parse"}`,
      { method: "POST", body, signal: controller.signal },
    );
    if (!result.ok)
      throw new Error(
        `The capture service returned ${result.status}. Try again or enter the details manually.`,
      );
    const json = responseSchema.safeParse(await result.json());
    if (!json.success)
      throw new Error(
        "The service returned an invalid response. Expected a text field.",
      );
    return json.data.text;
  } finally {
    clearTimeout(timer);
  }
}
