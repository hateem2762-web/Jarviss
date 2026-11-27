import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ChevronLeft,
  ChevronRight,
  ExternalLink,
  Hand,
  Loader2,
  Mic,
  Pause,
  Play,
  Radio,
  RefreshCw,
  Sparkles,
  Square,
  X,
} from 'lucide-react';
import type {
  BroadcastNewsItem,
  JarvisMood,
  JarvisVisualState,
  NewsBroadcastResponse,
  NewsCategory,
} from '../types/jarvis';
import { ParticleNebulaCanvas } from '../components/ParticleNebulaCanvas';
import { speakTurkishText, stopSpeaking } from '../services/voiceService';
import {
  AirGestureController,
  type AirGestureDirection,
  type GestureSensorStatus,
} from '../services/airGestureService';

interface NewsBroadcastScreenProps {
  broadcast: NewsBroadcastResponse | null;
  isLoading: boolean;
  errorMessage: string | null;
  activeIndex: number;
  isElaborating: boolean;
  elaboratedMap: Record<string, string>;
  airGestureEnabled: boolean;
  volumeLevel: number;
  volumeMuted: boolean;
  isListeningVoice: boolean;
  onChangeIndex: (index: number) => void;
  onRefresh: () => void;
  onElaborateCurrent: () => void;
  onVoiceClick: () => void;
  onCloseToBubble: () => void;
}

function categoryAccent(cat: NewsCategory): string {
  switch (cat) {
    case 'GÜNDEM':
      return '#FBBF24';
    case 'TEKNOLOJİ':
      return '#38BDF8';
    case 'SPOR':
      return '#34D399';
    case 'DÜNYA':
      return '#A78BFA';
  }
}

export const NewsBroadcastScreen: React.FC<NewsBroadcastScreenProps> = ({
  broadcast,
  isLoading,
  errorMessage,
  activeIndex,
  isElaborating,
  elaboratedMap,
  airGestureEnabled,
  volumeLevel,
  volumeMuted,
  isListeningVoice,
  onChangeIndex,
  onRefresh,
  onElaborateCurrent,
  onVoiceClick,
  onCloseToBubble,
}) => {
  const [isPaused, setIsPaused] = useState(false);
  const [isSpeakingNow, setIsSpeakingNow] = useState(false);
  const [ttsRms, setTtsRms] = useState(0);
  const [failedImages, setFailedImages] = useState<Record<string, boolean>>({});
  const [gestureStatus, setGestureStatus] = useState<GestureSensorStatus>('IDLE');
  const [gestureNote, setGestureNote] = useState<string>('');
  const [gestureFeedback, setGestureFeedback] = useState<string | null>(null);
  const [visibleChars, setVisibleChars] = useState(0);

  const pointerStartXRef = useRef<number | null>(null);
  const gestureControllerRef = useRef<AirGestureController | null>(null);
  const lastFetchedIdRef = useRef<number>(0);
  const lastSpokenElaborationKeyRef = useRef<string>('');
  const activeIndexRef = useRef(activeIndex);
  activeIndexRef.current = activeIndex;
  const isPausedRef = useRef(isPaused);
  isPausedRef.current = isPaused;

  const items = useMemo(() => broadcast?.items ?? [], [broadcast?.items]);
  const safeIndex = items.length > 0 ? Math.min(Math.max(0, activeIndex), items.length - 1) : 0;
  const currentItem: BroadcastNewsItem | null = items[safeIndex] ?? null;

  const activeElaboration = currentItem ? elaboratedMap[currentItem.id] : undefined;
  const activeScript = currentItem
    ? activeElaboration || currentItem.anchorScript
    : broadcast?.introScript || '';
  const scriptKey = currentItem
    ? `${broadcast?.fetchedAtMillis ?? 0}_${currentItem.id}_${activeElaboration ? 'elab' : 'base'}`
    : 'empty';

  // TTS cadence RMS oscillation for mini Particle Nebula
  useEffect(() => {
    if (!isSpeakingNow) {
      return;
    }
    let t = 0;
    const timer = window.setInterval(() => {
      t += 0.28;
      const rms = Math.min(
        0.78,
        Math.max(0.18, 0.32 + 0.26 * Math.abs(Math.sin(t)) + 0.14 * Math.abs(Math.sin(t * 2.1)))
      );
      setTtsRms(rms);
    }, 65);
    return () => {
      window.clearInterval(timer);
      setTtsRms(0);
    };
  }, [isSpeakingNow]);

  // Typewriter live subtitle synced to active news script
  useEffect(() => {
    const text = activeScript.trim();
    if (!text) {
      return;
    }
    let current = 0;
    const step = text.length > 260 ? 3 : text.length > 120 ? 2 : 1;
    const intervalMs = text.length > 260 ? 22 : 28;
    const resetTimer = window.setTimeout(() => setVisibleChars(0), 0);
    const timer = window.setInterval(() => {
      current = Math.min(text.length, current + step);
      setVisibleChars(current);
      if (current >= text.length) {
        window.clearInterval(timer);
      }
    }, intervalMs);
    return () => {
      window.clearTimeout(resetTimer);
      window.clearInterval(timer);
    };
  }, [scriptKey, activeScript]);

  // Speak a specific news item and auto-advance sequentially
  const speakStoryAtIndex = useCallback(
    (index: number, itemList: BroadcastNewsItem[], customText?: string) => {
      const target = itemList[index];
      if (!target || isPausedRef.current) return;
      const spokenBody =
        customText ||
        `${target.category} haberi. ${target.headline}. ${target.anchorScript}`;
      const startedAt = Date.now();
      const ok = speakTurkishText(spokenBody, {
        volume: volumeMuted ? 0 : volumeLevel / 100,
        onStart: () => setIsSpeakingNow(true),
        onEnd: () => {
          setIsSpeakingNow(false);
          const elapsed = Date.now() - startedAt;
          // Prevent rapid loop if browser speech engine aborts instantaneously
          if (!isPausedRef.current && elapsed > 800 && index + 1 < itemList.length) {
            onChangeIndex(index + 1);
          }
        },
      });
      if (!ok) {
        setIsSpeakingNow(false);
      }
    },
    [onChangeIndex, volumeLevel, volumeMuted]
  );

  const prevSpokenIndexRef = useRef<string>('');

  // Start intro + story 1 when a fresh broadcast arrives
  useEffect(() => {
    if (!broadcast || broadcast.items.length === 0) return;
    if (lastFetchedIdRef.current === broadcast.fetchedAtMillis) return;
    lastFetchedIdRef.current = broadcast.fetchedAtMillis;
    prevSpokenIndexRef.current = `${broadcast.fetchedAtMillis}_${activeIndexRef.current}`;

    if (isPausedRef.current) return;
    const introStartedAt = Date.now();
    const ok = speakTurkishText(broadcast.introScript, {
      volume: volumeMuted ? 0 : volumeLevel / 100,
      onStart: () => setIsSpeakingNow(true),
      onEnd: () => {
        setIsSpeakingNow(false);
        const elapsed = Date.now() - introStartedAt;
        if (!isPausedRef.current && elapsed > 400) {
          speakStoryAtIndex(activeIndexRef.current, broadcast.items);
        }
      },
    });
    if (!ok) {
      setIsSpeakingNow(false);
    }
  }, [broadcast, speakStoryAtIndex, volumeLevel, volumeMuted]);

  // Speak whenever activeIndex changes (after intro has played)
  useEffect(() => {
    if (!broadcast || broadcast.items.length === 0 || isPaused) return;
    if (lastFetchedIdRef.current !== broadcast.fetchedAtMillis) return;
    const key = `${broadcast.fetchedAtMillis}_${safeIndex}`;
    if (prevSpokenIndexRef.current === key) return;
    prevSpokenIndexRef.current = key;
    speakStoryAtIndex(safeIndex, broadcast.items);
  }, [broadcast, isPaused, safeIndex, speakStoryAtIndex]);

  // Speak newly generated elaboration immediately
  useEffect(() => {
    if (!currentItem || !activeElaboration || isPaused) return;
    const elabKey = `${currentItem.id}_${activeElaboration.length}`;
    if (lastSpokenElaborationKeyRef.current === elabKey) return;
    lastSpokenElaborationKeyRef.current = elabKey;
    speakStoryAtIndex(safeIndex, items, activeElaboration);
  }, [activeElaboration, currentItem, isPaused, items, safeIndex, speakStoryAtIndex]);

  // Stop speech when leaving NewsBroadcastScreen
  useEffect(() => {
    return () => {
      stopSpeaking();
    };
  }, []);

  const handleTogglePause = useCallback(() => {
    if (isPaused) {
      setIsPaused(false);
      isPausedRef.current = false;
      if (currentItem) {
        speakStoryAtIndex(safeIndex, items, activeElaboration);
      }
    } else {
      setIsPaused(true);
      isPausedRef.current = true;
      stopSpeaking();
      setIsSpeakingNow(false);
    }
  }, [activeElaboration, currentItem, isPaused, items, safeIndex, speakStoryAtIndex]);

  const goNext = useCallback(() => {
    if (items.length <= 1) return;
    onChangeIndex((safeIndex + 1) % items.length);
  }, [items.length, onChangeIndex, safeIndex]);

  const goPrev = useCallback(() => {
    if (items.length <= 1) return;
    onChangeIndex((safeIndex - 1 + items.length) % items.length);
  }, [items.length, onChangeIndex, safeIndex]);

  const goNextRef = useRef(goNext);
  goNextRef.current = goNext;
  const goPrevRef = useRef(goPrev);
  goPrevRef.current = goPrev;

  // Keyboard navigation
  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'ArrowRight') goNextRef.current();
      else if (e.key === 'ArrowLeft') goPrevRef.current();
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, []);

  // Optional MediaPipe Air Gesture camera controller with auto-release on exit or tab minimize
  useEffect(() => {
    if (!airGestureEnabled) {
      gestureControllerRef.current?.stop();
      gestureControllerRef.current = null;
      return;
    }

    const controller = new AirGestureController();
    gestureControllerRef.current = controller;

    const handleSwipe = (direction: AirGestureDirection) => {
      if (direction === 'SWIPE_RIGHT') {
        goNextRef.current();
        setGestureFeedback('SAĞA EL HAREKETİ · SONRAKİ HABER');
      } else {
        goPrevRef.current();
        setGestureFeedback('SOLA EL HAREKETİ · ÖNCEKİ HABER');
      }
    };

    const startSensor = () => {
      void controller.start({
        onSwipe: handleSwipe,
        onStatusChange: (status, note) => {
          setGestureStatus(status);
          setGestureNote(note ?? '');
        },
      });
    };

    startSensor();

    const handleVisibility = () => {
      if (document.hidden) {
        controller.stop();
      } else {
        startSensor();
      }
    };
    document.addEventListener('visibilitychange', handleVisibility);

    return () => {
      document.removeEventListener('visibilitychange', handleVisibility);
      controller.stop();
      gestureControllerRef.current = null;
      setGestureStatus('IDLE');
    };
  }, [airGestureEnabled]);

  useEffect(() => {
    if (!gestureFeedback) return;
    const t = window.setTimeout(() => setGestureFeedback(null), 1500);
    return () => window.clearTimeout(t);
  }, [gestureFeedback]);

  const handleClose = () => {
    stopSpeaking();
    setIsSpeakingNow(false);
    gestureControllerRef.current?.stop();
    onCloseToBubble();
  };

  const nebulaVisualState: JarvisVisualState = isListeningVoice
    ? 'LISTENING'
    : isLoading || isElaborating
      ? 'THINKING'
      : isSpeakingNow
        ? 'SPEAKING'
        : 'IDLE';
  const nebulaMood: JarvisMood = isSpeakingNow
    ? safeIndex % 2 === 0
      ? 'serious'
      : 'energetic'
    : 'energetic';

  const displayedSubtitle = activeScript.slice(
    0,
    Math.min(activeScript.length, Math.max(0, visibleChars))
  );

  return (
    <div className="flex flex-col h-full w-full bg-[var(--jarvis-bg)] text-[var(--jarvis-on-surface)] overflow-hidden">
      {/* Top Broadcast Bar */}
      <header className="w-full bg-[var(--jarvis-surface)] border-b border-[var(--jarvis-outline-variant)] shrink-0">
        <div className="w-full max-w-[1040px] mx-auto px-4 py-2.5 flex items-center justify-between gap-3">
          <div className="flex items-center gap-3 min-w-0">
            <div className="w-11 h-11 rounded-full overflow-hidden bg-[var(--jarvis-bg)] border border-amber-400/40 shrink-0">
              <ParticleNebulaCanvas
                visualState={nebulaVisualState}
                mood={nebulaMood}
                rmsLevel={isSpeakingNow ? ttsRms : isLoading ? 0.2 : 0.06}
                compactMode
              />
            </div>

            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <Radio className="w-3.5 h-3.5 text-amber-400 shrink-0" />
                <span className="font-mono-hud text-xs font-semibold tracking-[0.08em] text-[var(--jarvis-on-surface)] truncate">
                  JARVIS NEWS NETWORK // CANLI BRİFİNG
                </span>
              </div>
              <div className="flex flex-wrap items-center gap-x-2.5 gap-y-0.5 mt-0.5 font-mono-hud text-[11px] text-[var(--jarvis-on-surface-muted)]">
                <span>
                  {isLoading
                    ? 'RSS kaynakları taranıyor…'
                    : isPaused
                      ? 'Yayın duraklatıldı'
                      : isSpeakingNow
                        ? 'Spiker yayında'
                        : `${items.length} başlık hazır`}
                </span>

                {airGestureEnabled && (
                  <span
                    className={`inline-flex items-center gap-1 ${
                      gestureStatus === 'ACTIVE'
                        ? 'text-emerald-400'
                        : gestureStatus === 'PERMISSION_DENIED' || gestureStatus === 'ERROR'
                          ? 'text-rose-400'
                          : 'text-amber-400'
                    }`}
                    title={gestureNote || undefined}
                  >
                    <Hand className="w-3 h-3" />
                    <span>
                      {gestureStatus === 'ACTIVE'
                        ? 'GESTURE SENSOR // AKTİF'
                        : gestureStatus === 'INITIALIZING'
                          ? 'GESTURE SENSOR // BAŞLATILIYOR'
                          : gestureStatus === 'PERMISSION_DENIED'
                            ? 'GESTURE // KAMERA İZNİ YOK'
                            : gestureStatus === 'UNSUPPORTED'
                              ? 'GESTURE // DESTEKLENMİYOR'
                              : 'GESTURE SENSOR // HAZIR'}
                    </span>
                  </span>
                )}
              </div>
            </div>
          </div>

          <div className="flex items-center gap-2 shrink-0">
            <button
              type="button"
              onClick={handleTogglePause}
              disabled={items.length === 0 || isLoading}
              className="inline-flex items-center gap-1.5 px-3 py-2 rounded-xl bg-[var(--jarvis-surface-variant)] border border-[var(--jarvis-outline-variant)] text-xs font-mono-hud text-[var(--jarvis-on-surface)] hover:border-[var(--jarvis-outline)] disabled:opacity-50 cursor-pointer whitespace-nowrap"
            >
              {isPaused ? <Play className="w-3.5 h-3.5" /> : <Pause className="w-3.5 h-3.5" />}
              <span className="hidden sm:inline">{isPaused ? 'Devam Et' : 'Duraklat'}</span>
            </button>

            <button
              type="button"
              onClick={onRefresh}
              disabled={isLoading}
              title="Bülteni yenile"
              aria-label="Bülteni yenile"
              className="p-2 rounded-xl bg-[var(--jarvis-surface-variant)] border border-[var(--jarvis-outline-variant)] text-[var(--jarvis-on-surface-muted)] hover:text-[var(--jarvis-on-surface)] disabled:opacity-50 cursor-pointer"
            >
              <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin' : ''}`} />
            </button>

            <button
              type="button"
              onClick={handleClose}
              title="Yayını kapat ve yüzen baloncuğa dön"
              aria-label="Yayını kapat"
              className="p-2 rounded-xl bg-[var(--jarvis-surface-variant)] border border-[var(--jarvis-outline-variant)] text-[var(--jarvis-on-surface)] hover:bg-rose-500/15 hover:text-rose-400 cursor-pointer"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        </div>
      </header>

      {/* Main News Stage */}
      <div className="flex-1 min-h-0 w-full max-w-[1040px] mx-auto px-4 py-3 flex flex-col gap-3">
        {isLoading && items.length === 0 ? (
          <div className="flex-1 rounded-[22px] bg-[var(--jarvis-surface)] border border-[var(--jarvis-outline-variant)] flex flex-col items-center justify-center p-8 text-center gap-4">
            <div className="w-20 h-20 rounded-full overflow-hidden border border-[#38BDF8]/40">
              <ParticleNebulaCanvas
                visualState="THINKING"
                mood="energetic"
                rmsLevel={0.25}
                compactMode
              />
            </div>
            <div className="space-y-1.5 max-w-md">
              <h2 className="font-display font-semibold text-lg text-[var(--jarvis-on-surface)]">
                Canlı Haber Bülteni Hazırlanıyor
              </h2>
              <p className="text-sm text-[var(--jarvis-on-surface-muted)]">
                Açık RSS kaynakları taranıyor, tık tuzağı başlıklar eleniyor ve en önemli 6 gelişme spiker özetine dönüştürülüyor…
              </p>
            </div>
          </div>
        ) : errorMessage && items.length === 0 ? (
          <div className="flex-1 rounded-[22px] bg-[var(--jarvis-surface)] border border-[var(--jarvis-outline-variant)] flex flex-col items-center justify-center p-8 text-center gap-4">
            <p className="text-sm text-rose-400 max-w-md">{errorMessage}</p>
            <button
              type="button"
              onClick={onRefresh}
              className="px-4 py-2.5 rounded-xl bg-[var(--jarvis-primary)] text-[var(--jarvis-on-primary)] text-xs font-mono-hud cursor-pointer"
            >
              Tekrar Dene
            </button>
          </div>
        ) : currentItem ? (
          <div
            onPointerDown={(e) => {
              pointerStartXRef.current = e.clientX;
            }}
            onPointerUp={(e) => {
              if (pointerStartXRef.current === null) return;
              const dx = e.clientX - pointerStartXRef.current;
              pointerStartXRef.current = null;
              if (Math.abs(dx) >= 48) {
                if (dx < 0) goNext();
                else goPrev();
              }
            }}
            className="relative flex-1 min-h-0 rounded-[22px] overflow-hidden border border-[var(--jarvis-outline)] bg-[#090B0F] select-none flex flex-col justify-end"
          >
            {/* News Photo or Futuristic Abstract Fallback */}
            {currentItem.imageUrl && !failedImages[currentItem.id] ? (
              <img
                key={currentItem.id}
                src={currentItem.imageUrl}
                alt={currentItem.headline}
                referrerPolicy="no-referrer"
                onError={() =>
                  setFailedImages((prev) => ({ ...prev, [currentItem.id]: true }))
                }
                className="absolute inset-0 w-full h-full object-cover object-center transition-opacity duration-500"
              />
            ) : (
              <div
                aria-hidden="true"
                className="absolute inset-0 overflow-hidden"
                style={{
                  background:
                    'radial-gradient(circle at 75% 22%, rgba(56, 189, 248, 0.28), transparent 45%), radial-gradient(circle at 20% 35%, rgba(251, 191, 36, 0.22), transparent 48%), linear-gradient(135deg, #090B0F 0%, #101827 55%, #0B1220 100%)',
                }}
              >
                <div
                  className="absolute inset-0 opacity-25"
                  style={{
                    backgroundImage:
                      'linear-gradient(to right, rgba(56,189,248,0.16) 1px, transparent 1px), linear-gradient(to bottom, rgba(56,189,248,0.16) 1px, transparent 1px)',
                    backgroundSize: '42px 42px',
                  }}
                />
                <div className="absolute right-8 top-8 flex items-center gap-2 font-mono-hud text-xs text-sky-300/65">
                  <span>JARVIS BROADCAST FEED</span>
                  <span>·</span>
                  <span>{currentItem.category}</span>
                </div>
              </div>
            )}

            {/* Contrast Scrim */}
            <div className="absolute inset-0 bg-gradient-to-t from-[#090B0F] via-[#090B0F]/80 to-[#090B0F]/25 pointer-events-none" />

            {/* Top Stage Controls: Counter + Air Gesture Feedback */}
            <div className="absolute top-4 left-4 right-4 flex items-center justify-between gap-2 z-10 pointer-events-none">
              <div className="pointer-events-auto flex items-center gap-2 px-3 py-1.5 rounded-xl bg-[#090B0F]/75 backdrop-blur-md border border-white/15 font-mono-hud text-xs text-slate-200 tabular-nums">
                <span>
                  {String(safeIndex + 1).padStart(2, '0')} / {String(items.length).padStart(2, '0')}
                </span>
                <span aria-hidden="true">·</span>
                <span>{currentItem.sourceName}</span>
                <span aria-hidden="true">·</span>
                <span className="text-slate-400">{currentItem.publishedAt}</span>
              </div>

              {gestureFeedback && (
                <div className="px-3 py-1.5 rounded-xl bg-emerald-500/20 backdrop-blur-md border border-emerald-400/40 font-mono-hud text-xs text-emerald-300">
                  {gestureFeedback}
                </div>
              )}
            </div>

            {/* Left / Right Slide Arrows */}
            {items.length > 1 && (
              <>
                <button
                  type="button"
                  onClick={goPrev}
                  aria-label="Önceki haber"
                  className="hidden sm:flex absolute left-3 top-1/2 -translate-y-1/2 z-10 w-10 h-10 rounded-full bg-[#090B0F]/70 border border-white/15 text-white items-center justify-center hover:bg-[#090B0F]/90 cursor-pointer"
                >
                  <ChevronLeft className="w-5 h-5" />
                </button>
                <button
                  type="button"
                  onClick={goNext}
                  aria-label="Sonraki haber"
                  className="hidden sm:flex absolute right-3 top-1/2 -translate-y-1/2 z-10 w-10 h-10 rounded-full bg-[#090B0F]/70 border border-white/15 text-white items-center justify-center hover:bg-[#090B0F]/90 cursor-pointer"
                >
                  <ChevronRight className="w-5 h-5" />
                </button>
              </>
            )}

            {/* Bottom Content Overlay: Category, Headline, Live Subtitle, Actions */}
            <div className="relative z-10 p-5 sm:p-7 flex flex-col gap-3">
              <div className="flex items-center gap-2 font-mono-hud text-xs tracking-[0.08em]">
                <span
                  className="w-2 h-2 rounded-full"
                  style={{ backgroundColor: categoryAccent(currentItem.category) }}
                />
                <span style={{ color: categoryAccent(currentItem.category) }}>
                  {currentItem.category}
                </span>
                {activeElaboration && (
                  <>
                    <span className="text-slate-500" aria-hidden="true">
                      ·
                    </span>
                    <span className="text-sky-300">DETAYLI ANALİZ</span>
                  </>
                )}
              </div>

              <h2 className="font-display font-semibold text-xl sm:text-3xl leading-snug text-white max-w-4xl text-balance">
                {currentItem.headline}
              </h2>

              {/* Live Streaming Bulletin Subtitle */}
              <div className="rounded-2xl bg-[#090B0F]/75 backdrop-blur-md border border-white/10 px-4 py-3 max-w-4xl">
                <p className="text-sm sm:text-[15px] leading-relaxed text-slate-100 min-h-[44px]">
                  {displayedSubtitle || activeScript}
                </p>
              </div>

              {/* Action Controls & Slide Indicators */}
              <div className="flex flex-wrap items-center justify-between gap-3 pt-1">
                <div className="flex flex-wrap items-center gap-2">
                  <button
                    type="button"
                    onClick={onElaborateCurrent}
                    disabled={isElaborating}
                    className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-sky-500/20 border border-sky-400/45 text-xs font-mono-hud text-sky-200 hover:bg-sky-500/30 disabled:opacity-50 cursor-pointer whitespace-nowrap"
                  >
                    {isElaborating ? (
                      <Loader2 className="w-3.5 h-3.5 animate-spin" />
                    ) : (
                      <Sparkles className="w-3.5 h-3.5" />
                    )}
                    <span>{isElaborating ? 'Detaylandırılıyor…' : 'Haberi Detaylandır'}</span>
                  </button>

                  <button
                    type="button"
                    onClick={onVoiceClick}
                    className={`inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl border text-xs font-mono-hud cursor-pointer whitespace-nowrap ${
                      isListeningVoice
                        ? 'bg-rose-500/25 border-rose-400 text-rose-200'
                        : 'bg-white/10 border-white/15 text-slate-200 hover:bg-white/15'
                    }`}
                  >
                    {isListeningVoice ? (
                      <Square className="w-3.5 h-3.5" />
                    ) : (
                      <Mic className="w-3.5 h-3.5" />
                    )}
                    <span>
                      {isListeningVoice ? 'Dinleniyor…' : '"Bu haberi detaylandır"'}
                    </span>
                  </button>

                  {currentItem.articleUrl && (
                    <a
                      href={currentItem.articleUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="inline-flex items-center gap-1.5 px-3 py-2 rounded-xl bg-white/10 border border-white/15 text-xs font-mono-hud text-slate-200 hover:bg-white/15 whitespace-nowrap"
                    >
                      <ExternalLink className="w-3.5 h-3.5" />
                      <span>Kaynağı Aç</span>
                    </a>
                  )}
                </div>

                {/* Carousel Dots */}
                <div className="flex items-center gap-1.5">
                  {items.map((item, idx) => (
                    <button
                      key={item.id}
                      type="button"
                      onClick={() => onChangeIndex(idx)}
                      aria-label={`Haber ${idx + 1}`}
                      className={`h-2 rounded-full transition-all cursor-pointer ${
                        idx === safeIndex
                          ? 'w-6 bg-amber-400'
                          : 'w-2 bg-white/30 hover:bg-white/55'
                      }`}
                    />
                  ))}
                </div>
              </div>
            </div>
          </div>
        ) : null}
      </div>

      {/* Bottom News Ticker */}
      {items.length > 0 && (
        <footer className="w-full bg-[var(--jarvis-surface)] border-t border-[var(--jarvis-outline-variant)] h-10 flex items-center overflow-hidden shrink-0">
          <div className="px-3.5 h-full bg-amber-500/15 border-r border-[var(--jarvis-outline-variant)] flex items-center gap-1.5 shrink-0 z-10">
            <span className="font-mono-hud text-[11px] font-semibold tracking-[0.06em] text-amber-400 whitespace-nowrap">
              SON DAKİKA / ÖZET AKIŞI
            </span>
          </div>

          <div className="flex-1 min-w-0 overflow-hidden jarvis-ticker-wrap">
            <div className="jarvis-ticker-track items-center gap-8 px-6">
              {[...items, ...items].map((item, dupIdx) => {
                const originalIndex = dupIdx % items.length;
                return (
                  <button
                    key={`${item.id}_${dupIdx}`}
                    type="button"
                    onClick={() => onChangeIndex(originalIndex)}
                    className="inline-flex items-center gap-2 text-xs text-[var(--jarvis-on-surface-muted)] hover:text-[var(--jarvis-on-surface)] cursor-pointer whitespace-nowrap shrink-0"
                  >
                    <span
                      className="font-mono-hud text-[11px] font-semibold"
                      style={{ color: categoryAccent(item.category) }}
                    >
                      {item.category}
                    </span>
                    <span>{item.headline}</span>
                    <span className="text-[var(--jarvis-outline)]" aria-hidden="true">
                      •
                    </span>
                  </button>
                );
              })}
            </div>
          </div>
        </footer>
      )}
    </div>
  );
};
