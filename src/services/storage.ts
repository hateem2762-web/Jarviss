import {
  GENERAL_PROFILE_NAME,
  GENERAL_PROFILE_PACKAGE,
  type InstalledAppInfo,
  type JarvisMessage,
  type JarvisPreferences,
  type NoteItem,
  type ProactiveAlertItem,
  type ReminderItem,
  type TapCoordinateItem,
} from '../types/jarvis';

const KEYS = {
  MESSAGES: 'jarvis_messages_v1',
  REMINDERS: 'jarvis_reminders_v1',
  COORDINATES: 'jarvis_coordinates_v1',
  PROACTIVE_ALERTS: 'jarvis_proactive_alerts_v1',
  NOTES: 'jarvis_notes_v1',
  PREFERENCES: 'jarvis_preferences_v1',
};

export const DEFAULT_PREFERENCES: JarvisPreferences = {
  userContext: '',
  darkTheme: true,
  autoExecuteActions: true,
  speakReplies: false,
  persistentTriggerEnabled: false,
  floatingBubbleEnabled: false,
  wakeWordEnabled: false,
  proactiveAlertsEnabled: true,
  importanceThreshold: 7,
  newsAirGestureEnabled: false,
  manualProfilePackageName: GENERAL_PROFILE_PACKAGE,
  manualProfileName: GENERAL_PROFILE_NAME,
};

export const DEFAULT_INSTALLED_APPS: InstalledAppInfo[] = [
  { label: 'YouTube', packageName: 'com.google.android.youtube', webUrl: 'https://www.youtube.com' },
  { label: 'Spotify', packageName: 'com.spotify.music', webUrl: 'https://open.spotify.com' },
  { label: 'WhatsApp', packageName: 'com.whatsapp', webUrl: 'https://web.whatsapp.com' },
  { label: 'Gmail', packageName: 'com.google.android.gm', webUrl: 'https://mail.google.com' },
  { label: 'Chrome', packageName: 'com.android.chrome', webUrl: 'https://www.google.com' },
  { label: 'Google Haritalar', packageName: 'com.google.android.apps.maps', webUrl: 'https://maps.google.com' },
  { label: 'Takvim', packageName: 'com.google.android.calendar', webUrl: 'https://calendar.google.com' },
  { label: 'Notlar / Keep', packageName: 'com.google.android.keep', webUrl: 'https://keep.google.com' },
  { label: 'Saat', packageName: 'com.google.android.deskclock' },
  { label: 'Hesap Makinesi', packageName: 'com.google.android.calculator' },
  { label: 'Ayarlar', packageName: 'com.android.settings' },
  { label: 'Dosyalar', packageName: 'com.google.android.documentsui' },
  { label: 'Fotoğraflar', packageName: 'com.google.android.apps.photos', webUrl: 'https://photos.google.com' },
  { label: 'Kamera', packageName: 'com.android.camera' },
];

function safeParse<T>(raw: string | null, fallback: T): T {
  if (!raw) return fallback;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return fallback;
  }
}

export function loadMessages(): JarvisMessage[] {
  return safeParse<JarvisMessage[]>(localStorage.getItem(KEYS.MESSAGES), []);
}

export function saveMessages(messages: JarvisMessage[]): void {
  localStorage.setItem(KEYS.MESSAGES, JSON.stringify(messages));
}

export function loadReminders(): ReminderItem[] {
  return safeParse<ReminderItem[]>(localStorage.getItem(KEYS.REMINDERS), []);
}

export function saveReminders(reminders: ReminderItem[]): void {
  localStorage.setItem(KEYS.REMINDERS, JSON.stringify(reminders));
}

export function loadCoordinates(): TapCoordinateItem[] {
  return safeParse<TapCoordinateItem[]>(localStorage.getItem(KEYS.COORDINATES), []);
}

export function saveCoordinates(coordinates: TapCoordinateItem[]): void {
  localStorage.setItem(KEYS.COORDINATES, JSON.stringify(coordinates));
}

export function loadProactiveAlerts(): ProactiveAlertItem[] {
  return safeParse<ProactiveAlertItem[]>(localStorage.getItem(KEYS.PROACTIVE_ALERTS), []);
}

export function saveProactiveAlerts(alerts: ProactiveAlertItem[]): void {
  localStorage.setItem(KEYS.PROACTIVE_ALERTS, JSON.stringify(alerts));
}

export function loadNotes(): NoteItem[] {
  return safeParse<NoteItem[]>(localStorage.getItem(KEYS.NOTES), []);
}

export function saveNotes(notes: NoteItem[]): void {
  localStorage.setItem(KEYS.NOTES, JSON.stringify(notes));
}

export function loadPreferences(): JarvisPreferences {
  const loaded = safeParse<Partial<JarvisPreferences>>(
    localStorage.getItem(KEYS.PREFERENCES),
    {}
  );
  return {
    ...DEFAULT_PREFERENCES,
    ...loaded,
    importanceThreshold: Math.min(
      10,
      Math.max(1, loaded.importanceThreshold ?? DEFAULT_PREFERENCES.importanceThreshold)
    ),
  };
}

export function savePreferences(prefs: JarvisPreferences): void {
  localStorage.setItem(KEYS.PREFERENCES, JSON.stringify(prefs));
}
