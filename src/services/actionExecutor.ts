import {
  formatActionDurationLabel,
  getTotalDelayMillis,
  type ActionExecutionStatus,
  type InstalledAppInfo,
  type JarvisAction,
  type JarvisDestination,
  type NoteItem,
  type ReminderItem,
  type TapCoordinateItem,
} from '../types/jarvis';
import {
  findCoordinateByModelLabel,
  normalizeTurkishCoordinateText,
} from '../utils/coordinateMatcher';
import { generateNoteFromScreenApi } from './jarvisApi';

export interface ActionExecutionResult {
  status: ActionExecutionStatus;
  detail: string;
  actionUrl?: string;
}

export interface WebActionRuntimeCallbacks {
  installedApps: InstalledAppInfo[];
  getCoordinatesForActiveOrFallback: (activePackageName?: string | null) => TapCoordinateItem[];
  onNavigate: (destination: JarvisDestination) => void;
  onAddReminder: (reminder: ReminderItem) => void;
  onAddNote: (note: Omit<NoteItem, 'id' | 'createdAtMillis' | 'updatedAtMillis'>) => void;
  onTapVisualFeedback: (coord: TapCoordinateItem) => void;
  onTorchChange: (enabled: boolean) => void;
  getTorchState: () => boolean;
  onVolumeChange: (nextVolume: number, muted: boolean) => void;
  getVolumeState: () => { level: number; muted: boolean };
  getActiveScreenText: () => { text: string; sourceAppName: string };
  onElaborateActiveNews: () => Promise<{ found: boolean; detail: string }>;
}

const APP_ALIASES: Record<string, string[]> = {
  ayarlar: ['settings'],
  kamera: ['camera'],
  tarayici: ['chrome', 'browser'],
  chrome: ['browser'],
  saat: ['clock', 'alarm'],
  takvim: ['calendar'],
  'hesap makinesi': ['calculator'],
  harita: ['maps'],
  haritalar: ['maps'],
  muzik: ['music', 'spotify', 'yt music'],
  notlar: ['notes', 'keep'],
  posta: ['gmail', 'mail'],
  galeri: ['photos', 'gallery'],
  fotograflar: ['photos'],
  dosyalar: ['files'],
};

function expandAliases(normalizedQuery: string): string[] {
  const cleaned = normalizedQuery
    .replace('uygulamasini', '')
    .replace('uygulamasi', '')
    .replace('ac', '')
    .trim();
  const base = cleaned || normalizedQuery;
  const aliases = [normalizedQuery, base];
  for (const [key, vals] of Object.entries(APP_ALIASES)) {
    if (base.includes(key) || normalizedQuery.includes(key)) {
      aliases.push(...vals);
    }
  }
  return Array.from(new Set(aliases)).filter(Boolean);
}

function findBestAppMatch(query: string, apps: InstalledAppInfo[]): InstalledAppInfo | null {
  const normalizedQuery = normalizeTurkishCoordinateText(query);
  const candidates = expandAliases(normalizedQuery);
  let bestApp: InstalledAppInfo | null = null;
  let bestScore = 0;

  for (const app of apps) {
    const normLabel = normalizeTurkishCoordinateText(app.label);
    const normPkg = normalizeTurkishCoordinateText(app.packageName);
    const labelWords = normLabel.split(' ').filter(Boolean);

    for (const candidate of candidates) {
      let score = 0;
      if (normLabel === candidate) score = 100;
      else if (normLabel.startsWith(candidate)) score = 90;
      else if (labelWords.some((w) => w === candidate)) score = 85;
      else if (normLabel.includes(candidate)) score = 78;
      else if (candidate.includes(normLabel) && normLabel.length >= 2) score = 68;
      else if (normPkg.includes(candidate) && candidate.length >= 3) score = 55;

      if (score > bestScore) {
        bestScore = score;
        bestApp = app;
      }
    }
  }

  return bestScore >= 55 ? bestApp : null;
}

export function launchSafeExternalLink(url: string): void {
  const a = document.createElement('a');
  a.href = url;
  a.target = '_blank';
  a.rel = 'noopener noreferrer';
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
}

export async function executeJarvisAction(
  action: JarvisAction,
  activeProfilePackage: string | null,
  callbacks: WebActionRuntimeCallbacks
): Promise<ActionExecutionResult> {
  switch (action.intent) {
    case 'open_app': {
      const query = action.target.trim();
      if (!query) {
        return { status: 'FAILED', detail: 'Açılacak uygulama adı belirtilmedi.' };
      }
      const matched = findBestAppMatch(query, callbacks.installedApps);
      if (!matched) {
        return { status: 'FAILED', detail: `Cihazda "${query}" uygulaması bulunamadı.` };
      }
      if (matched.packageName === 'com.android.settings') {
        callbacks.onNavigate('SETTINGS');
        return { status: 'EXECUTED', detail: `${matched.label} açıldı.` };
      }
      if (matched.packageName === 'com.google.android.keep') {
        callbacks.onNavigate('NOTES');
        return { status: 'EXECUTED', detail: `${matched.label} (Not Defteri) açıldı.` };
      }
      if (matched.packageName === 'com.google.android.deskclock') {
        callbacks.onNavigate('REMINDERS');
        return { status: 'EXECUTED', detail: `${matched.label} (Alarmlar) açıldı.` };
      }
      if (matched.webUrl) {
        launchSafeExternalLink(matched.webUrl);
        return {
          status: 'EXECUTED',
          detail: `${matched.label} açılıyor.`,
          actionUrl: matched.webUrl,
        };
      }
      return {
        status: 'EXECUTED',
        detail: `${matched.label} web ortamında simüle edildi (yerel APK özelliği).`,
      };
    }

    case 'open_url': {
      const trimmed = action.target.trim();
      if (!trimmed) {
        return { status: 'FAILED', detail: 'Açılacak bağlantı belirtilmedi.' };
      }
      const normalizedUrl =
        trimmed.startsWith('http://') || trimmed.startsWith('https://')
          ? trimmed
          : `https://${trimmed}`;
      launchSafeExternalLink(normalizedUrl);
      return {
        status: 'EXECUTED',
        detail: `${normalizedUrl} açılıyor.`,
        actionUrl: normalizedUrl,
      };
    }

    case 'search_youtube': {
      const cleaned = action.target.trim();
      if (!cleaned) {
        return { status: 'FAILED', detail: 'YouTube arama sorgusu boş.' };
      }
      const youtubeUrl = `https://www.youtube.com/results?search_query=${encodeURIComponent(cleaned)}`;
      launchSafeExternalLink(youtubeUrl);
      return {
        status: 'EXECUTED',
        detail: `YouTube'da "${cleaned}" aranıyor.`,
        actionUrl: youtubeUrl,
      };
    }

    case 'set_reminder': {
      const reminderText = action.message.trim() || action.target.trim() || 'Hatırlatıcı';
      const delayMillis = getTotalDelayMillis(action);
      const durationLabel = formatActionDurationLabel(action);
      const now = Date.now();
      const triggerAt = now + delayMillis;
      callbacks.onAddReminder({
        id: now + Math.floor(Math.random() * 1000),
        message: reminderText,
        triggerAtMillis: triggerAt,
        durationLabel,
        isCompleted: false,
        createdAtMillis: now,
      });
      return {
        status: 'EXECUTED',
        detail: `${durationLabel} sonra için kuruldu: "${reminderText}"`,
      };
    }

    case 'go_home': {
      callbacks.onNavigate('ASSISTANT');
      return { status: 'EXECUTED', detail: 'Ana ekrana dönüldü.' };
    }

    case 'tap_coordinate': {
      const candidates = callbacks.getCoordinatesForActiveOrFallback(activeProfilePackage);
      const matched = findCoordinateByModelLabel(action.target, candidates);
      if (!matched) {
        return {
          status: 'FAILED',
          detail: `"${action.target}" için kayıtlı ekran koordinatı bulunamadı.`,
        };
      }
      callbacks.onTapVisualFeedback(matched);
      const xPct = Math.round(matched.xPercent * 100);
      const yPct = Math.round(matched.yPercent * 100);
      return {
        status: 'EXECUTED',
        detail: `"${matched.label}" (${matched.profileName}, %${xPct}, %${yPct}) noktasına dokunuldu.`,
      };
    }

    case 'torch_control': {
      const mode = action.target.trim().toLowerCase();
      const next = mode === 'on' ? true : mode === 'off' ? false : !callbacks.getTorchState();
      callbacks.onTorchChange(next);
      return {
        status: 'EXECUTED',
        detail: next ? 'Fener açıldı (HUD ekran ışığı).' : 'Fener kapatıldı.',
      };
    }

    case 'volume_control': {
      const parts = action.target.split(':');
      const op = (parts[1] ?? 'up').toLowerCase();
      const arg = parts[2] ? Number.parseInt(parts[2], 10) : NaN;
      const cur = callbacks.getVolumeState();
      let nextLevel = cur.level;
      let nextMuted = cur.muted;

      if (op === 'mute' || op === 'vibrate') {
        nextMuted = true;
      } else if (op === 'unmute') {
        nextMuted = false;
      } else if (op === 'max') {
        nextLevel = 100;
        nextMuted = false;
      } else if (op === 'up') {
        nextLevel = Math.min(100, cur.level + 15);
        nextMuted = false;
      } else if (op === 'down') {
        nextLevel = Math.max(0, cur.level - 15);
      } else if (op === 'set' && Number.isFinite(arg)) {
        nextLevel = Math.min(100, Math.max(0, arg));
        nextMuted = nextLevel === 0;
      }
      callbacks.onVolumeChange(nextLevel, nextMuted);
      return {
        status: 'EXECUTED',
        detail: nextMuted
          ? 'Ses kapatıldı (Sessiz mod).'
          : `Ses seviyesi %${nextLevel} olarak ayarlandı.`,
      };
    }

    case 'battery_status': {
      try {
        const nav = navigator as Navigator & {
          getBattery?: () => Promise<{ level: number; charging: boolean }>;
        };
        if (typeof nav.getBattery === 'function') {
          const battery = await nav.getBattery();
          const pct = Math.round(battery.level * 100);
          const chargingNote = battery.charging ? 'Şarj oluyor' : 'Pil kullanımında';
          return {
            status: 'EXECUTED',
            detail: `Pil seviyesi %${pct} (${chargingNote}).`,
          };
        }
      } catch {
        // Fallback below
      }
      return {
        status: 'EXECUTED',
        detail: 'Tarayıcı ortamında pil sensörü kısıtlı; güç durumu normal.',
      };
    }

    case 'open_system_settings': {
      callbacks.onNavigate('SETTINGS');
      const target = action.target.trim() || 'settings';
      return {
        status: 'EXECUTED',
        detail: `Ayarlar ekranı açıldı (${target}).`,
      };
    }

    case 'capture_screen_note': {
      const screenData = callbacks.getActiveScreenText();
      if (!screenData.text.trim()) {
        return {
          status: 'FAILED',
          detail: 'Ekrandan okunabilir içerik bulunamadı.',
        };
      }
      try {
        const aiNote = await generateNoteFromScreenApi({
          extractedText: screenData.text,
          sourceAppName: screenData.sourceAppName,
        });
        callbacks.onAddNote({
          title: aiNote.title,
          content: aiNote.content,
          sourcePackage: 'web.workspace',
          sourceAppName: screenData.sourceAppName,
        });
        return {
          status: 'EXECUTED',
          detail: `Not Defteri'ne kaydedildi: "${aiNote.title}" (${screenData.sourceAppName})`,
        };
      } catch (err) {
        const fallbackTitle = `${screenData.sourceAppName} Ekran Notu`;
        callbacks.onAddNote({
          title: fallbackTitle,
          content: screenData.text,
          sourcePackage: 'web.workspace',
          sourceAppName: screenData.sourceAppName,
        });
        return {
          status: 'EXECUTED',
          detail: `Not Defteri'ne ham metin kaydedildi (${err instanceof Error ? err.message : 'yerel kayıt'}).`,
        };
      }
    }

    case 'open_news_broadcast': {
      callbacks.onNavigate('NEWS');
      return {
        status: 'EXECUTED',
        detail: 'JARVIS Haber Kanalı açılıyor.',
      };
    }

    case 'elaborate_news': {
      const res = await callbacks.onElaborateActiveNews();
      if (!res.found) {
        return {
          status: 'FAILED',
          detail: res.detail || 'Şu anda aktif bir haber bülteni açık değil.',
        };
      }
      return {
        status: 'EXECUTED',
        detail: res.detail,
      };
    }

    case 'none':
    default:
      return { status: 'EXECUTED', detail: '' };
  }
}
