import type { ActionIntent, JarvisMood, JarvisVisualState } from '../types/jarvis';

export function resolveHudHeader(params: {
  isListening: boolean;
  isProcessing: boolean;
  hasError: boolean;
  hasProactiveAlert: boolean;
  latestActionIntent?: ActionIntent | null;
  aiTopicTitle: string;
}): string {
  const {
    isListening,
    isProcessing,
    hasError,
    hasProactiveAlert,
    latestActionIntent,
    aiTopicTitle,
  } = params;

  if (hasError) return 'SİSTEM UYARISI // KRİTİK';
  if (isListening) return 'SES KANALI // DİNLENİYOR';
  if (isProcessing) return 'NÖRAL ÇEKİRDEK // ANALİZ';
  if (hasProactiveAlert) return 'İLETİŞİM PROTOKOLÜ // TASLAK';
  if (latestActionIntent === 'open_news_broadcast') return 'HABER KANALI // CANLI BRİFİNG';
  if (latestActionIntent === 'elaborate_news') return 'DERİN ANALİZ // BÜLTEN';
  if (latestActionIntent === 'capture_screen_note') return 'GÖRSEL TARAMA // ARŞİV KAYDI';
  if (latestActionIntent === 'tap_coordinate') return 'HEDEF KİLİTLEME // OTOMASYON';
  if (latestActionIntent === 'set_reminder') return 'ZAMANLAYICI // HATIRLATMA';
  if (
    latestActionIntent === 'battery_status' ||
    latestActionIntent === 'torch_control' ||
    latestActionIntent === 'volume_control' ||
    latestActionIntent === 'open_system_settings'
  ) {
    return 'SİSTEM DURUMU // ANALİZ';
  }
  if (aiTopicTitle.trim()) return aiTopicTitle.trim().toLocaleUpperCase('tr-TR');
  if (
    latestActionIntent === 'open_app' ||
    latestActionIntent === 'open_url' ||
    latestActionIntent === 'search_youtube' ||
    latestActionIntent === 'go_home'
  ) {
    return 'KOMUT İCRASI // YÖNLENDİRME';
  }
  return 'ÇEKİRDEK HAZIR // BEKLEMEDE';
}

export function moodForAction(intent: ActionIntent, fallbackMood: JarvisMood): JarvisMood {
  switch (intent) {
    case 'capture_screen_note':
    case 'battery_status':
    case 'elaborate_news':
      return 'analytical';
    case 'tap_coordinate':
    case 'torch_control':
    case 'search_youtube':
    case 'open_news_broadcast':
      return 'energetic';
    case 'set_reminder':
    case 'open_system_settings':
    case 'volume_control':
      return 'serious';
    default:
      return fallbackMood;
  }
}

export function resolveMoodAccentColor(visualState: JarvisVisualState, mood: JarvisMood): string {
  if (visualState === 'ERROR' || mood === 'alert') return '#FB7185';
  if (visualState === 'LISTENING') return '#34D399';
  switch (mood) {
    case 'neutral':
      return visualState === 'IDLE' ? '#60A5FA' : '#38BDF8';
    case 'serious':
      return '#FBBF24';
    case 'analytical':
      return '#A78BFA';
    case 'energetic':
      return '#22D3EE';
  }
}
