import React, { useEffect, useState } from 'react';
import {
  AlarmClock,
  BatteryCharging,
  Flashlight,
  Newspaper,
  NotebookPen,
  PictureInPicture2,
  Power,
  Radio,
  Send,
  X,
} from 'lucide-react';
import {
  type JarvisAction,
  type JarvisMessage,
  type JarvisMood,
  type JarvisPreferences,
  type JarvisVisualState,
  type ProactiveAlertItem,
  type VoiceInputState,
} from '../types/jarvis';
import { resolveHudHeader, resolveMoodAccentColor } from '../utils/hudTopicResolver';
import {
  JarvisHudHeader,
  JarvisTopHudBar,
  LiveSubtitleDisplay,
  MinimalistChatStreamView,
  VoiceOrbButton,
} from '../components/JarvisHudComponents';
import { ParticleNebulaCanvas } from '../components/ParticleNebulaCanvas';
import { ProactiveAlertCard } from '../components/ProactiveAlertCard';

const STARTER_COMMANDS = [
  { label: 'Gündemi aç', command: 'Jarvis, gündemi aç', Icon: Newspaper },
  { label: 'Ekranı not al', command: 'Bu ekranı not al', Icon: NotebookPen },
  { label: 'Feneri aç', command: 'Feneri aç', Icon: Flashlight },
  {
    label: '3 dk hatırlatıcı',
    command: '3 dakika sonra kontrol etmeyi hatırlat',
    Icon: AlarmClock,
  },
  { label: 'Pil durumu', command: 'Şarjım kaç?', Icon: BatteryCharging },
];

interface AssistantScreenProps {
  messages: JarvisMessage[];
  proactiveAlerts: ProactiveAlertItem[];
  preferences: JarvisPreferences;
  voiceState: VoiceInputState;
  isProcessing: boolean;
  speakingMessageId: number | null;
  errorMessage: string | null;
  lastFailedPrompt: string | null;
  currentMood: JarvisMood;
  currentTopicTitle: string;
  torchOn: boolean;
  volumeLevel: number;
  volumeMuted: boolean;
  onLaunchOverlay: () => void;
  onToggleWakeWord: (enabled: boolean) => void;
  onDisableAllOverlays: () => void;
  onUpdateProactiveDraft: (id: number, draft: string) => void;
  onCopyProactiveDraft: (draft: string) => void;
  onOpenProactiveWhatsApp: (alert: ProactiveAlertItem, draft: string) => void;
  onSendProactiveDirectReply: (alert: ProactiveAlertItem, draft: string) => void;
  onDismissProactiveAlert: (id: number) => void;
  onSendCommand: (text: string) => void;
  onRetry: () => void;
  onRunAction: (messageId: number, action: JarvisAction) => void;
  onCopyMessage: (text: string) => void;
  onToggleSpeakMessage: (message: JarvisMessage) => void;
  onVoiceOrbClick: () => void;
  onContextClick: () => void;
  onClearChat: () => void;
  onDismissError: () => void;
}

export const AssistantScreen: React.FC<AssistantScreenProps> = ({
  messages,
  proactiveAlerts,
  preferences,
  voiceState,
  isProcessing,
  speakingMessageId,
  errorMessage,
  lastFailedPrompt,
  currentMood,
  currentTopicTitle,
  torchOn,
  volumeLevel,
  volumeMuted,
  onLaunchOverlay,
  onToggleWakeWord,
  onDisableAllOverlays,
  onUpdateProactiveDraft,
  onCopyProactiveDraft,
  onOpenProactiveWhatsApp,
  onSendProactiveDirectReply,
  onDismissProactiveAlert,
  onSendCommand,
  onRetry,
  onRunAction,
  onCopyMessage,
  onToggleSpeakMessage,
  onVoiceOrbClick,
  onContextClick,
  onClearChat,
  onDismissError,
}) => {
  const [inputText, setInputText] = useState('');
  const [ttsCadenceRms, setTtsCadenceRms] = useState(0);

  const combinedError = errorMessage || voiceState.errorMessage;
  const latestBotMessage = [...messages].reverse().find((m) => !m.isUser) ?? null;

  const visualState: JarvisVisualState = combinedError
    ? 'ERROR'
    : voiceState.isListening
      ? 'LISTENING'
      : isProcessing
        ? 'THINKING'
        : speakingMessageId !== null
          ? 'SPEAKING'
          : 'IDLE';

  const effectiveMood: JarvisMood = combinedError
    ? 'alert'
    : proactiveAlerts.length > 0 && !latestBotMessage
      ? 'serious'
      : currentMood;

  const dynamicHeader = resolveHudHeader({
    isListening: voiceState.isListening,
    isProcessing,
    hasError: combinedError !== null,
    hasProactiveAlert: proactiveAlerts.length > 0,
    latestActionIntent: latestBotMessage?.action?.intent ?? null,
    aiTopicTitle: currentTopicTitle,
  });

  useEffect(() => {
    if (speakingMessageId === null) {
      setTtsCadenceRms(0);
      return;
    }
    let t = 0;
    const timer = window.setInterval(() => {
      t += 0.25;
      const rms = Math.min(
        0.72,
        Math.max(0.15, 0.28 + 0.25 * Math.abs(Math.sin(t)) + 0.14 * Math.abs(Math.sin(t * 2.3)))
      );
      setTtsCadenceRms(rms);
    }, 65);
    return () => window.clearInterval(timer);
  }, [speakingMessageId]);

  const effectiveRms = voiceState.isListening
    ? voiceState.rmsLevel
    : speakingMessageId !== null
      ? ttsCadenceRms
      : isProcessing
        ? 0.18
        : 0;

  const liveSubtitleText = combinedError
    ? combinedError
    : voiceState.isListening
      ? voiceState.partialText || 'Dinliyorum…'
      : isProcessing
        ? 'Nöral analiz yürütülüyor…'
        : latestBotMessage
          ? latestBotMessage.text
          : '';

  const liveSubtitleKey = combinedError
    ? `err_${combinedError}`
    : voiceState.isListening
      ? 'listen'
      : isProcessing
        ? 'proc'
        : latestBotMessage
          ? latestBotMessage.id
          : 'idle';

  const accentColor = resolveMoodAccentColor(visualState, effectiveMood);
  const anyOverlayActive =
    preferences.floatingBubbleEnabled ||
    preferences.persistentTriggerEnabled ||
    preferences.wakeWordEnabled;

  const handleSubmit = (e?: React.FormEvent) => {
    e?.preventDefault();
    const toSend = inputText.trim();
    if (toSend && !isProcessing) {
      onSendCommand(toSend);
      setInputText('');
    }
  };

  return (
    <div className="flex flex-col h-full w-full bg-[var(--jarvis-bg)] items-center overflow-hidden">
      <JarvisTopHudBar
        isListening={voiceState.isListening}
        isProcessing={isProcessing}
        hasUserContext={preferences.userContext.trim().length > 0}
        hasMessages={messages.length > 0}
        torchOn={torchOn}
        volumeLevel={volumeLevel}
        volumeMuted={volumeMuted}
        onContextClick={onContextClick}
        onClearChat={onClearChat}
      />

      {/* Quick Overlay / Wake Word Toolbar */}
      <div className="w-full max-w-[720px] px-4 py-1.5 flex items-center justify-end gap-2 shrink-0">
        {anyOverlayActive && (
          <button
            type="button"
            onClick={onDisableAllOverlays}
            className="inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-xl border border-[var(--jarvis-outline-variant)] text-xs font-mono-hud text-[var(--jarvis-on-surface)] hover:bg-[var(--jarvis-surface-variant)] cursor-pointer whitespace-nowrap"
          >
            <Power className="w-3.5 h-3.5" />
            <span>Kapat</span>
          </button>
        )}

        <button
          type="button"
          onClick={() => onToggleWakeWord(!preferences.wakeWordEnabled)}
          className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl border text-xs font-mono-hud cursor-pointer whitespace-nowrap transition-colors ${
            preferences.wakeWordEnabled
              ? 'border-[var(--jarvis-primary)] text-[var(--jarvis-primary)] bg-[var(--jarvis-primary-container)]/30'
              : 'border-[var(--jarvis-outline-variant)] text-[var(--jarvis-on-surface-muted)] hover:text-[var(--jarvis-on-surface)]'
          }`}
        >
          <Radio className="w-3.5 h-3.5" />
          <span>{preferences.wakeWordEnabled ? '"Jarvis": Açık' : '"Jarvis" Dinle'}</span>
        </button>

        <button
          type="button"
          onClick={onLaunchOverlay}
          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-[var(--jarvis-surface-variant)] border border-[var(--jarvis-outline)] text-xs font-mono-hud text-[var(--jarvis-on-surface)] hover:opacity-90 cursor-pointer whitespace-nowrap"
        >
          <PictureInPicture2 className="w-3.5 h-3.5 text-[#38BDF8]" />
          <span>Kayan Pencere</span>
        </button>
      </div>

      {/* Dynamic HUD Core */}
      <div className="w-full max-w-[720px] py-1.5 flex flex-col items-center gap-1.5 shrink-0">
        <JarvisHudHeader
          topicHeader={dynamicHeader}
          visualState={visualState}
          mood={effectiveMood}
        />

        <button
          type="button"
          onClick={onVoiceOrbClick}
          aria-label="Sesli komut başlat veya durdur"
          className={`rounded-full overflow-hidden cursor-pointer transition-all duration-300 focus-visible:outline-2 focus-visible:outline-[var(--jarvis-primary)] ${
            messages.length === 0 && proactiveAlerts.length === 0
              ? 'w-[196px] h-[196px]'
              : 'w-[156px] h-[156px]'
          }`}
        >
          <ParticleNebulaCanvas
            visualState={visualState}
            mood={effectiveMood}
            rmsLevel={effectiveRms}
            compactMode={false}
          />
        </button>

        <LiveSubtitleDisplay
          subtitleText={liveSubtitleText}
          subtitleKey={liveSubtitleKey}
          visualState={visualState}
          mood={effectiveMood}
        />
      </div>

      {/* Active Proactive WhatsApp Alerts */}
      {proactiveAlerts.length > 0 && (
        <div className="w-full max-w-[720px] max-h-[210px] overflow-y-auto px-4 py-1 flex flex-col gap-2 shrink-0">
          {proactiveAlerts.map((alert) => (
            <ProactiveAlertCard
              key={alert.id}
              alert={alert}
              onDraftChange={(edited) => onUpdateProactiveDraft(alert.id, edited)}
              onCopyDraft={(draft) => onCopyProactiveDraft(draft)}
              onOpenWhatsApp={(draft) => onOpenProactiveWhatsApp(alert, draft)}
              onSendDirectReply={(draft) => onSendProactiveDirectReply(alert, draft)}
              onDismiss={() => onDismissProactiveAlert(alert.id)}
            />
          ))}
        </div>
      )}

      {/* Minimalist Chat Stream OR Starter Chips */}
      <div className="flex-1 w-full max-w-[720px] min-h-0 flex items-end justify-center">
        {messages.length === 0 ? (
          <div className="w-full px-5 py-3.5 flex flex-wrap items-center justify-center gap-2.5">
            {STARTER_COMMANDS.map((item) => (
              <button
                key={item.label}
                type="button"
                onClick={() => onSendCommand(item.command)}
                className="inline-flex items-center gap-2 px-3.5 py-2.5 rounded-[14px] bg-[var(--jarvis-surface)] border border-[var(--jarvis-outline-variant)] hover:border-[var(--jarvis-outline)] text-sm font-display font-medium text-[var(--jarvis-on-surface)] transition-colors cursor-pointer whitespace-nowrap"
              >
                <item.Icon className="w-4 h-4 shrink-0" style={{ color: accentColor }} />
                <span>{item.label}</span>
              </button>
            ))}
          </div>
        ) : (
          <MinimalistChatStreamView
            messages={messages}
            accentColor={accentColor}
            speakingMessageId={speakingMessageId}
            onRunAction={onRunAction}
            onCopyMessage={onCopyMessage}
            onToggleSpeakMessage={onToggleSpeakMessage}
          />
        )}
      </div>

      {/* Error Banner */}
      {combinedError && (
        <div className="w-full max-w-[720px] px-4 py-1.5 shrink-0">
          <div className="w-full rounded-xl bg-[var(--jarvis-error-container)] text-[var(--jarvis-on-error-container)] px-3 py-2 flex items-center gap-2">
            <p className="text-xs flex-1">{combinedError}</p>
            {lastFailedPrompt && !isProcessing && (
              <button
                type="button"
                onClick={onRetry}
                className="px-2.5 py-1 rounded-lg text-xs font-medium hover:bg-black/10 cursor-pointer whitespace-nowrap"
              >
                Tekrar Dene
              </button>
            )}
            <button
              type="button"
              onClick={onDismissError}
              aria-label="Hatayı kapat"
              className="p-1.5 rounded-lg hover:bg-black/10 cursor-pointer"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        </div>
      )}

      {/* Command Dock */}
      <div className="w-full max-w-[720px] bg-[var(--jarvis-bg)] shrink-0">
        <div className="h-px w-full bg-[var(--jarvis-outline-variant)]" />
        <form
          onSubmit={handleSubmit}
          className="w-full px-3.5 py-2.5 flex items-center gap-2.5"
        >
          <input
            type="text"
            value={inputText}
            onChange={(e) => setInputText(e.target.value)}
            placeholder="Komut ver veya sor…"
            className="flex-1 min-w-0 rounded-2xl bg-[var(--jarvis-surface)] border border-[var(--jarvis-outline-variant)] focus:border-[var(--jarvis-primary)] focus:outline-none px-4 py-3 text-sm text-[var(--jarvis-on-surface)] placeholder:text-[var(--jarvis-on-surface-muted)]"
          />

          {inputText.trim().length > 0 && !isProcessing && (
            <button
              type="submit"
              aria-label="Komutu gönder"
              className="w-12 h-12 rounded-full bg-[var(--jarvis-primary)] text-[var(--jarvis-on-primary)] flex items-center justify-center shrink-0 cursor-pointer hover:opacity-90 transition-opacity"
            >
              <Send className="w-5 h-5" />
            </button>
          )}

          <VoiceOrbButton
            isListening={voiceState.isListening}
            rmsLevel={voiceState.rmsLevel}
            onClick={onVoiceOrbClick}
          />
        </form>
      </div>
    </div>
  );
};
