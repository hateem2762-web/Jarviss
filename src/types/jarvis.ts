export type JarvisDestination =
  | 'ASSISTANT'
  | 'NEWS'
  | 'NOTES'
  | 'COORDINATES'
  | 'REMINDERS'
  | 'SETTINGS';

export type ActionIntent =
  | 'open_app'
  | 'open_url'
  | 'search_youtube'
  | 'set_reminder'
  | 'go_home'
  | 'tap_coordinate'
  | 'torch_control'
  | 'volume_control'
  | 'battery_status'
  | 'open_system_settings'
  | 'capture_screen_note'
  | 'open_news_broadcast'
  | 'elaborate_news'
  | 'none';

export const ACTION_INTENT_LABELS: Record<ActionIntent, string> = {
  open_app: 'Uygulama Aç',
  open_url: 'Bağlantı Aç',
  search_youtube: 'YouTube Ara',
  set_reminder: 'Hatırlatıcı Kur',
  go_home: 'Ana Ekrana Dön',
  tap_coordinate: 'Koordinata Dokun',
  torch_control: 'Fener Kontrolü',
  volume_control: 'Ses Kontrolü',
  battery_status: 'Pil Durumu',
  open_system_settings: 'Sistem Ayarı',
  capture_screen_note: 'Ekranı Not Al',
  open_news_broadcast: 'Gündem Bülteni',
  elaborate_news: 'Haberi Detaylandır',
  none: 'Yanıt',
};

export function normalizeActionIntent(value?: string | null): ActionIntent {
  const normalized = (value ?? '').trim().toLowerCase();
  if (normalized in ACTION_INTENT_LABELS) {
    return normalized as ActionIntent;
  }
  return 'none';
}

export type ActionExecutionStatus = 'PENDING' | 'EXECUTED' | 'FAILED';

export interface JarvisAction {
  intent: ActionIntent;
  target: string;
  minutes: number | null;
  seconds: number | null;
  message: string;
}

export function getTotalDelayMillis(action: JarvisAction): number {
  const mins = Math.max(0, action.minutes ?? 0);
  const secs = Math.max(0, action.seconds ?? 0);
  if (mins === 0 && secs === 0) {
    return 10 * 60_000;
  }
  return mins * 60_000 + secs * 1_000;
}

export function formatActionDurationLabel(action: JarvisAction): string {
  const mins = Math.max(0, action.minutes ?? 0);
  const secs = Math.max(0, action.seconds ?? 0);
  if (mins >= 60 && mins % 60 === 0 && secs === 0) return `${mins / 60} sa`;
  if (mins >= 60 && secs === 0) return `${Math.floor(mins / 60)} sa ${mins % 60} dk`;
  if (mins > 0 && secs > 0) return `${mins} dk ${secs} sn`;
  if (mins > 0) return `${mins} dk`;
  if (secs > 0) return `${secs} sn`;
  return '10 dk';
}

export function toPrettyActionJson(action: JarvisAction): string {
  return JSON.stringify(
    {
      type: 'action',
      intent: action.intent,
      target: action.target,
      minutes: action.minutes,
      seconds: action.seconds,
      message: action.message,
    },
    null,
    2
  );
}

export interface JarvisMessage {
  id: number;
  isUser: boolean;
  text: string;
  action?: JarvisAction | null;
  actionStatus: ActionExecutionStatus;
  actionStatusDetail: string;
  actionUrl?: string;
  timestampMillis: number;
}

export interface InstalledAppInfo {
  label: string;
  packageName: string;
  webUrl?: string;
}

export interface ReminderItem {
  id: number;
  message: string;
  triggerAtMillis: number;
  durationLabel: string;
  isCompleted: boolean;
  createdAtMillis: number;
}

export interface TapCoordinateItem {
  id: number;
  profilePackageName: string;
  profileName: string;
  label: string;
  xPercent: number;
  yPercent: number;
  xPx: number;
  yPx: number;
  createdAtMillis: number;
}

export interface ProactiveAlertItem {
  id: number;
  sourcePackage: string;
  senderName: string;
  originalMessage: string;
  summary: string;
  importanceScore: number;
  importanceReason: string;
  draftReply: string;
  messageTimestampMillis: number;
  createdAtMillis: number;
  isDismissed: boolean;
}

export interface NoteItem {
  id: number;
  title: string;
  content: string;
  sourcePackage: string;
  sourceAppName: string;
  screenshotDataUrl?: string;
  createdAtMillis: number;
  updatedAtMillis: number;
}

export type JarvisMood = 'neutral' | 'serious' | 'analytical' | 'energetic' | 'alert';

export function normalizeJarvisMood(value?: string | null): JarvisMood {
  const normalized = (value ?? '').trim().toLowerCase();
  if (
    normalized === 'neutral' ||
    normalized === 'serious' ||
    normalized === 'analytical' ||
    normalized === 'energetic' ||
    normalized === 'alert'
  ) {
    return normalized;
  }
  return 'neutral';
}

export type JarvisVisualState = 'IDLE' | 'LISTENING' | 'THINKING' | 'SPEAKING' | 'ERROR';

export interface JarvisPreferences {
  userContext: string;
  darkTheme: boolean;
  autoExecuteActions: boolean;
  speakReplies: boolean;
  persistentTriggerEnabled: boolean;
  floatingBubbleEnabled: boolean;
  wakeWordEnabled: boolean;
  proactiveAlertsEnabled: boolean;
  importanceThreshold: number;
  newsAirGestureEnabled: boolean;
  manualProfilePackageName: string;
  manualProfileName: string;
}

export interface VoiceInputState {
  isListening: boolean;
  partialText: string;
  rmsLevel: number;
  errorMessage: string | null;
}

export interface JarvisAiReply {
  text: string;
  ttsSummary: string;
  action: JarvisAction | null;
  mood: JarvisMood;
  topicTitle: string;
}

export interface WhatsAppEvaluation {
  importanceScore: number;
  importanceReason: string;
  summary: string;
  draftReply: string;
}

export interface ScreenNoteAiResult {
  title: string;
  content: string;
}

export type NewsCategory = 'GÜNDEM' | 'TEKNOLOJİ' | 'SPOR' | 'DÜNYA';

export interface RawRssNewsItem {
  id: string;
  title: string;
  description: string;
  fullContent: string;
  sourceName: string;
  publishedAt: string;
  link: string;
  imageUrl: string | null;
  hintCategory: NewsCategory;
}

export interface BroadcastNewsItem {
  id: string;
  category: NewsCategory;
  headline: string;
  anchorScript: string;
  elaboratedScript: string;
  sourceName: string;
  publishedAt: string;
  imageUrl: string | null;
  articleUrl: string;
}

export interface NewsBroadcastResponse {
  introScript: string;
  items: BroadcastNewsItem[];
  fetchedAtMillis: number;
  feedSourcesCount: number;
}

export const GENERAL_PROFILE_PACKAGE = 'manual.general';
export const GENERAL_PROFILE_NAME = 'Genel Profil';
