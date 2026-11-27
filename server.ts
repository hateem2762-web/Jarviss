import express from 'express';
import type { Request, Response } from 'express';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { GoogleGenAI, Type } from '@google/genai';
import { parseReminderDuration } from './src/utils/reminderDurationParser.ts';
import { fetchOpenRssNewsPool } from './server/rssNewsService.ts';
import {
  formatActionDurationLabel,
  normalizeActionIntent,
  normalizeJarvisMood,
  type BroadcastNewsItem,
  type InstalledAppInfo,
  type JarvisAction,
  type JarvisAiReply,
  type JarvisMessage,
  type NewsBroadcastResponse,
  type NewsCategory,
  type ScreenNoteAiResult,
  type WhatsAppEvaluation,
} from './src/types/jarvis.ts';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const MODEL_NAME = 'gemini-4-hydrogen';

function getAiClient(): GoogleGenAI {
  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) {
    throw new Error(
      'GEMINI_API_KEY ortam değişkeni tanımlı değil. Settings > Secrets panelinden API anahtarını kontrol edin.'
    );
  }
  return new GoogleGenAI({
    apiKey,
    httpOptions: {
      headers: {
        'User-Agent': 'aistudio-build',
      },
    },
  });
}

function buildSystemPrompt(params: {
  userContext: string;
  installedApps: InstalledAppInfo[];
  savedCoordinateLabels: string[];
  activeProfileLabel: string;
}): string {
  const now = new Date();
  const nowFormatted = new Intl.DateTimeFormat('tr-TR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    weekday: 'long',
  }).format(now);

  const contextSection =
    params.userContext.trim() || 'Varsayılan: Kısa, net, Türkçe ve pratik ol.';
  const appsSummary =
    params.installedApps
      .slice(0, 35)
      .map((a) => a.label)
      .join(', ') || 'Ayarlar, Kamera, Chrome, Saat, Takvim';

  const coordsSection =
    params.savedCoordinateLabels.length > 0
      ? `AKTİF OYUN/PROFİL (${params.activeProfileLabel || 'Aktif Profil'}) KAYITLI DOKUNMA ETİKETLERİ: ${params.savedCoordinateLabels.join(', ')}`
      : 'AKTİF OYUN KAYITLI NOKTALARI: Yok';

  return `Sen kullanıcının kişisel, hızlı ve pratik yapay zeka asistanısın (JARVIS).
Amacın uzun uzun konuşmak değil; kullanıcının söylediğini doğru anlamak, gerekirse doğrudan işlem/aksiyon niyeti çıkarmak, gerekirse kısa, net ve işe yarar içerik üretmektir.

GENEL KURALLAR:
1. Gereksiz gevezelik, yapay heyecan veya boş laf yapma. Net, direkt ve işe odaklı ol.
2. Cevapları olabildiğince öz, okunaklı ve hızlı taranabilir tut. Metin üretimi (iş başvurusu, e-posta, özet) istendiğinde doğrudan kullanıma hazır temiz metni reply alanına yaz, ttsSummary alanında 1 cümlelik sesli özet ver.
3. Sesle yazıya çevrilmiş metinlerdeki ufak yazım/noktalama hatalarını mantıksal olarak doğru tahmin et.
4. Bilmediğin veya kesin olmayan bir şeyi uydurma; eksik bilgi varsa tek ve net bir soruyla sor.

HATIRLATICI SÜRE KURALI (ÇOK ÖNEMLİ):
- Kullanıcının belirttiği TAM süreyi çıkar; ASLA kafandan 10 dakikaya yuvarlama!
- Saniye belirtirse (ör. "20 saniye sonra") minutes=null, seconds=20 ver.
- Dakika belirtirse (ör. "3 dakika sonra", "15 dk") minutes=3 veya 15, seconds=null ver.
- Saat belirtirse dakikaya çevir ("yarım saat" -> minutes=30, "1 saat" -> minutes=60, "1.5 saat" -> minutes=90).
- Saat formatı verirse (ör. "saat 16:30'da") güncel saatten kalan dakikayı hesapla.
- Sadece kullanıcı hiçbir süre vermediyse minutes=10 ver.

DONANIM VE SİSTEM KONTROL KURALLARI:
- Fener aç/kapat: intent="torch_control", target="on" | "off" | "toggle"
- Ses seviyesi: intent="volume_control", target="<media|ring|notification|alarm>:<up|down|mute|unmute|vibrate|max|set>:<0-100?>"
  Örn: "medya sesini kıs" -> "media:down", "sesi %60 yap" -> "media:set:60", "telefonu sessize al" -> "ring:mute", "titreşime al" -> "ring:vibrate"
- Şarj / pil durumu: intent="battery_status", target=""
- Sistem ayarları aç (Wi-Fi, Bluetooth, Ses, Erişilebilirlik, Bildirim, Pil, Konum, Ekran): intent="open_system_settings", target="wifi"|"bluetooth"|"sound"|"accessibility"|"notifications"|"battery"|"location"|"display"|"settings"
- Ekranı not al ("bu ekranı not al", "şu ekranı kaydet", "bunu not defterine kaydet"): intent="capture_screen_note", target=""
- Haber / Gündem Bülteni ("gündemi aç", "haberleri ver", "sabah brifingi", "günün haberlerini sun"): intent="open_news_broadcast", target=""
- Mevcut haberi detaylandır ("bu haberi detaylandır", "haberi açıkla"): intent="elaborate_news", target=""

EKRAN NOKTASI TIKLAMA KURALI:
- Kullanıcı ekrandaki bir butona/sekmeye tıklamak, basmak veya girmek istiyorsa ve yukarıdaki KAYITLI DOKUNMA ETİKETLERİ listesinde karşılık gelen bir etiket varsa mode="action", intent="tap_coordinate", target="<listeden birebir etiket>" seç!

GÜNCEL ZAMAN: ${nowFormatted}
CİHAZDAKİ UYGULAMALAR: ${appsSummary}
${coordsSection}
KULLANICI BAĞLAMI: ${contextSection}

ÇIKTI MODLARI VE HUD ETİKETLERİ:
- MOD 1 (mode="action"): open_app, open_url, search_youtube, set_reminder, go_home, tap_coordinate, torch_control, volume_control, battery_status, open_system_settings, capture_screen_note, open_news_broadcast veya elaborate_news niyeti varsa mode="action", reply alanına tek satır onay yaz.
- MOD 2 (mode="answer"): Bilgi sorusu, özet, metin üretimi veya sohbetse mode="answer", intent="none" seç.
- mood alanına yanıtın duygusunu (neutral, serious, analytical, energetic, alert) yaz.
- topic_title alanına konuyu özetleyen 2-3 kelimelik kısa büyük harfli HUD başlığı yaz (ör. "STRATEJİ ANALİZİ", "GÜNLÜK PLAN", "SİSTEM DURUMU").`;
}

function buildRecentHistoryBlock(messages: JarvisMessage[]): string {
  if (!messages || messages.length === 0) return '';
  return messages
    .slice(-6)
    .map((msg) => {
      const speaker = msg.isUser ? 'Kullanıcı' : 'JARVIS';
      const actionTag = msg.action
        ? ` [Aksiyon: ${msg.action.intent} -> ${msg.action.target}]`
        : '';
      return `${speaker}: ${msg.text}${actionTag}`;
    })
    .join('\n');
}

function parseStructuredJarvisReply(rawJson: string, rawUserPrompt: string): JarvisAiReply {
  const json = JSON.parse(rawJson) as Record<string, unknown>;
  const mode = String(json.mode ?? 'answer').toLowerCase();
  let reply = String(json.reply ?? '').trim();
  let ttsSummary = String(json.ttsSummary ?? '').trim();
  const intent = normalizeActionIntent(String(json.intent ?? 'none'));
  const rawTarget = json.target;
  const target = rawTarget === null || rawTarget === undefined || rawTarget === 'null'
    ? ''
    : String(rawTarget).trim();

  let minutes =
    typeof json.minutes === 'number' && Number.isFinite(json.minutes) && json.minutes > 0
      ? Math.round(json.minutes)
      : null;
  let seconds =
    typeof json.seconds === 'number' && Number.isFinite(json.seconds) && json.seconds > 0
      ? Math.round(json.seconds)
      : null;

  const rawMessage = json.message;
  const message =
    rawMessage === null || rawMessage === undefined || rawMessage === 'null'
      ? ''
      : String(rawMessage).trim();

  if (intent === 'set_reminder') {
    const parsedDuration = parseReminderDuration(rawUserPrompt);
    if (parsedDuration && parsedDuration.totalMillis > 0) {
      minutes = parsedDuration.minutes;
      seconds = parsedDuration.seconds;
    }
  }

  const action: JarvisAction | null =
    mode === 'action' && intent !== 'none'
      ? {
          intent,
          target,
          minutes,
          seconds,
          message,
        }
      : null;

  if (action && action.intent === 'set_reminder') {
    const label = formatActionDurationLabel(action);
    const reminderText = action.message || action.target;
    if (!reply || (minutes !== 10 && reply.includes('10 dakika'))) {
      reply = reminderText
        ? `${label} sonrası için hatırlatıcı kuruldu: "${reminderText}"`
        : `${label} sonrası için hatırlatıcı kuruldu.`;
      ttsSummary = reply;
    }
  }

  const mood = normalizeJarvisMood(String(json.mood ?? 'neutral'));
  const rawTopic = json.topic_title;
  const topicTitle =
    rawTopic === null || rawTopic === undefined || rawTopic === 'null'
      ? ''
      : String(rawTopic).trim().toLocaleUpperCase('tr-TR');

  const finalReply = reply || (action ? 'Aksiyon hazırlanıyor.' : 'Tamam.');
  return {
    text: finalReply,
    ttsSummary: ttsSummary || finalReply,
    action,
    mood,
    topicTitle,
  };
}

function formatApiError(err: unknown): { status: number; message: string } {
  const msg = err instanceof Error ? err.message : String(err ?? '');
  const lower = msg.toLowerCase();
  if (lower.includes('gemini_api_key')) {
    return { status: 500, message: msg };
  }
  if (lower.includes('403') || lower.includes('permission_denied') || lower.includes('api_key_invalid')) {
    return {
      status: 401,
      message: 'Gemini API anahtarı geçersiz veya yetkisiz. Settings > Secrets panelini kontrol edin.',
    };
  }
  if (lower.includes('429') || lower.includes('resource_exhausted')) {
    return {
      status: 429,
      message: 'API kota sınırına ulaşıldı. Bir süre sonra tekrar deneyin.',
    };
  }
  return {
    status: 500,
    message: msg || 'JARVIS şu anda yanıt üretemedi.',
  };
}

async function startServer() {
  const app = express();
  app.use(express.json({ limit: '10mb' }));

  app.post('/api/jarvis/ask', async (req: Request, res: Response) => {
    try {
      const {
        userPrompt,
        userContext = '',
        recentMessages = [],
        installedApps = [],
        savedCoordinateLabels = [],
        activeProfileLabel = '',
      } = req.body as {
        userPrompt?: string;
        userContext?: string;
        recentMessages?: JarvisMessage[];
        installedApps?: InstalledAppInfo[];
        savedCoordinateLabels?: string[];
        activeProfileLabel?: string;
      };

      if (!userPrompt || !userPrompt.trim()) {
        res.status(400).json({ error: 'Komut metni boş olamaz.' });
        return;
      }

      const ai = getAiClient();
      const systemPrompt = buildSystemPrompt({
        userContext,
        installedApps,
        savedCoordinateLabels,
        activeProfileLabel,
      });
      const historyText = buildRecentHistoryBlock(recentMessages);
      const userContent = historyText
        ? `SON KONUŞMA BAĞLAMI:\n${historyText}\n\nKULLANICI GİRDİSİ:\n${userPrompt.trim()}`
        : `KULLANICI GİRDİSİ:\n${userPrompt.trim()}`;

      const response = await ai.models.generateContent({
        model: MODEL_NAME,
        contents: userContent,
        config: {
          systemInstruction: systemPrompt,
          responseMimeType: 'application/json',
          responseSchema: {
            type: Type.OBJECT,
            properties: {
              mode: {
                type: Type.STRING,
                enum: ['action', 'answer'],
                description: "MOD 1 için 'action', MOD 2 için 'answer'.",
              },
              reply: {
                type: Type.STRING,
                description: "MOD 1'de tek satırlık kısa onay ifadesi; MOD 2'de kısa, net yanıt.",
              },
              ttsSummary: {
                type: Type.STRING,
                description: 'Sesli okuma (TTS) için 1-2 cümlelik çok kısa Türkçe özet.',
              },
              intent: {
                type: Type.STRING,
                enum: [
                  'open_app',
                  'open_url',
                  'search_youtube',
                  'set_reminder',
                  'go_home',
                  'tap_coordinate',
                  'torch_control',
                  'volume_control',
                  'battery_status',
                  'open_system_settings',
                  'capture_screen_note',
                  'open_news_broadcast',
                  'elaborate_news',
                  'none',
                ],
                description: 'Aksiyon niyeti.',
              },
              target: {
                type: Type.STRING,
                description: 'Aksiyon hedefi.',
              },
              minutes: {
                type: Type.INTEGER,
                nullable: true,
                description: 'set_reminder için dakika sayısı.',
              },
              seconds: {
                type: Type.INTEGER,
                nullable: true,
                description: 'set_reminder için saniye sayısı.',
              },
              message: {
                type: Type.STRING,
                description: 'set_reminder için hatırlatma metni.',
              },
              mood: {
                type: Type.STRING,
                enum: ['neutral', 'serious', 'analytical', 'energetic', 'alert'],
                description: 'Yanıtın tonu/duygusu.',
              },
              topic_title: {
                type: Type.STRING,
                description: 'Konuya uygun 2-3 kelimelik kısa büyük harfli HUD başlığı.',
              },
            },
            required: ['mode', 'reply', 'ttsSummary', 'intent', 'target', 'message'],
          },
        },
      });

      const rawJson = (response.text ?? '').trim();
      if (!rawJson) {
        res.status(500).json({ error: 'Model boş yanıt döndürdü.' });
        return;
      }

      const parsed = parseStructuredJarvisReply(rawJson, userPrompt);
      res.json(parsed);
    } catch (err) {
      const formatted = formatApiError(err);
      res.status(formatted.status).json({ error: formatted.message });
    }
  });

  app.post('/api/jarvis/evaluate-whatsapp', async (req: Request, res: Response) => {
    try {
      const { senderName, messageText, userContext = '' } = req.body as {
        senderName?: string;
        messageText?: string;
        userContext?: string;
      };

      if (!senderName?.trim() || !messageText?.trim()) {
        res.status(400).json({ error: 'Gönderen ve mesaj metni gerekli.' });
        return;
      }

      const ai = getAiClient();
      const contextHint =
        userContext.trim() || 'Kısa, net, kibar ve doğal Türkçe yanıt ver.';
      const prompt = `Sen kullanıcının proaktif WhatsApp asistanısın (JARVIS).
Gelen bir WhatsApp mesajını analiz edip önem derecesini puanla ve kullanıcı için hazır cevap taslağı oluştur.

PUANLAMA REHBERİ:
- 1-3: Gündelik selamlaşma, muhabbet, tek yönlü önemsiz bilgi, şaka, teşekkür.
- 4-6: Normal planlama, aciliyeti olmayan soru veya sıradan hatırlatma.
- 7-8: Önemli soru, randevu/toplantı değişikliği, iş/aile ile ilgili net aksiyon gerektiren mesaj.
- 9-10: Acil durum, son dakika iptali, ödeme/finans/onay kodu, kritik iş veya sağlık konusu.

KULLANICI BAĞLAMI: ${contextHint}
GÖNDEREN: ${senderName.trim()}
MESAJ: ${messageText.trim()}`;

      const response = await ai.models.generateContent({
        model: MODEL_NAME,
        contents: prompt,
        config: {
          responseMimeType: 'application/json',
          responseSchema: {
            type: Type.OBJECT,
            properties: {
              importanceScore: {
                type: Type.INTEGER,
                description: '1 ile 10 arasında önem puanı.',
              },
              importanceReason: {
                type: Type.STRING,
                description: 'Tek satırlık kısa Türkçe gerekçe.',
              },
              summary: {
                type: Type.STRING,
                description: 'Çok kısa 1 cümlelik Türkçe özet.',
              },
              draftReply: {
                type: Type.STRING,
                description: 'Doğal, net ve kısa Türkçe WhatsApp cevap taslağı.',
              },
            },
            required: ['importanceScore', 'importanceReason', 'summary', 'draftReply'],
          },
        },
      });

      const raw = (response.text ?? '').trim();
      if (!raw) {
        res.status(500).json({ error: 'Boş değerlendirme yanıtı.' });
        return;
      }
      const json = JSON.parse(raw) as Partial<WhatsAppEvaluation>;
      const score = Math.min(10, Math.max(1, Number(json.importanceScore ?? 1)));
      res.json({
        importanceScore: score,
        importanceReason:
          json.importanceReason?.trim() || 'Mesaj içeriği dikkat gerektiriyor.',
        summary: json.summary?.trim() || messageText.trim().slice(0, 100),
        draftReply: json.draftReply?.trim() || 'Mesajını aldım, birazdan dönüş yapacağım.',
      } satisfies WhatsAppEvaluation);
    } catch (err) {
      const formatted = formatApiError(err);
      res.status(formatted.status).json({ error: formatted.message });
    }
  });

  app.post('/api/jarvis/screen-note', async (req: Request, res: Response) => {
    try {
      const {
        extractedText = '',
        screenshotBase64,
        mimeType = 'image/png',
        sourceAppName = 'Web Çalışma Alanı',
      } = req.body as {
        extractedText?: string;
        screenshotBase64?: string;
        mimeType?: string;
        sourceAppName?: string;
      };

      const cleanedText = extractedText.trim();
      if (!cleanedText && !screenshotBase64) {
        res.status(400).json({ error: 'Not çıkarılacak ekran metni veya görüntüsü gerekli.' });
        return;
      }

      const ai = getAiClient();
      const promptLines = [
        "Kullanıcı mevcut ekranı JARVIS Not Defteri'ne kaydetmek istiyor.",
        `Kaynak Uygulama: ${sourceAppName || 'Bilinmiyor'}`,
        'Görevlerin:',
        '1. Ekrandaki en önemli bilgileri, mesajları, başlıkları, fiyatları, tarihleri veya metni temiz ve okunaklı biçimde çıkar.',
        '2. Kısa ve anlamlı bir Türkçe başlık (title) üret.',
        '3. Eğer ekran görüntüsü verildiyse görseldeki metni de (OCR) oku; gereksiz sistem ikon yazılarını dahil etme.',
      ];
      if (cleanedText) {
        promptLines.push('', 'EKRAN METNİ:', cleanedText.slice(0, 4500));
      }

      const parts: Array<
        | { text: string }
        | { inlineData: { mimeType: string; data: string } }
      > = [];
      if (screenshotBase64) {
        parts.push({
          inlineData: {
            mimeType,
            data: screenshotBase64,
          },
        });
      }
      parts.push({ text: promptLines.join('\n') });

      const response = await ai.models.generateContent({
        model: MODEL_NAME,
        contents: { parts },
        config: {
          responseMimeType: 'application/json',
          responseSchema: {
            type: Type.OBJECT,
            properties: {
              title: {
                type: Type.STRING,
                description: 'Not için 2-6 kelimelik kısa Türkçe başlık.',
              },
              content: {
                type: Type.STRING,
                description: 'Temiz, okunabilir not metni.',
              },
            },
            required: ['title', 'content'],
          },
        },
      });

      const raw = (response.text ?? '').trim();
      if (!raw) {
        res.status(500).json({ error: 'Ekran notu üretilemedi.' });
        return;
      }
      const json = JSON.parse(raw) as Partial<ScreenNoteAiResult>;
      res.json({
        title: json.title?.trim() || `${sourceAppName || 'Ekran'} Notu`,
        content: json.content?.trim() || cleanedText,
      } satisfies ScreenNoteAiResult);
    } catch (err) {
      const formatted = formatApiError(err);
      res.status(formatted.status).json({ error: formatted.message });
    }
  });

  app.post('/api/jarvis/news-broadcast', async (_req: Request, res: Response) => {
    try {
      const { items: rawPool, activeSourcesCount } = await fetchOpenRssNewsPool();
      if (rawPool.length === 0) {
        res.status(500).json({
          error: 'Açık RSS kaynaklarından güncel haber alınamadı. İnternet bağlantısını kontrol edin.',
        });
        return;
      }

      const ai = getAiClient();
      const feedDigest = rawPool
        .map(
          (item) =>
            `ID: ${item.id} | ÖnerilenKategori: ${item.hintCategory} | Kaynak: ${item.sourceName} | Saat: ${item.publishedAt}\nBaşlık: ${item.title}\nÖzet: ${item.description}`
        )
        .join('\n---\n');

      const prompt = `Sen JARVIS Haber Kanalı (JARVIS News Network) baş editörü ve canlı yayın spikerisin.
Aşağıdaki açık RSS haber havuzunu analiz et:
1. Tık tuzağı (clickbait: "Şok!", "İnanamayacaksınız", "Bakan açıkladı o isim" vb.), reklam/sponsorlu içerik, dedikodu ve aynı konuyu tekrarlayan haberleri ELE.
2. En önemli, güvenilir ve bilgilendirici 6 haberi seç. Mümkünse GÜNDEM, TEKNOLOJİ, SPOR ve DÜNYA kategorilerinden dengeli dağıt (görseli olan haberleri önceliklendir).
3. Her seçilen haber için:
   - id: Kaynaktaki birebir ID değeri
   - category: "GÜNDEM", "TEKNOLOJİ", "SPOR" veya "DÜNYA"
   - headline: Tık tuzağından arındırılmış, net, çarpıcı Türkçe haber başlığı (yabancı kaynaklıysa Türkçeye çevir)
   - anchorScript: Spiker tarzında akıcı, sesli okumaya uygun 2-3 cümlelik kısa bülten özeti (Türkçe)
   - elaboratedScript: Kullanıcı "JARVIS, bu haberi detaylandır" dediğinde okunacak 4-5 cümlelik kapsamlı spiker analizi ve bağlam (Türkçe)
4. introScript: Yayına başlarken okunacak 1-2 cümlelik net, profesyonel spiker giriş cümlesi.

HAM RSS HABER HAVUZU:
${feedDigest}`;

      const response = await ai.models.generateContent({
        model: MODEL_NAME,
        contents: prompt,
        config: {
          responseMimeType: 'application/json',
          responseSchema: {
            type: Type.OBJECT,
            properties: {
              introScript: {
                type: Type.STRING,
                description: 'Canlı yayın açılış anonsu.',
              },
              items: {
                type: Type.ARRAY,
                items: {
                  type: Type.OBJECT,
                  properties: {
                    id: { type: Type.STRING },
                    category: {
                      type: Type.STRING,
                      enum: ['GÜNDEM', 'TEKNOLOJİ', 'SPOR', 'DÜNYA'],
                    },
                    headline: { type: Type.STRING },
                    anchorScript: { type: Type.STRING },
                    elaboratedScript: { type: Type.STRING },
                  },
                  required: ['id', 'category', 'headline', 'anchorScript', 'elaboratedScript'],
                },
              },
            },
            required: ['introScript', 'items'],
          },
        },
      });

      const rawJson = (response.text ?? '').trim();
      if (!rawJson) {
        res.status(500).json({ error: 'Haber bülteni oluşturulamadı.' });
        return;
      }

      const parsed = JSON.parse(rawJson) as {
        introScript?: string;
        items?: Array<{
          id?: string;
          category?: string;
          headline?: string;
          anchorScript?: string;
          elaboratedScript?: string;
        }>;
      };

      const rawMap = new Map(rawPool.map((r) => [r.id, r]));
      const validCategories = new Set<NewsCategory>(['GÜNDEM', 'TEKNOLOJİ', 'SPOR', 'DÜNYA']);
      const curated: BroadcastNewsItem[] = [];
      const usedIds = new Set<string>();

      for (const aiItem of parsed.items ?? []) {
        const id = (aiItem.id ?? '').trim();
        const source = rawMap.get(id);
        if (!source || usedIds.has(id)) continue;
        usedIds.add(id);
        const cat = validCategories.has(aiItem.category as NewsCategory)
          ? (aiItem.category as NewsCategory)
          : source.hintCategory;
        curated.push({
          id: source.id,
          category: cat,
          headline: aiItem.headline?.trim() || source.title,
          anchorScript: aiItem.anchorScript?.trim() || source.description,
          elaboratedScript: aiItem.elaboratedScript?.trim() || source.fullContent,
          sourceName: source.sourceName,
          publishedAt: source.publishedAt,
          imageUrl: source.imageUrl,
          articleUrl: source.link,
        });
        if (curated.length >= 6) break;
      }

      // Ensure 6 items if Gemini returned fewer matching IDs
      for (const fallback of rawPool) {
        if (curated.length >= 6) break;
        if (usedIds.has(fallback.id)) continue;
        usedIds.add(fallback.id);
        curated.push({
          id: fallback.id,
          category: fallback.hintCategory,
          headline: fallback.title,
          anchorScript: fallback.description,
          elaboratedScript: fallback.fullContent,
          sourceName: fallback.sourceName,
          publishedAt: fallback.publishedAt,
          imageUrl: fallback.imageUrl,
          articleUrl: fallback.link,
        });
      }

      res.json({
        introScript:
          parsed.introScript?.trim() ||
          'JARVIS Haber Ağı canlı yayında. Günün öne çıkan altı başlığını aktarıyorum.',
        items: curated,
        fetchedAtMillis: Date.now(),
        feedSourcesCount: activeSourcesCount,
      } satisfies NewsBroadcastResponse);
    } catch (err) {
      const formatted = formatApiError(err);
      res.status(formatted.status).json({ error: formatted.message });
    }
  });

  app.post('/api/jarvis/news-elaborate', async (req: Request, res: Response) => {
    try {
      const { headline, anchorScript, elaboratedScript, sourceName, question = '' } = req.body as {
        headline?: string;
        anchorScript?: string;
        elaboratedScript?: string;
        sourceName?: string;
        question?: string;
      };

      if (!headline?.trim()) {
        res.status(400).json({ error: 'Detaylandırılacak haber başlığı eksik.' });
        return;
      }

      const ai = getAiClient();
      const prompt = `Sen JARVIS Haber Kanalı spikerisin. Kullanıcı yayındaki şu haber hakkında daha fazla detay istedi.
Doğal, akıcı, sesli okumaya uygun 4-6 cümlelik derinleştirilmiş Türkçe spiker özeti ver (uydurma bilgi ekleme, eldeki veriyi net ve anlaşılır biçimde genişlet).

KAYNAK: ${sourceName || 'Haber Kaynağı'}
BAŞLIK: ${headline.trim()}
KISA ÖZET: ${anchorScript || ''}
EK BİLGİ: ${elaboratedScript || ''}
KULLANICI TALEBİ: ${question.trim() || 'Bu haberi detaylandır.'}`;

      const response = await ai.models.generateContent({
        model: MODEL_NAME,
        contents: prompt,
      });

      const detail = (response.text ?? '').trim() || elaboratedScript || anchorScript || headline;
      res.json({ detail });
    } catch (err) {
      const formatted = formatApiError(err);
      res.status(formatted.status).json({ error: formatted.message });
    }
  });

  if (process.env.NODE_ENV === 'production') {
    const distDir = path.join(__dirname, 'dist');
    app.use(express.static(distDir));
    app.get('/{*splat}', (_req: Request, res: Response) => {
      res.sendFile(path.join(distDir, 'index.html'));
    });
  } else {
    const { createServer: createViteServer } = await import('vite');
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  }

  app.listen(3000, '0.0.0.0', () => {
    console.log('JARVIS server running on http://0.0.0.0:3000');
  });
}

startServer().catch((err) => {
  console.error('Failed to start JARVIS server:', err);
  process.exit(1);
});
