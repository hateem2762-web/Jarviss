import type { JarvisAction } from '../types/jarvis';

function normalize(text: string): string {
  return text
    .toLowerCase()
    .replace(/ç/g, 'c')
    .replace(/ğ/g, 'g')
    .replace(/ı/g, 'i')
    .replace(/İ/g, 'i')
    .replace(/ö/g, 'o')
    .replace(/ş/g, 's')
    .replace(/ü/g, 'u')
    .replace(/-/g, ' ')
    .replace(/[^\p{L}\p{N}\s]/gu, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

const TORCH_ON = new Set(['fener', 'feneri ac', 'fener ac', 'flasi ac', 'flas ac', 'isigi ac']);
const TORCH_OFF = new Set([
  'feneri kapat',
  'fener kapat',
  'feneri kapa',
  'flasi kapat',
  'flas kapat',
  'isigi kapat',
]);

export function matchLocalQuickCommand(rawPrompt: string): JarvisAction | null {
  const normRaw = normalize(rawPrompt);
  if (!normRaw) return null;
  const norm = normRaw.replace(/^jarvis\s+/, '').trim();
  if (!norm) return null;

  // 0. News Broadcast & Elaboration
  const isNewsBroadcastCommand =
    norm.includes('gundemi ac') ||
    norm.includes('gundem ac') ||
    norm.includes('haberleri ver') ||
    norm.includes('haberleri ac') ||
    norm.includes('sabah brifingi') ||
    norm.includes('sabah brifingini') ||
    norm.includes('haber bulteni') ||
    norm.includes('gundem bulteni') ||
    norm.includes('haber kanali') ||
    norm.includes('gunun haberleri') ||
    norm === 'gundem' ||
    norm === 'haberler';
  if (isNewsBroadcastCommand) {
    return {
      intent: 'open_news_broadcast',
      target: '',
      minutes: null,
      seconds: null,
      message: '',
    };
  }

  const isElaborateNewsCommand =
    norm.includes('bu haberi detaylandir') ||
    norm.includes('haberi detaylandir') ||
    norm.includes('haberin detayi') ||
    norm.includes('haberi acikla') ||
    norm === 'detaylandir';
  if (isElaborateNewsCommand) {
    return {
      intent: 'elaborate_news',
      target: '',
      minutes: null,
      seconds: null,
      message: '',
    };
  }

  // 1. Screen note
  const isScreenNoteCommand =
    (norm.includes('ekran') &&
      (norm.includes('not al') || norm.includes('not et') || norm.includes('kaydet'))) ||
    norm.includes('not defterine kaydet') ||
    norm.includes('not defterine ekle') ||
    norm === 'not al';
  if (isScreenNoteCommand) {
    return {
      intent: 'capture_screen_note',
      target: '',
      minutes: null,
      seconds: null,
      message: '',
    };
  }

  // 2. Flashlight
  if (TORCH_ON.has(norm)) {
    return {
      intent: 'torch_control',
      target: 'on',
      minutes: null,
      seconds: null,
      message: '',
    };
  }
  if (TORCH_OFF.has(norm)) {
    return {
      intent: 'torch_control',
      target: 'off',
      minutes: null,
      seconds: null,
      message: '',
    };
  }

  // 3. Battery status
  if (
    norm.includes('sarjim kac') ||
    norm.includes('sarj yuzde') ||
    norm.includes('pil yuzde') ||
    norm.includes('pil durumu') ||
    norm.includes('sarj durumu') ||
    norm.includes('batarya durumu') ||
    norm.includes('sarj oluyor mu')
  ) {
    return {
      intent: 'battery_status',
      target: '',
      minutes: null,
      seconds: null,
      message: '',
    };
  }

  // 4. Direct System Settings shortcuts
  if (norm.includes('ayar')) {
    if (norm.includes('wifi') || norm.includes('wi fi') || norm.includes('kablosuz')) {
      return { intent: 'open_system_settings', target: 'wifi', minutes: null, seconds: null, message: '' };
    }
    if (norm.includes('bluetooth')) {
      return { intent: 'open_system_settings', target: 'bluetooth', minutes: null, seconds: null, message: '' };
    }
    if (norm.includes('ses ayar')) {
      return { intent: 'open_system_settings', target: 'sound', minutes: null, seconds: null, message: '' };
    }
    if (norm.includes('erisilebilirlik')) {
      return { intent: 'open_system_settings', target: 'accessibility', minutes: null, seconds: null, message: '' };
    }
  }

  return null;
}
