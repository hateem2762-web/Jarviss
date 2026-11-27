import React, { useEffect, useRef, useState } from 'react';
import {
  AlarmClock,
  BatteryCharging,
  CheckCircle2,
  Code2,
  Clock,
  ExternalLink,
  Flashlight,
  Globe,
  Home,
  Mic,
  MousePointerClick,
  NotebookPen,
  Play,
  PlayCircle,
  Settings,
  Square,
  SlidersHorizontal,
  Trash2,
  Volume2,
  AlertCircle,
} from 'lucide-react';
import {
  ACTION_INTENT_LABELS,
  toPrettyActionJson,
  formatActionDurationLabel,
  type ActionIntent,
  type JarvisAction,
  type JarvisMessage,
  type JarvisMood,
  type JarvisVisualState,
} from '../types/jarvis';
import { resolveMoodAccentColor } from '../utils/hudTopicResolver';

interface JarvisTopHudBarProps {
  isListening: boolean;
  isProcessing: boolean;
  hasUserContext: boolean;
  hasMessages: boolean;
  torchOn: boolean;
  volumeLevel: number;
  volumeMuted: boolean;
  onContextClick: () => void;
  onClearChat: () => void;
}

export const JarvisTopHudBar: React.FC<JarvisTopHudBarProps> = ({
  isListening,
  isProcessing,
  hasUserContext,
  hasMessages,
  torchOn,
  volumeLevel,
  volumeMuted,
  onContextClick,
  onClearChat,
}) => {
  const statusText = isListening ? 'Dinleniyor…' : isProcessing ? 'İşleniyor…' : 'Hazır';
  const statusColor = isListening ? '#FB7185' : isProcessing ? '#FBBF24' : '#34D399';

  return (
    <div className="w-full bg-[var(--jarvis-bg)] shrink-0">
      <div className="flex items-center px-5 py-3.5 max-w-[720px] mx-auto w-full">
        <div className="w-9 h-9 rounded-full bg-[var(--jarvis-surface-variant)] border border-[var(--jarvis-outline)] flex items-center justify-center shrink-0">
          <span className="font-display font-medium text-base text-[var(--jarvis-on-surface)]">
            J
          </span>
        </div>

        <div className="ml-3 flex-1 min-w-0">
          <h1 className="font-display font-semibold text-lg leading-6 text-[var(--jarvis-on-surface)]">
            JARVIS
          </h1>
          <div className="flex items-center gap-1.5">
            <span
              className="w-[7px] h-[7px] rounded-full inline-block"
              style={{ backgroundColor: statusColor }}
            />
            <span className="font-mono-hud text-xs text-[var(--jarvis-on-surface-muted)]">
              {statusText}
            </span>
            {torchOn && (
              <span className="font-mono-hud text-[11px] text-amber-400 ml-2">
                · Fener Açık
              </span>
            )}
            {(volumeMuted || volumeLevel !== 80) && (
              <span className="font-mono-hud text-[11px] text-[var(--jarvis-on-surface-muted)] ml-1">
                · {volumeMuted ? 'Sessiz' : `Ses %${volumeLevel}`}
              </span>
            )}
          </div>
        </div>

        <button
          type="button"
          onClick={onContextClick}
          className={`flex items-center gap-1.5 px-2.5 py-1.5 rounded-[10px] border text-xs font-mono-hud transition-colors cursor-pointer whitespace-nowrap shrink-0 ${
            hasUserContext
              ? 'bg-[var(--jarvis-primary-container)]/70 border-[var(--jarvis-outline-variant)] text-[var(--jarvis-on-surface)]'
              : 'bg-[var(--jarvis-surface)] border-[var(--jarvis-outline-variant)] text-[var(--jarvis-on-surface)]'
          }`}
        >
          <SlidersHorizontal
            className="w-3.5 h-3.5"
            style={{
              color: hasUserContext ? 'var(--jarvis-primary)' : 'var(--jarvis-on-surface-muted)',
            }}
          />
          <span>{hasUserContext ? 'Bağlam' : 'Ayar'}</span>
        </button>

        {hasMessages && (
          <button
            type="button"
            onClick={onClearChat}
            title="Sohbet geçmişini temizle"
            aria-label="Sohbet geçmişini temizle"
            className="ml-1.5 p-2 rounded-lg text-[var(--jarvis-on-surface-muted)] hover:text-[var(--jarvis-on-surface)] hover:bg-[var(--jarvis-surface-variant)] transition-colors cursor-pointer"
          >
            <Trash2 className="w-4 h-4" />
          </button>
        )}
      </div>
      <div className="h-px w-full bg-[var(--jarvis-outline-variant)]" />
    </div>
  );
};

interface JarvisHudHeaderProps {
  topicHeader: string;
  visualState: JarvisVisualState;
  mood: JarvisMood;
}

export const JarvisHudHeader: React.FC<JarvisHudHeaderProps> = ({
  topicHeader,
  visualState,
  mood,
}) => {
  const accent = resolveMoodAccentColor(visualState, mood);

  return (
    <div className="w-full flex flex-col items-center gap-1.5">
      <div className="flex items-center justify-center gap-2">
        <span
          className="w-1.5 h-1.5 rounded-full transition-colors duration-300"
          style={{ backgroundColor: accent }}
        />
        <span
          className="font-mono-hud text-xs font-semibold tracking-[0.09em] transition-colors duration-300 truncate max-w-full"
          style={{ color: accent }}
        >
          {topicHeader || 'ÇEKİRDEK HAZIR // BEKLEMEDE'}
        </span>
      </div>
      <div
        className="w-[148px] h-px transition-all duration-300"
        style={{
          background: `linear-gradient(to right, transparent, ${accent}A6, transparent)`,
        }}
      />
    </div>
  );
};

interface LiveSubtitleDisplayProps {
  subtitleText: string;
  subtitleKey: string | number;
  visualState: JarvisVisualState;
  mood: JarvisMood;
}

export const LiveSubtitleDisplay: React.FC<LiveSubtitleDisplayProps> = ({
  subtitleText,
  subtitleKey,
  visualState,
  mood,
}) => {
  const accent = resolveMoodAccentColor(visualState, mood);
  const shouldAnimate =
    visualState === 'SPEAKING' || (visualState === 'IDLE' && subtitleText.trim().length > 0);
  const [visibleChars, setVisibleChars] = useState(
    shouldAnimate ? 0 : subtitleText.length
  );

  useEffect(() => {
    if (!shouldAnimate || !subtitleText.trim()) {
      setVisibleChars(subtitleText.length);
      return;
    }
    setVisibleChars(0);
    const step = subtitleText.length > 260 ? 4 : subtitleText.length > 110 ? 2 : 1;
    const delayMs = subtitleText.length > 260 ? 12 : subtitleText.length > 110 ? 16 : 22;
    let current = 0;
    const timer = window.setInterval(() => {
      current = Math.min(subtitleText.length, current + step);
      setVisibleChars(current);
      if (current >= subtitleText.length) {
        window.clearInterval(timer);
      }
    }, delayMs);
    return () => window.clearInterval(timer);
  }, [subtitleKey, subtitleText, shouldAnimate]);

  const displayedText = !subtitleText.trim()
    ? visualState === 'LISTENING'
      ? 'Dinliyorum…'
      : visualState === 'THINKING'
        ? 'İşleniyor…'
        : visualState === 'ERROR'
          ? 'Sistem hatası algılandı.'
          : 'Komut bekleniyor'
    : subtitleText.slice(0, Math.min(subtitleText.length, Math.max(0, visibleChars)));

  const colorStyle =
    visualState === 'ERROR'
      ? '#FECDD3'
      : !subtitleText.trim() && visualState === 'IDLE'
        ? 'color-mix(in srgb, var(--jarvis-on-surface-muted) 72%, transparent)'
        : visualState === 'LISTENING'
          ? accent
          : 'var(--jarvis-on-surface)';

  return (
    <div className="w-full px-4 min-h-[38px] flex items-center justify-center">
      <p
        className={`text-sm leading-5 text-center line-clamp-4 ${
          visualState === 'LISTENING' ? 'font-medium' : 'font-normal'
        }`}
        style={{ color: colorStyle }}
      >
        {displayedText}
      </p>
    </div>
  );
};

interface VoiceOrbButtonProps {
  isListening: boolean;
  rmsLevel: number;
  onClick: () => void;
}

export const VoiceOrbButton: React.FC<VoiceOrbButtonProps> = ({
  isListening,
  rmsLevel,
  onClick,
}) => {
  const scale = isListening ? Math.min(1.42, 1.15 + rmsLevel * 0.25) : 1;

  return (
    <div className="relative w-14 h-14 flex items-center justify-center shrink-0">
      {isListening && (
        <span
          className="absolute inset-1 rounded-full bg-[#FB7185]/25 animate-ping pointer-events-none"
          style={{ transform: `scale(${scale})` }}
        />
      )}
      <button
        type="button"
        onClick={onClick}
        aria-label="Sesli komut başlat veya durdur"
        className={`relative w-12 h-12 rounded-full flex items-center justify-center transition-colors cursor-pointer ${
          isListening
            ? 'bg-[#FB7185] text-white'
            : 'bg-[var(--jarvis-primary)] text-[var(--jarvis-on-primary)]'
        }`}
      >
        {isListening ? <Square className="w-5 h-5" /> : <Mic className="w-5 h-5" />}
      </button>
    </div>
  );
};

function ActionIntentIcon({ intent }: { intent: ActionIntent }) {
  const cls = 'w-4 h-4 text-[var(--jarvis-primary)] shrink-0';
  switch (intent) {
    case 'open_app':
      return <ExternalLink className={cls} />;
    case 'open_url':
      return <Globe className={cls} />;
    case 'search_youtube':
      return <PlayCircle className={cls} />;
    case 'set_reminder':
      return <AlarmClock className={cls} />;
    case 'go_home':
      return <Home className={cls} />;
    case 'tap_coordinate':
      return <MousePointerClick className={cls} />;
    case 'torch_control':
      return <Flashlight className={cls} />;
    case 'volume_control':
      return <Volume2 className={cls} />;
    case 'battery_status':
      return <BatteryCharging className={cls} />;
    case 'open_system_settings':
      return <Settings className={cls} />;
    case 'capture_screen_note':
      return <NotebookPen className={cls} />;
    default:
      return <Code2 className={cls} />;
  }
}

interface ActionDispatchCardProps {
  message: JarvisMessage;
  action: JarvisAction;
  onRunAction: () => void;
}

export const ActionDispatchCard: React.FC<ActionDispatchCardProps> = ({
  message,
  action,
  onRunAction,
}) => {
  const [showJson, setShowJson] = useState(false);

  const statusMeta =
    message.actionStatus === 'EXECUTED'
      ? { label: 'Tamamlandı', color: '#34D399', Icon: CheckCircle2 }
      : message.actionStatus === 'FAILED'
        ? { label: 'Başarısız', color: '#FB7185', Icon: AlertCircle }
        : { label: 'Hazır', color: '#FBBF24', Icon: Clock };

  const summaryParts: string[] = [];
  if (action.target.trim()) summaryParts.push(`Hedef: ${action.target}`);
  if (action.minutes !== null || action.seconds !== null) {
    summaryParts.push(`Süre: ${formatActionDurationLabel(action)}`);
  }
  if (action.message.trim()) summaryParts.push(`Not: ${action.message}`);

  return (
    <div className="mt-2 w-full rounded-[14px] bg-[var(--jarvis-surface-variant)]/55 border border-[var(--jarvis-outline-variant)] p-3 flex flex-col gap-2">
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2 min-w-0">
          <ActionIntentIcon intent={action.intent} />
          <span className="font-display font-medium text-sm text-[var(--jarvis-on-surface)] truncate">
            {ACTION_INTENT_LABELS[action.intent]}
          </span>
        </div>

        <span
          className="inline-flex items-center gap-1 px-2 py-1 rounded-lg text-[11px] font-mono-hud font-medium shrink-0"
          style={{
            backgroundColor: `${statusMeta.color}24`,
            color: statusMeta.color,
          }}
        >
          <statusMeta.Icon className="w-3 h-3" />
          {statusMeta.label}
        </span>
      </div>

      {summaryParts.length > 0 && (
        <p className="text-xs text-[var(--jarvis-on-surface-muted)]">
          {summaryParts.join('  •  ')}
        </p>
      )}

      {message.actionStatusDetail && message.actionStatusDetail !== message.text && (
        <p
          className="text-xs"
          style={{
            color:
              message.actionStatus === 'FAILED'
                ? '#FB7185'
                : 'var(--jarvis-on-surface-muted)',
          }}
        >
          {message.actionStatusDetail}
        </p>
      )}

      <div className="flex flex-wrap items-center gap-2">
        <button
          type="button"
          onClick={onRunAction}
          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-[var(--jarvis-outline)] text-xs font-mono-hud text-[var(--jarvis-on-surface)] hover:bg-[var(--jarvis-surface-variant)] transition-colors cursor-pointer"
        >
          <Play className="w-3.5 h-3.5" />
          <span>{message.actionStatus === 'PENDING' ? 'Çalıştır' : 'Tekrarla'}</span>
        </button>

        <button
          type="button"
          onClick={() => setShowJson((v) => !v)}
          className="inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-[10px] bg-[var(--jarvis-surface)] border border-[var(--jarvis-outline-variant)] text-xs font-mono-hud text-[var(--jarvis-on-surface-muted)] hover:text-[var(--jarvis-on-surface)] transition-colors cursor-pointer"
        >
          <Code2 className="w-3.5 h-3.5 text-[#38BDF8]" />
          <span>JSON</span>
        </button>

        {message.actionUrl && (
          <a
            href={message.actionUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-[10px] bg-[var(--jarvis-surface)] border border-[var(--jarvis-outline-variant)] text-xs font-mono-hud text-[var(--jarvis-primary)] hover:underline"
          >
            <ExternalLink className="w-3.5 h-3.5" />
            <span>Bağlantıyı Aç</span>
          </a>
        )}
      </div>

      {showJson && (
        <pre className="w-full rounded-[10px] bg-[var(--jarvis-bg)] border border-[var(--jarvis-outline-variant)] p-2.5 font-mono-hud text-[11px] text-[#38BDF8] overflow-x-auto whitespace-pre-wrap">
          {toPrettyActionJson(action)}
        </pre>
      )}
    </div>
  );
};

interface MinimalistChatStreamViewProps {
  messages: JarvisMessage[];
  accentColor: string;
  speakingMessageId: number | null;
  onRunAction: (messageId: number, action: JarvisAction) => void;
  onCopyMessage: (text: string) => void;
  onToggleSpeakMessage: (message: JarvisMessage) => void;
}

export const MinimalistChatStreamView: React.FC<MinimalistChatStreamViewProps> = ({
  messages,
  accentColor,
  speakingMessageId,
  onRunAction,
  onCopyMessage,
  onToggleSpeakMessage,
}) => {
  const bottomRef = useRef<HTMLDivElement | null>(null);
  const [expandedActionIds, setExpandedActionIds] = useState<Record<number, boolean>>({});

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages.length]);

  const toggleExpand = (id: number) => {
    setExpandedActionIds((prev) => ({ ...prev, [id]: !prev[id] }));
  };

  return (
    <div className="relative w-full h-full overflow-y-auto chat-fade-mask px-4 py-3">
      <div className="flex flex-col justify-end min-h-full">
        {messages.map((item) => {
          const isSpeaking = speakingMessageId === item.id;
          const action = item.action;
          const statusMark =
            item.actionStatus === 'EXECUTED'
              ? '✓'
              : item.actionStatus === 'FAILED'
                ? '!'
                : '▶';
          const statusColor =
            item.actionStatus === 'EXECUTED'
              ? '#10B981'
              : item.actionStatus === 'FAILED'
                ? '#F43F5E'
                : accentColor;
          const detailSuffix =
            item.actionStatusDetail && item.actionStatusDetail !== item.text
              ? ` · ${item.actionStatusDetail}`
              : '';

          return (
            <div
              key={item.id}
              className="py-2 border-b border-[var(--jarvis-outline-variant)]/50 last:border-b-0"
            >
              <div className="flex items-center justify-between gap-2">
                <span
                  className="font-mono-hud text-[10.5px] tracking-[0.08em]"
                  style={{
                    color: item.isUser
                      ? 'color-mix(in srgb, var(--jarvis-on-surface-muted) 80%, transparent)'
                      : accentColor,
                  }}
                >
                  {item.isUser ? 'SEN' : 'JARVIS'}
                </span>

                {!item.isUser && (
                  <div className="flex items-center gap-2 font-mono-hud text-[10.5px] text-[var(--jarvis-on-surface-muted)]">
                    <button
                      type="button"
                      onClick={() => onToggleSpeakMessage(item)}
                      className="hover:text-[var(--jarvis-on-surface)] cursor-pointer"
                    >
                      {isSpeaking ? '■ DURDUR' : '▶ DİNLE'}
                    </button>
                    <span aria-hidden="true">•</span>
                    <button
                      type="button"
                      onClick={() => onCopyMessage(item.text)}
                      className="hover:text-[var(--jarvis-on-surface)] cursor-pointer"
                    >
                      KOPYALA
                    </button>
                  </div>
                )}
              </div>

              <p
                className="mt-1 text-sm leading-5 whitespace-pre-wrap"
                style={{
                  color: item.isUser
                    ? 'var(--jarvis-on-surface-muted)'
                    : 'var(--jarvis-on-surface)',
                }}
              >
                {item.text}
              </p>

              {action && (
                <div className="mt-1.5">
                  <div className="flex flex-wrap items-center gap-2">
                    <button
                      type="button"
                      onClick={() => onRunAction(item.id, action)}
                      className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-[var(--jarvis-surface-variant)]/75 border border-[var(--jarvis-outline-variant)] font-mono-hud text-[11.5px] max-w-full truncate cursor-pointer"
                      style={{ color: statusColor }}
                    >
                      <span className="truncate">
                        {statusMark} {ACTION_INTENT_LABELS[action.intent]}
                        {detailSuffix}
                      </span>
                    </button>

                    <button
                      type="button"
                      onClick={() => toggleExpand(item.id)}
                      className="font-mono-hud text-[11px] text-[var(--jarvis-on-surface-muted)] hover:text-[var(--jarvis-on-surface)] cursor-pointer"
                    >
                      {expandedActionIds[item.id] ? 'Detayı Gizle' : 'Aksiyon / JSON'}
                    </button>

                    {item.actionUrl && (
                      <a
                        href={item.actionUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="inline-flex items-center gap-1 font-mono-hud text-[11px] text-[var(--jarvis-primary)] hover:underline"
                      >
                        <ExternalLink className="w-3 h-3" />
                        <span>Aç</span>
                      </a>
                    )}
                  </div>

                  {expandedActionIds[item.id] && (
                    <ActionDispatchCard
                      message={item}
                      action={action}
                      onRunAction={() => onRunAction(item.id, action)}
                    />
                  )}
                </div>
              )}
            </div>
          );
        })}
        <div ref={bottomRef} />
      </div>
    </div>
  );
};
