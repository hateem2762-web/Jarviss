import type {
  BroadcastNewsItem,
  InstalledAppInfo,
  JarvisAiReply,
  JarvisMessage,
  NewsBroadcastResponse,
  ScreenNoteAiResult,
  WhatsAppEvaluation,
} from '../types/jarvis';

async function parseApiJson<T>(response: Response, fallbackError: string): Promise<T> {
  const rawText = await response.text();
  let parsed: (T & { error?: string }) | null = null;
  try {
    parsed = rawText ? (JSON.parse(rawText) as T & { error?: string }) : null;
  } catch {
    throw new Error(`${fallbackError} (Sunucu JSON yerine beklenmeyen bir yanıt döndürdü: HTTP ${response.status})`);
  }
  if (!response.ok) {
    throw new Error(parsed?.error || `${fallbackError} (HTTP ${response.status})`);
  }
  if (!parsed) {
    throw new Error(fallbackError);
  }
  return parsed;
}

export async function askJarvisApi(params: {
  userPrompt: string;
  userContext: string;
  recentMessages: JarvisMessage[];
  installedApps: InstalledAppInfo[];
  savedCoordinateLabels: string[];
  activeProfileLabel: string;
}): Promise<JarvisAiReply> {
  const response = await fetch('/api/jarvis/ask', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(params),
  });
  return parseApiJson<JarvisAiReply>(response, 'JARVIS yanıt verirken bir hata oluştu.');
}

export async function evaluateWhatsAppMessageApi(params: {
  senderName: string;
  messageText: string;
  userContext: string;
}): Promise<WhatsAppEvaluation> {
  const response = await fetch('/api/jarvis/evaluate-whatsapp', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(params),
  });
  return parseApiJson<WhatsAppEvaluation>(response, 'WhatsApp mesajı değerlendirilemedi.');
}

export async function generateNoteFromScreenApi(params: {
  extractedText: string;
  screenshotBase64?: string;
  mimeType?: string;
  sourceAppName: string;
}): Promise<ScreenNoteAiResult> {
  const response = await fetch('/api/jarvis/screen-note', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(params),
  });
  return parseApiJson<ScreenNoteAiResult>(response, 'Ekran notu üretilemedi.');
}

export async function fetchNewsBroadcastApi(): Promise<NewsBroadcastResponse> {
  const response = await fetch('/api/jarvis/news-broadcast', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
  });
  return parseApiJson<NewsBroadcastResponse>(response, 'Gündem bülteni yüklenemedi.');
}

export async function elaborateNewsItemApi(params: {
  item: BroadcastNewsItem;
  question?: string;
}): Promise<string> {
  const response = await fetch('/api/jarvis/news-elaborate', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      headline: params.item.headline,
      anchorScript: params.item.anchorScript,
      elaboratedScript: params.item.elaboratedScript,
      sourceName: params.item.sourceName,
      question: params.question ?? '',
    }),
  });
  const data = await parseApiJson<{ detail?: string }>(response, 'Haber detayı üretilemedi.');
  return data.detail || params.item.elaboratedScript || params.item.anchorScript;
}
