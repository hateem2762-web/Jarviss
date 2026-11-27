import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  AlarmClock,
  Crosshair,
  Newspaper,
  NotebookPen,
  SlidersHorizontal,
  Sparkles,
} from 'lucide-react';
import {
  GENERAL_PROFILE_NAME,
  GENERAL_PROFILE_PACKAGE,
  type JarvisAction,
  type JarvisDestination,
  type JarvisMessage,
  type JarvisMood,
  type JarvisPreferences,
  type NewsBroadcastResponse,
  type NoteItem,
  type ProactiveAlertItem,
  type ReminderItem,
  type TapCoordinateItem,
  type VoiceInputState,
} from './types/jarvis';
import {
  DEFAULT_INSTALLED_APPS,
  loadCoordinates,
  loadMessages,
  loadNotes,
  loadPreferences,
  loadProactiveAlerts,
  loadReminders,
  saveCoordinates,
  saveMessages,
  saveNotes,
  savePreferences,
  saveProactiveAlerts,
  saveReminders,
} from './services/storage';
import {
  askJarvisApi,
  elaborateNewsItemApi,
  evaluateWhatsAppMessageApi,
  fetchNewsBroadcastApi,
} from './services/jarvisApi';
import { executeJarvisAction, launchSafeExternalLink } from './services/actionExecutor';
import {
  speakTurkishText,
  stopSpeaking,
  VoiceRecognitionController,
  WakeWordController,
} from './services/voiceService';
import { findZeroTokenCoordinateMatch } from './utils/coordinateMatcher';
import { matchLocalQuickCommand } from './utils/localQuickCommandMatcher';
import { moodForAction, resolveHudHeader } from './utils/hudTopicResolver';
import { shouldAnalyzeWhatsAppWithAi } from './utils/whatsAppMessageFilter';
import { AssistantScreen } from './screens/AssistantScreen';
import { NewsBroadcastScreen } from './screens/NewsBroadcastScreen';
import { NotesScreen } from './screens/NotesScreen';
import { CoordinatesScreen } from './screens/CoordinatesScreen';
import { RemindersScreen } from './screens/RemindersScreen';
import { SettingsScreen } from './screens/SettingsScreen';
import {
  CrosshairRecorderModal,
  FloatingMiniHud,
} from './components/OverlayAndCrosshairModals';

export function App() {
  const [destination, setDestination] = useState<JarvisDestination>('ASSISTANT');
  const [messages, setMessages] = useState<JarvisMessage[]>(() => loadMessages());
  const [reminders, setReminders] = useState<ReminderItem[]>(() => loadReminders());
  const [coordinates, setCoordinates] = useState<TapCoordinateItem[]>(() => loadCoordinates());
  const [proactiveAlerts, setProactiveAlerts] = useState<ProactiveAlertItem[]>(() =>
    loadProactiveAlerts().filter((a) => !a.isDismissed)
  );
  const [notes, setNotes] = useState<NoteItem[]>(() => loadNotes());
  const [preferences, setPreferences] = useState<JarvisPreferences>(() => loadPreferences());

  const [isProcessing, setIsProcessing] = useState(false);
  const [speakingMessageId, setSpeakingMessageId] = useState<number | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [lastFailedPrompt, setLastFailedPrompt] = useState<string | null>(null);
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const [currentMood, setCurrentMood] = useState<JarvisMood>('neutral');
  const [currentTopicTitle, setCurrentTopicTitle] = useState('');
  const [isEvaluatingWhatsApp, setIsEvaluatingWhatsApp] = useState(false);

  const [voiceState, setVoiceState] = useState<VoiceInputState>({
    isListening: false,
    partialText: '',
    rmsLevel: 0,
    errorMessage: null,
  });

  const [torchOn, setTorchOn] = useState(false);
  const [volumeState, setVolumeState] = useState({ level: 80, muted: false });
  const [showOverlayWindow, setShowOverlayWindow] = useState(false);
  const [showCrosshairModal, setShowCrosshairModal] = useState(false);
  const [tapPulseCoord, setTapPulseCoord] = useState<TapCoordinateItem | null>(null);

  const [newsBroadcast, setNewsBroadcast] = useState<NewsBroadcastResponse | null>(null);
  const [isLoadingNews, setIsLoadingNews] = useState(false);
  const [newsError, setNewsError] = useState<string | null>(null);
  const [activeNewsIndex, setActiveNewsIndex] = useState(0);
  const [isElaboratingNews, setIsElaboratingNews] = useState(false);
  const [elaboratedNewsMap, setElaboratedNewsMap] = useState<Record<string, string>>({});

  const [notificationPermission, setNotificationPermission] = useState<
    NotificationPermission | 'unsupported'
  >(() =>
    typeof window !== 'undefined' && 'Notification' in window
      ? window.Notification.permission
      : 'unsupported'
  );

  const voiceControllerRef = useRef<VoiceRecognitionController | null>(null);
  const wakeWordControllerRef = useRef<WakeWordController | null>(null);
  const firedReminderIdsRef = useRef<Set<number>>(new Set());

  // Sync dark mode class on <html>
  useEffect(() => {
    const root = document.documentElement;
    if (preferences.darkTheme) {
      root.classList.add('dark');
    } else {
      root.classList.remove('dark');
    }
  }, [preferences.darkTheme]);

  // Persist states
  useEffect(() => saveMessages(messages), [messages]);
  useEffect(() => saveReminders(reminders), [reminders]);
  useEffect(() => saveCoordinates(coordinates), [coordinates]);
  useEffect(() => saveProactiveAlerts(proactiveAlerts), [proactiveAlerts]);
  useEffect(() => saveNotes(notes), [notes]);
  useEffect(() => savePreferences(preferences), [preferences]);

  // Auto-hide toast
  useEffect(() => {
    if (!toastMessage) return;
    const t = window.setTimeout(() => setToastMessage(null), 3400);
    return () => window.clearTimeout(t);
  }, [toastMessage]);

  const showToast = useCallback((msg: string) => {
    setToastMessage(msg);
  }, []);

  const loadNewsBroadcast = useCallback(async () => {
    if (isLoadingNews) return;
    setIsLoadingNews(true);
    setNewsError(null);
    try {
      const data = await fetchNewsBroadcastApi();
      setNewsBroadcast(data);
      setActiveNewsIndex(0);
      setElaboratedNewsMap({});
    } catch (err) {
      setNewsError(err instanceof Error ? err.message : 'Gündem bülteni yüklenemedi.');
    } finally {
      setIsLoadingNews(false);
    }
  }, [isLoadingNews]);

  useEffect(() => {
    if (destination === 'NEWS' && !newsBroadcast && !isLoadingNews && !newsError) {
      void loadNewsBroadcast();
    }
  }, [destination, isLoadingNews, loadNewsBroadcast, newsBroadcast, newsError]);

  const elaborateCurrentNewsItem = useCallback(
    async (question?: string): Promise<{ found: boolean; detail: string }> => {
      const items = newsBroadcast?.items ?? [];
      const current = items[activeNewsIndex];
      if (!current) {
        return {
          found: false,
          detail: 'Detaylandırılacak aktif bir haber bulunamadı. Önce "Gündemi aç" deyin.',
        };
      }
      if (!question && elaboratedNewsMap[current.id]) {
        return { found: true, detail: elaboratedNewsMap[current.id] };
      }
      setIsElaboratingNews(true);
      try {
        const detail = await elaborateNewsItemApi({ item: current, question });
        setElaboratedNewsMap((prev) => ({ ...prev, [current.id]: detail }));
        setDestination('NEWS');
        return { found: true, detail };
      } catch (err) {
        const fallback = current.elaboratedScript || current.anchorScript;
        setElaboratedNewsMap((prev) => ({ ...prev, [current.id]: fallback }));
        showToast(
          err instanceof Error ? err.message : 'Detay yerel özetten yüklendi.'
        );
        return { found: true, detail: fallback };
      } finally {
        setIsElaboratingNews(false);
      }
    },
    [activeNewsIndex, elaboratedNewsMap, newsBroadcast?.items, showToast]
  );

  const activeProfilePkg =
    preferences.manualProfilePackageName.trim() || GENERAL_PROFILE_PACKAGE;
  const activeProfileName =
    preferences.manualProfileName.trim() || GENERAL_PROFILE_NAME;

  const getCoordinatesForActiveOrFallback = useCallback(
    (pkg?: string | null) => {
      if (!pkg) return coordinates;
      const specific = coordinates.filter(
        (c) => c.profilePackageName.toLowerCase() === pkg.toLowerCase()
      );
      return specific.length > 0 ? specific : coordinates;
    },
    [coordinates]
  );

  const speakIfEnabled = useCallback(
    (enabled: boolean, messageId: number, text: string) => {
      if (!enabled || !text.trim()) return;
      const started = speakTurkishText(text, {
        volume: volumeState.muted ? 0 : volumeState.level / 100,
        onStart: () => setSpeakingMessageId(messageId),
        onEnd: () => setSpeakingMessageId((cur) => (cur === messageId ? null : cur)),
      });
      if (started) {
        setSpeakingMessageId(messageId);
      }
    },
    [volumeState.level, volumeState.muted]
  );

  // Reminder due watcher
  useEffect(() => {
    const interval = window.setInterval(() => {
      const now = Date.now();
      for (const rem of reminders) {
        if (!rem.isCompleted && rem.triggerAtMillis <= now && !firedReminderIdsRef.current.has(rem.id)) {
          firedReminderIdsRef.current.add(rem.id);
          showToast(`Hatırlatıcı: ${rem.message}`);
          if (preferences.speakReplies) {
            speakTurkishText(`Hatırlatıcı: ${rem.message}`, {
              volume: volumeState.muted ? 0 : volumeState.level / 100,
            });
          }
          if (typeof window !== 'undefined' && 'Notification' in window && window.Notification.permission === 'granted') {
            new window.Notification('JARVIS Hatırlatıcı', { body: rem.message });
          }
        }
      }
    }, 1000);
    return () => window.clearInterval(interval);
  }, [reminders, preferences.speakReplies, showToast, volumeState.level, volumeState.muted]);

  const actionCallbacks = useMemo(
    () => ({
      installedApps: DEFAULT_INSTALLED_APPS,
      getCoordinatesForActiveOrFallback,
      onNavigate: (dest: JarvisDestination) => setDestination(dest),
      onAddReminder: (reminder: ReminderItem) =>
        setReminders((prev) =>
          [...prev, reminder].sort((a, b) => a.triggerAtMillis - b.triggerAtMillis)
        ),
      onAddNote: (noteInput: Omit<NoteItem, 'id' | 'createdAtMillis' | 'updatedAtMillis'>) => {
        const now = Date.now();
        const newNote: NoteItem = {
          ...noteInput,
          id: now + Math.floor(Math.random() * 1000),
          createdAtMillis: now,
          updatedAtMillis: now,
        };
        setNotes((prev) => [newNote, ...prev]);
      },
      onTapVisualFeedback: (coord: TapCoordinateItem) => {
        setTapPulseCoord(coord);
        window.setTimeout(() => setTapPulseCoord(null), 1600);
      },
      onTorchChange: (enabled: boolean) => setTorchOn(enabled),
      getTorchState: () => torchOn,
      onVolumeChange: (level: number, muted: boolean) => setVolumeState({ level, muted }),
      getVolumeState: () => volumeState,
      getActiveScreenText: () => {
        if (destination === 'NEWS' && newsBroadcast?.items[activeNewsIndex]) {
          const item = newsBroadcast.items[activeNewsIndex];
          return {
            text: `${item.category} - ${item.headline}\n${elaboratedNewsMap[item.id] || item.anchorScript}\nKaynak: ${item.sourceName}`,
            sourceAppName: 'JARVIS Haber Kanalı',
          };
        }
        const recentText = messages
          .slice(-6)
          .map((m) => `${m.isUser ? 'Kullanıcı' : 'JARVIS'}: ${m.text}`)
          .join('\n');
        return {
          text:
            recentText ||
            'JARVIS Çalışma Alanı — aktif oturum özeti',
          sourceAppName: `JARVIS (${destination})`,
        };
      },
      onElaborateActiveNews: () => elaborateCurrentNewsItem(),
    }),
    [
      activeNewsIndex,
      destination,
      elaborateCurrentNewsItem,
      elaboratedNewsMap,
      getCoordinatesForActiveOrFallback,
      messages,
      newsBroadcast?.items,
      torchOn,
      volumeState,
    ]
  );

  const dispatchCommand = useCallback(
    async (rawText: string, insertUserBubble: boolean) => {
      const text = rawText.trim();
      if (!text || isProcessing) return;

      setErrorMessage(null);
      setLastFailedPrompt(null);
      setVoiceState((prev) => ({ ...prev, errorMessage: null }));
      setIsProcessing(true);

      const snapshotMessages = messages;
      const now = Date.now();

      if (insertUserBubble) {
        const userMsg: JarvisMessage = {
          id: now,
          isUser: true,
          text,
          action: null,
          actionStatus: 'EXECUTED',
          actionStatusDetail: '',
          timestampMillis: now,
        };
        setMessages((prev) => [...prev, userMsg]);
      }

      try {
        const activeCoordinates = getCoordinatesForActiveOrFallback(activeProfilePkg);

        // 0-Token Fast Path 1: saved screen coordinate
        const zeroTokenMatch = findZeroTokenCoordinateMatch(text, activeCoordinates);
        if (zeroTokenMatch) {
          const execResult = await executeJarvisAction(
            {
              intent: 'tap_coordinate',
              target: zeroTokenMatch.label,
              minutes: null,
              seconds: null,
              message: '',
            },
            activeProfilePkg,
            actionCallbacks
          );
          const replyText =
            execResult.status === 'EXECUTED'
              ? `"${zeroTokenMatch.label}" noktasına dokunuluyor.`
              : execResult.detail;
          setCurrentMood(execResult.status === 'FAILED' ? 'alert' : 'energetic');
          setCurrentTopicTitle('HEDEF KİLİTLEME // OTOMASYON');
          const botId = Date.now() + 1;
          const botMsg: JarvisMessage = {
            id: botId,
            isUser: false,
            text: replyText,
            action: {
              intent: 'tap_coordinate',
              target: zeroTokenMatch.label,
              minutes: null,
              seconds: null,
              message: '',
            },
            actionStatus: execResult.status,
            actionStatusDetail: execResult.detail,
            timestampMillis: botId,
          };
          setMessages((prev) => [...prev, botMsg]);
          speakIfEnabled(preferences.speakReplies, botId, replyText);
          return;
        }

        // 0-Token Fast Path 2: unambiguous local device/screen-note commands
        const localAction = matchLocalQuickCommand(text);
        if (localAction) {
          const execResult = await executeJarvisAction(
            localAction,
            activeProfilePkg,
            actionCallbacks
          );
          const replyText = execResult.detail || 'İşlem tamamlandı.';
          setCurrentMood(
            execResult.status === 'FAILED'
              ? 'alert'
              : moodForAction(localAction.intent, 'energetic')
          );
          setCurrentTopicTitle(
            resolveHudHeader({
              isListening: false,
              isProcessing: false,
              hasError: execResult.status === 'FAILED',
              hasProactiveAlert: false,
              latestActionIntent: localAction.intent,
              aiTopicTitle: '',
            })
          );
          const botId = Date.now() + 1;
          const botMsg: JarvisMessage = {
            id: botId,
            isUser: false,
            text: replyText,
            action: localAction,
            actionStatus: execResult.status,
            actionStatusDetail: execResult.detail,
            actionUrl: execResult.actionUrl,
            timestampMillis: botId,
          };
          setMessages((prev) => [...prev, botMsg]);
          speakIfEnabled(preferences.speakReplies, botId, replyText);
          return;
        }

        // AI Server Call
        const reply = await askJarvisApi({
          userPrompt: text,
          userContext: preferences.userContext,
          recentMessages: snapshotMessages.slice(-6),
          installedApps: DEFAULT_INSTALLED_APPS,
          savedCoordinateLabels: Array.from(new Set(activeCoordinates.map((c) => c.label))),
          activeProfileLabel: activeProfileName,
        });

        const action = reply.action;
        setCurrentMood(action ? moodForAction(action.intent, reply.mood) : reply.mood);
        setCurrentTopicTitle(
          resolveHudHeader({
            isListening: false,
            isProcessing: false,
            hasError: false,
            hasProactiveAlert: false,
            latestActionIntent: action?.intent ?? null,
            aiTopicTitle: reply.topicTitle,
          })
        );

        const botId = Date.now() + 1;
        let finalStatus: JarvisMessage['actionStatus'] = action ? 'PENDING' : 'EXECUTED';
        let finalDetail = '';
        let actionUrl: string | undefined;
        let spokenText = reply.ttsSummary;

        if (action && preferences.autoExecuteActions) {
          const execResult = await executeJarvisAction(
            action,
            activeProfilePkg,
            actionCallbacks
          );
          finalStatus = execResult.status;
          finalDetail = execResult.detail;
          actionUrl = execResult.actionUrl;
          if (execResult.status === 'FAILED') {
            setCurrentMood('alert');
          }
          if (
            (action.intent === 'battery_status' ||
              action.intent === 'torch_control' ||
              action.intent === 'volume_control' ||
              action.intent === 'capture_screen_note') &&
            execResult.detail
          ) {
            spokenText = execResult.detail;
          }
        }

        const botMsg: JarvisMessage = {
          id: botId,
          isUser: false,
          text: reply.text,
          action,
          actionStatus: finalStatus,
          actionStatusDetail: finalDetail,
          actionUrl,
          timestampMillis: botId,
        };
        setMessages((prev) => [...prev, botMsg]);
        speakIfEnabled(preferences.speakReplies, botId, spokenText);
      } catch (err) {
        setCurrentMood('alert');
        setCurrentTopicTitle('SİSTEM UYARISI // KRİTİK');
        setLastFailedPrompt(text);
        setErrorMessage(
          err instanceof Error ? err.message : 'JARVIS yanıt verirken bir hata oluştu.'
        );
      } finally {
        setIsProcessing(false);
      }
    },
    [
      actionCallbacks,
      activeProfileName,
      activeProfilePkg,
      getCoordinatesForActiveOrFallback,
      isProcessing,
      messages,
      preferences.autoExecuteActions,
      preferences.speakReplies,
      preferences.userContext,
      speakIfEnabled,
    ]
  );

  const sendCommand = useCallback(
    (rawText: string) => {
      const normalized = rawText.toLowerCase().trim();
      if (destination === 'NEWS' && newsBroadcast?.items.length) {
        if (
          normalized.includes('sonraki haber') ||
          normalized === 'sonraki' ||
          normalized.includes('sıradaki haber')
        ) {
          setActiveNewsIndex((idx) => (idx + 1) % newsBroadcast.items.length);
          return;
        }
        if (normalized.includes('önceki haber') || normalized === 'önceki') {
          setActiveNewsIndex(
            (idx) => (idx - 1 + newsBroadcast.items.length) % newsBroadcast.items.length
          );
          return;
        }
      }
      void dispatchCommand(rawText, true);
    },
    [destination, dispatchCommand, newsBroadcast?.items.length]
  );

  const startVoiceCommand = useCallback(() => {
    wakeWordControllerRef.current?.pause();
    if (!voiceControllerRef.current) {
      voiceControllerRef.current = new VoiceRecognitionController();
    }
    setVoiceState({
      isListening: true,
      partialText: '',
      rmsLevel: 0.35,
      errorMessage: null,
    });
    voiceControllerRef.current.start({
      onPartial: (partial) => {
        setVoiceState((prev) => ({
          ...prev,
          partialText: partial,
          rmsLevel: 0.45 + Math.random() * 0.35,
        }));
      },
      onFinal: (transcript) => {
        setVoiceState({
          isListening: false,
          partialText: '',
          rmsLevel: 0,
          errorMessage: null,
        });
        sendCommand(transcript);
      },
      onError: (msg) => {
        setVoiceState({
          isListening: false,
          partialText: '',
          rmsLevel: 0,
          errorMessage: msg,
        });
      },
      onEnd: () => {
        setVoiceState((prev) => ({ ...prev, isListening: false, rmsLevel: 0 }));
        if (preferences.wakeWordEnabled) {
          wakeWordControllerRef.current?.resume(() => {
            setShowOverlayWindow(true);
            startVoiceCommand();
          });
        }
      },
    });
  }, [preferences.wakeWordEnabled, sendCommand]);

  const stopVoiceCommand = useCallback(() => {
    voiceControllerRef.current?.stop();
    setVoiceState((prev) => ({ ...prev, isListening: false, rmsLevel: 0 }));
  }, []);

  const handleVoiceOrbClick = useCallback(() => {
    if (voiceState.isListening) {
      stopVoiceCommand();
    } else {
      startVoiceCommand();
    }
  }, [startVoiceCommand, stopVoiceCommand, voiceState.isListening]);

  // Wake Word lifecycle
  useEffect(() => {
    if (!preferences.wakeWordEnabled) {
      wakeWordControllerRef.current?.stop();
      wakeWordControllerRef.current = null;
      return;
    }
    const ctrl = new WakeWordController();
    wakeWordControllerRef.current = ctrl;
    const ok = ctrl.start((remainder) => {
      setShowOverlayWindow(true);
      if (remainder) {
        sendCommand(remainder);
      } else {
        startVoiceCommand();
      }
    });
    if (!ok) {
      showToast('Bu tarayıcıda sesli uyandırma desteklenmiyor.');
      setPreferences((prev) => ({ ...prev, wakeWordEnabled: false }));
    }
    return () => {
      ctrl.stop();
    };
  }, [preferences.wakeWordEnabled, sendCommand, showToast, startVoiceCommand]);

  const handleRunAction = useCallback(
    async (messageId: number, action: JarvisAction) => {
      const execResult = await executeJarvisAction(action, activeProfilePkg, actionCallbacks);
      setMessages((prev) =>
        prev.map((m) =>
          m.id === messageId
            ? {
                ...m,
                actionStatus: execResult.status,
                actionStatusDetail: execResult.detail,
                actionUrl: execResult.actionUrl ?? m.actionUrl,
              }
            : m
        )
      );
      if (execResult.detail) {
        showToast(execResult.detail);
      }
    },
    [actionCallbacks, activeProfilePkg, showToast]
  );

  const handleCopyText = useCallback(
    async (text: string) => {
      try {
        await navigator.clipboard.writeText(text);
        showToast('Panoya kopyalandı.');
      } catch {
        showToast('Panoya kopyalanamadı.');
      }
    },
    [showToast]
  );

  const handleToggleSpeakMessage = useCallback(
    (msg: JarvisMessage) => {
      if (speakingMessageId === msg.id) {
        stopSpeaking();
        setSpeakingMessageId(null);
        return;
      }
      const snippet = msg.text.length > 260 ? `${msg.text.slice(0, 260)}…` : msg.text;
      const started = speakTurkishText(snippet, {
        volume: volumeState.muted ? 0 : volumeState.level / 100,
        onStart: () => setSpeakingMessageId(msg.id),
        onEnd: () => setSpeakingMessageId((cur) => (cur === msg.id ? null : cur)),
      });
      if (!started) {
        showToast('Sesli okuma motoru bu tarayıcıda kullanılamıyor.');
      } else {
        setSpeakingMessageId(msg.id);
      }
    },
    [showToast, speakingMessageId, volumeState.level, volumeState.muted]
  );

  const handleSimulateWhatsApp = useCallback(
    async (senderName: string, messageText: string) => {
      if (!preferences.proactiveAlertsEnabled) {
        showToast('Proaktif WhatsApp takibi kapalı.');
        return;
      }
      if (!shouldAnalyzeWhatsAppWithAi(senderName, messageText)) {
        showToast('Mesaj önemsiz/gündelik olarak filtrelendi (0 API token).');
        return;
      }
      setIsEvaluatingWhatsApp(true);
      try {
        const evalRes = await evaluateWhatsAppMessageApi({
          senderName,
          messageText,
          userContext: preferences.userContext,
        });
        if (evalRes.importanceScore < preferences.importanceThreshold) {
          showToast(
            `Önem puanı (${evalRes.importanceScore}/10) eşiğin (${preferences.importanceThreshold}) altında kaldı.`
          );
          return;
        }
        const now = Date.now();
        const alert: ProactiveAlertItem = {
          id: now,
          sourcePackage: 'com.whatsapp',
          senderName: senderName.trim(),
          originalMessage: messageText.trim(),
          summary: evalRes.summary,
          importanceScore: evalRes.importanceScore,
          importanceReason: evalRes.importanceReason,
          draftReply: evalRes.draftReply,
          messageTimestampMillis: now,
          createdAtMillis: now,
          isDismissed: false,
        };
        setProactiveAlerts((prev) => [alert, ...prev]);
        setDestination('ASSISTANT');
        showToast(`Önemli WhatsApp mesajı (${evalRes.importanceScore}/10) Asistan'a eklendi.`);
      } catch (err) {
        showToast(err instanceof Error ? err.message : 'WhatsApp analizi başarısız.');
      } finally {
        setIsEvaluatingWhatsApp(false);
      }
    },
    [
      preferences.importanceThreshold,
      preferences.proactiveAlertsEnabled,
      preferences.userContext,
      showToast,
    ]
  );

  const activeRemindersCount = reminders.filter((r) => !r.isCompleted).length;
  const latestBotMessage = [...messages].reverse().find((m) => !m.isUser) ?? null;

  const navItems: Array<{
    id: JarvisDestination;
    label: string;
    Icon: React.ComponentType<{ className?: string }>;
    badge?: number;
  }> = [
    { id: 'ASSISTANT', label: 'Asistan', Icon: Sparkles },
    { id: 'NEWS', label: 'Gündem', Icon: Newspaper },
    { id: 'NOTES', label: 'Notlar', Icon: NotebookPen },
    { id: 'COORDINATES', label: 'Noktalar', Icon: Crosshair },
    { id: 'REMINDERS', label: 'Alarmlar', Icon: AlarmClock, badge: activeRemindersCount },
    { id: 'SETTINGS', label: 'Ayarlar', Icon: SlidersHorizontal },
  ];

  return (
    <div className="flex flex-col sm:flex-row h-screen w-screen overflow-hidden bg-[var(--jarvis-bg)] text-[var(--jarvis-on-surface)]">
      {/* Desktop / Tablet Navigation Rail */}
      <aside className="hidden sm:flex flex-col items-center w-20 bg-[var(--jarvis-surface)] border-r border-[var(--jarvis-outline-variant)] py-4 gap-3 shrink-0">
        {navItems.map((item) => {
          const selected = destination === item.id;
          return (
            <button
              key={item.id}
              type="button"
              onClick={() => setDestination(item.id)}
              className="w-16 py-2 rounded-2xl flex flex-col items-center gap-1 transition-colors cursor-pointer group"
            >
              <div
                className={`relative px-3.5 py-1.5 rounded-full transition-colors ${
                  selected
                    ? 'bg-[var(--jarvis-surface-variant)] text-[var(--jarvis-primary)]'
                    : 'text-[var(--jarvis-on-surface-muted)] group-hover:text-[var(--jarvis-on-surface)]'
                }`}
              >
                <item.Icon className="w-5 h-5" />
                {item.badge !== undefined && item.badge > 0 && (
                  <span className="absolute -top-1 -right-1 min-w-[16px] h-4 px-1 rounded-full bg-[#FB7185] text-white text-[10px] font-mono-hud flex items-center justify-center">
                    {item.badge}
                  </span>
                )}
              </div>
              <span
                className={`text-[11px] font-display ${
                  selected
                    ? 'text-[var(--jarvis-on-surface)] font-medium'
                    : 'text-[var(--jarvis-on-surface-muted)]'
                }`}
              >
                {item.label}
              </span>
            </button>
          );
        })}
      </aside>

      {/* Main Content */}
      <main className="flex-1 min-w-0 min-h-0 flex flex-col relative">
        {destination === 'ASSISTANT' && (
          <AssistantScreen
            messages={messages}
            proactiveAlerts={proactiveAlerts}
            preferences={preferences}
            voiceState={voiceState}
            isProcessing={isProcessing}
            speakingMessageId={speakingMessageId}
            errorMessage={errorMessage}
            lastFailedPrompt={lastFailedPrompt}
            currentMood={currentMood}
            currentTopicTitle={currentTopicTitle}
            torchOn={torchOn}
            volumeLevel={volumeState.level}
            volumeMuted={volumeState.muted}
            onLaunchOverlay={() => setShowOverlayWindow(true)}
            onToggleWakeWord={(enabled) =>
              setPreferences((prev) => ({ ...prev, wakeWordEnabled: enabled }))
            }
            onDisableAllOverlays={() => {
              setPreferences((prev) => ({
                ...prev,
                floatingBubbleEnabled: false,
                persistentTriggerEnabled: false,
                wakeWordEnabled: false,
              }));
              setShowOverlayWindow(false);
              showToast('Kayan baloncuk ve hızlı servis kapatıldı.');
            }}
            onUpdateProactiveDraft={(alertId, draft) =>
              setProactiveAlerts((prev) =>
                prev.map((a) => (a.id === alertId ? { ...a, draftReply: draft } : a))
              )
            }
            onCopyProactiveDraft={(draft) => {
              void handleCopyText(draft);
            }}
            onOpenProactiveWhatsApp={(alert, draft) => {
              void handleCopyText(draft);
              launchSafeExternalLink(
                `https://wa.me/?text=${encodeURIComponent(draft)}`
              );
              setProactiveAlerts((prev) =>
                prev.map((a) => (a.id === alert.id ? { ...a, draftReply: draft } : a))
              );
            }}
            onSendProactiveDirectReply={(alert, _draft) => {
              setProactiveAlerts((prev) => prev.filter((a) => a.id !== alert.id));
              showToast(`${alert.senderName} için yanıt gönderildi.`);
            }}
            onDismissProactiveAlert={(id) =>
              setProactiveAlerts((prev) => prev.filter((a) => a.id !== id))
            }
            onSendCommand={sendCommand}
            onRetry={() => {
              if (lastFailedPrompt) {
                void dispatchCommand(lastFailedPrompt, false);
              }
            }}
            onRunAction={handleRunAction}
            onCopyMessage={(txt) => void handleCopyText(txt)}
            onToggleSpeakMessage={handleToggleSpeakMessage}
            onVoiceOrbClick={handleVoiceOrbClick}
            onContextClick={() => setDestination('SETTINGS')}
            onClearChat={() => {
              setMessages([]);
              setCurrentMood('neutral');
              setCurrentTopicTitle('');
              showToast('Sohbet geçmişi temizlendi.');
            }}
            onDismissError={() => {
              setErrorMessage(null);
              setLastFailedPrompt(null);
              setVoiceState((prev) => ({ ...prev, errorMessage: null }));
              if (currentMood === 'alert') {
                setCurrentMood('neutral');
                setCurrentTopicTitle('');
              }
            }}
          />
        )}

        {destination === 'NEWS' && (
          <NewsBroadcastScreen
            broadcast={newsBroadcast}
            isLoading={isLoadingNews}
            errorMessage={newsError}
            activeIndex={activeNewsIndex}
            isElaborating={isElaboratingNews}
            elaboratedMap={elaboratedNewsMap}
            airGestureEnabled={preferences.newsAirGestureEnabled}
            volumeLevel={volumeState.level}
            volumeMuted={volumeState.muted}
            isListeningVoice={voiceState.isListening}
            onChangeIndex={setActiveNewsIndex}
            onRefresh={() => void loadNewsBroadcast()}
            onElaborateCurrent={() => void elaborateCurrentNewsItem()}
            onVoiceClick={handleVoiceOrbClick}
            onCloseToBubble={() => {
              stopSpeaking();
              setPreferences((prev) => ({
                ...prev,
                floatingBubbleEnabled: true,
              }));
              setShowOverlayWindow(false);
              setDestination('ASSISTANT');
              showToast('Haber yayını durduruldu, yüzen baloncuk moduna dönüldü.');
            }}
          />
        )}

        {destination === 'NOTES' && (
          <NotesScreen
            notes={notes}
            onAddNote={(title, content) => {
              const now = Date.now();
              setNotes((prev) => [
                {
                  id: now,
                  title: title.trim() || 'Hızlı Not',
                  content: content.trim(),
                  sourcePackage: 'web.manual',
                  sourceAppName: 'Manuel Not',
                  createdAtMillis: now,
                  updatedAtMillis: now,
                },
                ...prev,
              ]);
              showToast("Not Defteri'ne kaydedildi.");
            }}
            onUpdateNote={(id, title, content) => {
              setNotes((prev) =>
                prev.map((n) =>
                  n.id === id
                    ? { ...n, title, content, updatedAtMillis: Date.now() }
                    : n
                )
              );
              showToast('Not güncellendi.');
            }}
            onDeleteNote={(id) => {
              setNotes((prev) => prev.filter((n) => n.id !== id));
              showToast('Not silindi.');
            }}
            onCopyNote={(txt) => void handleCopyText(txt)}
          />
        )}

        {destination === 'COORDINATES' && (
          <CoordinatesScreen
            coordinates={coordinates}
            installedApps={DEFAULT_INSTALLED_APPS}
            preferences={preferences}
            onSelectManualProfile={(pkg, label) => {
              setPreferences((prev) => ({
                ...prev,
                manualProfilePackageName: pkg,
                manualProfileName: label,
              }));
              showToast(`Aktif profil: ${label}`);
            }}
            onStartCrosshair={() => setShowCrosshairModal(true)}
            onTestTapCoordinate={(coord) => {
              setTapPulseCoord(coord);
              window.setTimeout(() => setTapPulseCoord(null), 1600);
              showToast(
                `"${coord.label}" (${coord.profileName}) noktasına dokunuldu.`
              );
            }}
            onDeleteCoordinate={(id) => {
              setCoordinates((prev) => prev.filter((c) => c.id !== id));
              showToast('Kayıtlı nokta silindi.');
            }}
          />
        )}

        {destination === 'REMINDERS' && (
          <RemindersScreen
            reminders={reminders}
            notificationPermission={notificationPermission}
            onRequestNotificationPermission={() => {
              if ('Notification' in window) {
                void window.Notification.requestPermission().then((perm) =>
                  setNotificationPermission(perm)
                );
              }
            }}
            onToggleComplete={(rem) =>
              setReminders((prev) =>
                prev.map((r) =>
                  r.id === rem.id ? { ...r, isCompleted: !r.isCompleted } : r
                )
              )
            }
            onDeleteReminder={(id) => {
              setReminders((prev) => prev.filter((r) => r.id !== id));
              showToast('Hatırlatıcı silindi.');
            }}
          />
        )}

        {destination === 'SETTINGS' && (
          <SettingsScreen
            preferences={preferences}
            installedApps={DEFAULT_INSTALLED_APPS}
            isEvaluatingWhatsApp={isEvaluatingWhatsApp}
            onLaunchOverlay={() => setShowOverlayWindow(true)}
            onStartCrosshair={() => setShowCrosshairModal(true)}
            onSaveUserContext={(ctx) => {
              setPreferences((prev) => ({ ...prev, userContext: ctx.trim() }));
              showToast('Kullanıcı bağlamı güncellendi.');
            }}
            onToggleDarkTheme={(enabled) =>
              setPreferences((prev) => ({ ...prev, darkTheme: enabled }))
            }
            onToggleAutoExecute={(enabled) =>
              setPreferences((prev) => ({ ...prev, autoExecuteActions: enabled }))
            }
            onToggleSpeakReplies={(enabled) => {
              setPreferences((prev) => ({ ...prev, speakReplies: enabled }));
              if (!enabled) {
                stopSpeaking();
                setSpeakingMessageId(null);
              }
            }}
            onToggleFloatingBubble={(enabled) =>
              setPreferences((prev) => ({ ...prev, floatingBubbleEnabled: enabled }))
            }
            onToggleWakeWord={(enabled) =>
              setPreferences((prev) => ({ ...prev, wakeWordEnabled: enabled }))
            }
            onToggleNewsAirGesture={(enabled) => {
              setPreferences((prev) => ({ ...prev, newsAirGestureEnabled: enabled }));
              showToast(
                enabled
                  ? 'Haber modunda el hareketi (kamera kontrolü) açıldı.'
                  : 'Haber modunda el hareketi kapatıldı.'
              );
            }}
            onToggleProactiveAlerts={(enabled) => {
              setPreferences((prev) => ({ ...prev, proactiveAlertsEnabled: enabled }));
              showToast(
                enabled
                  ? 'Proaktif WhatsApp takibi açıldı.'
                  : 'Proaktif WhatsApp takibi kapatıldı.'
              );
            }}
            onChangeImportanceThreshold={(threshold) =>
              setPreferences((prev) => ({ ...prev, importanceThreshold: threshold }))
            }
            onDisableAllOverlays={() => {
              setPreferences((prev) => ({
                ...prev,
                floatingBubbleEnabled: false,
                persistentTriggerEnabled: false,
                wakeWordEnabled: false,
              }));
              setShowOverlayWindow(false);
              showToast('Kayan baloncuk ve hızlı servis kapatıldı.');
            }}
            onSimulateWhatsAppMessage={(sender, msg) =>
              void handleSimulateWhatsApp(sender, msg)
            }
            onClearHistory={() => {
              setMessages([]);
              setCurrentMood('neutral');
              setCurrentTopicTitle('');
              showToast('Sohbet geçmişi temizlendi.');
            }}
          />
        )}

        {/* Simulated Flashlight HUD Glow */}
        {torchOn && (
          <div className="pointer-events-none fixed inset-x-0 top-0 h-1.5 bg-amber-300 shadow-[0_0_24px_8px_rgba(251,191,36,0.55)] z-50" />
        )}

        {/* Visual Tap Coordinate Ripple Feedback */}
        {tapPulseCoord && (
          <div
            className="pointer-events-none fixed z-50 -translate-x-1/2 -translate-y-1/2 flex flex-col items-center gap-1"
            style={{
              left: `${tapPulseCoord.xPercent * 100}%`,
              top: `${tapPulseCoord.yPercent * 100}%`,
            }}
          >
            <div className="w-12 h-12 rounded-full border-2 border-[#38BDF8] bg-[#38BDF8]/25 animate-ping" />
            <span className="px-2 py-0.5 rounded bg-[#090B0F]/90 border border-[#38BDF8] font-mono-hud text-[11px] text-[#38BDF8]">
              {tapPulseCoord.label}
            </span>
          </div>
        )}

        {/* Toast / Snackbar */}
        {toastMessage && (
          <div className="fixed bottom-20 sm:bottom-6 left-1/2 -translate-x-1/2 z-50 px-4 py-2.5 rounded-xl bg-[var(--jarvis-surface)] border border-[var(--jarvis-outline)] shadow-xl text-xs font-medium text-[var(--jarvis-on-surface)] max-w-[90vw] text-center">
            {toastMessage}
          </div>
        )}
      </main>

      {/* Mobile Bottom Navigation Bar */}
      <nav className="sm:hidden w-full bg-[var(--jarvis-surface)] border-t border-[var(--jarvis-outline-variant)] flex items-center justify-around py-1.5 shrink-0">
        {navItems.map((item) => {
          const selected = destination === item.id;
          return (
            <button
              key={item.id}
              type="button"
              onClick={() => setDestination(item.id)}
              className="flex flex-col items-center gap-0.5 px-2 py-1 cursor-pointer min-w-[56px]"
            >
              <div
                className={`relative px-3 py-1 rounded-full ${
                  selected
                    ? 'bg-[var(--jarvis-surface-variant)] text-[var(--jarvis-primary)]'
                    : 'text-[var(--jarvis-on-surface-muted)]'
                }`}
              >
                <item.Icon className="w-5 h-5" />
                {item.badge !== undefined && item.badge > 0 && (
                  <span className="absolute -top-0.5 right-0.5 min-w-[15px] h-4 px-1 rounded-full bg-[#FB7185] text-white text-[10px] font-mono-hud flex items-center justify-center">
                    {item.badge}
                  </span>
                )}
              </div>
              <span
                className={`text-[11px] font-display truncate max-w-[64px] ${
                  selected
                    ? 'text-[var(--jarvis-on-surface)] font-medium'
                    : 'text-[var(--jarvis-on-surface-muted)]'
                }`}
              >
                {item.label}
              </span>
            </button>
          );
        })}
      </nav>

      {/* Floating Mini-HUD & Bubble */}
      <FloatingMiniHud
        showBubble={preferences.floatingBubbleEnabled}
        showWindow={showOverlayWindow}
        visualState={
          errorMessage
            ? 'ERROR'
            : voiceState.isListening
              ? 'LISTENING'
              : isProcessing
                ? 'THINKING'
                : speakingMessageId !== null
                  ? 'SPEAKING'
                  : 'IDLE'
        }
        mood={currentMood}
        rmsLevel={voiceState.rmsLevel}
        lastReply={latestBotMessage?.text ?? ''}
        isProcessing={isProcessing}
        onOpenWindow={() => setShowOverlayWindow(true)}
        onMinimizeWindow={() => setShowOverlayWindow(false)}
        onCloseAll={() => {
          setShowOverlayWindow(false);
          setPreferences((prev) => ({
            ...prev,
            floatingBubbleEnabled: false,
            wakeWordEnabled: false,
          }));
        }}
        onSendCommand={sendCommand}
        onVoiceClick={handleVoiceOrbClick}
        onOpenCrosshair={() => setShowCrosshairModal(true)}
      />

      {/* Interactive Crosshair Coordinate Recorder */}
      {showCrosshairModal && (
        <CrosshairRecorderModal
          activeProfileName={activeProfileName}
          activeProfilePkg={activeProfilePkg}
          onSavePoint={(point) => {
            const now = Date.now();
            setCoordinates((prev) => {
              const filtered = prev.filter(
                (c) =>
                  !(
                    c.profilePackageName === activeProfilePkg &&
                    c.label.toLowerCase() === point.label.toLowerCase()
                  )
              );
              return [
                ...filtered,
                {
                  id: now,
                  profilePackageName: activeProfilePkg,
                  profileName: activeProfileName,
                  label: point.label,
                  xPercent: point.xPercent,
                  yPercent: point.yPercent,
                  xPx: point.xPx,
                  yPx: point.yPx,
                  createdAtMillis: now,
                },
              ];
            });
            showToast(`"${point.label}" koordinatı kaydedildi.`);
          }}
          onClose={() => setShowCrosshairModal(false)}
        />
      )}
    </div>
  );
}

export default App;
