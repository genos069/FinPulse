import { Platform } from "react-native";
import * as DocumentPicker from "expo-document-picker";
import { File, Paths } from "expo-file-system";
import * as Sharing from "expo-sharing";
export async function pickTextFile(backup = false): Promise<string | null> {
  const result = await DocumentPicker.getDocumentAsync({
    type: backup
      ? ["application/json", "text/plain"]
      : ["text/*", "application/csv", "application/vnd.ms-excel"],
    copyToCacheDirectory: true,
    multiple: false,
  });
  if (result.canceled) return null;
  const asset = result.assets[0];
  if ((asset.size ?? 0) > 5_000_000)
    throw new Error("Choose a file smaller than 5 MB.");
  if (Platform.OS === "web") {
    if (asset.file) return asset.file.text();
    return (await fetch(asset.uri)).text();
  }
  return new File(asset.uri).text();
}
export async function exportText(name: string, content: string, mime: string) {
  if (Platform.OS === "web") {
    const blob = new Blob([content], { type: mime }),
      url = URL.createObjectURL(blob),
      link = document.createElement("a");
    link.href = url;
    link.download = name;
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    return;
  }
  if (!(await Sharing.isAvailableAsync()))
    throw new Error("Sharing is unavailable on this device.");
  const file = new File(Paths.cache, name);
  file.write(content);
  try {
    await Sharing.shareAsync(file.uri, {
      mimeType: mime,
      dialogTitle: `Export ${name}`,
    });
  } finally {
    if (file.exists) file.delete();
  }
}
