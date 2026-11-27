import React, { useEffect, useState } from 'react';
import {
  AppWindow,
  Check,
  Crosshair,
  Hand,
  PictureInPicture2,
  Power,
  Send,
  SlidersHorizontal,
  Trash2,
} from 'lucide-react';
import type { InstalledAppInfo, JarvisPreferences } from '../types/jarvis';

const CONTEXT_PRESETS = [
  'Kısa ve net yanıt ver',
  'Müzik deyince Spotify aç',
  'Teknik terimleri İngilizce koru',
  'Resmi değil samimi ol',
];

interface SettingsScreenProps {
  preferences: JarvisPreferences;
  installedApps: InstalledAppInfo[];
  isEvaluatingWhatsApp: boolean;
  onLaunchOverlay: () => void;
  onStartCrosshair: () => void;
  onSaveUserContext: (ctx: string) => void;
  onToggleDarkTheme: (enabled: boolean) => void;
  onToggleAutoExecute: (enabled: boolean) => void;
  onToggleSpeakReplies: (enabled: boolean) => void;
  onToggleFloatingBubble: (enabled: boolean) => void;
  onToggleWakeWord: (enabled: boolean) => void;
  onToggleNewsAirGesture: (enabled: boolean) => void;
  onToggleProactiveAlerts: (enabled: boolean) => void;
  onChangeImportanceThreshold: (threshold: number) => void;
  onDisableAllOverlays: () => void;
  onSimulateWhatsAppMessage: (senderName: string, messageText: string) => void;
  onClearHistory: () => void;
}

export const SettingsScreen: React.FC<SettingsScreenProps> = ({
  preferences,
  installedApps,
  isEvaluatingWhatsApp,
  onLaunchOverlay,
  onStartCrosshair,
  onSaveUserContext,
  onToggleDarkTheme,
  onToggleAutoExecute,
  onToggleSpeakReplies,
  onToggleFloatingBubble,
  onToggleWakeWord,
  onToggleNewsAirGesture,
  onToggleProactiveAlerts,
  onChangeImportanceThreshold,
  onDisableAllOverlays,
  onSimulateWhatsAppMessage,
  onClearHistory,
}) => {
  const [contextDraft, setContextDraft] = useState(preferences.userContext);
  const [showAppsPreview, setShowAppsPreview] = useState(false);
  const [showClearConfirm, setShowClearConfirm] = useState(false);
  const [simSender, setSimSender] = useState('Ahmet Yılmaz');
  const [simMessage, setSimMessage] = useState(
    'Yarınki 14:00 müşteri toplantısı acil olarak 10:30’a çekildi, uygun musun?'
  );

  useEffect(() => {
    setContextDraft(preferences.userContext);
  }, [preferences.userContext]);

  const anyOverlayActive =
    preferences.floatingBubbleEnabled ||
    preferences.persistentTriggerEnabled ||
    preferences.wakeWordEnabled;

  return (
    <div className="flex flex-col h-full w-full bg-[var(--jarvis-bg)] items-center overflow-hidden">
      {/* Header */}
      <div className="w-full bg-[var(--jarvis-surface)] shrink-0">
        <div className="w-full max-w-[720px] mx-auto px-5 py-3.5 flex items-center gap-2.5">
          <SlidersHorizontal className="w-5 h-5 text-[var(--jarvis-primary)]" />
          <h1 className="font-display font-semibold text-lg text-[var(--jarvis-on-surface)]">
            Ayarlar
          </h1>
        </div>
        <div className="h-px w-full bg-[var(--jarvis-outline-variant)]" />
      </div>

      <div className="flex-1 w-full max-w-[720px] overflow-y-auto p-4 flex flex-col gap-4">
        {/* Overlay & Quick Trigger Card */}
        <section className="rounded-[18px] bg-[var(--jarvis-surface)] border border-[var(--jarvis-outline-variant)] p-4 flex flex-col gap-3">
          <h2 className="font-display font-medium text-base text-[var(--jarvis-on-surface)]">
            Kayan Pencere (Overlay) ve Hızlı Tetikleme
          </h2>
          <p className="text-xs text-[var(--jarvis-on-surface-muted)]">
            Kayan baloncuk, hızlı komut penceresi veya &quot;Jarvis&quot; sesli uyandırma ile herhangi bir ekranda JARVIS&apos;i açabilirsin.
          </p>

          <SettingToggleRow
            title="Kayan baloncuk"
            subtitle="Ekran köşesinde sade hızlı erişim ikonu gösterir"
            checked={preferences.floatingBubbleEnabled}
            onChange={onToggleFloatingBubble}
          />

          <SettingToggleRow
            title='"Jarvis" sesli uyandırma'
            subtitle='Dinleme açıkken "Jarvis" dediğinizde hızlı pencereyi açar'
            checked={preferences.wakeWordEnabled}
            onChange={onToggleWakeWord}
          />

          {anyOverlayActive && (
            <button
              type="button"
              onClick={onDisableAllOverlays}
              className="w-full py-2 px-3 rounded-xl border border-[var(--jarvis-outline)] text-xs font-medium text-[var(--jarvis-on-surface)] flex items-center justify-center gap-2 hover:bg-[var(--jarvis-surface-variant)] cursor-pointer"
            >
              <Power className="w-4 h-4" />
              <span>Kayan Baloncuk ve Servisi Tamamen Kapat</span>
            </button>
          )}

          <div className="grid grid-cols-2 gap-2">
            <button
              type="button"
              onClick={onLaunchOverlay}
              className="py-2.5 px-3 rounded-xl bg-[var(--jarvis-surface-variant)] text-xs font-mono-hud text-[var(--jarvis-on-surface)] flex items-center justify-center gap-1.5 hover:opacity-90 cursor-pointer"
            >
              <PictureInPicture2 className="w-4 h-4" />
              <span>Kayan Pencere</span>
            </button>
            <button
              type="button"
              onClick={onStartCrosshair}
              className="py-2.5 px-3 rounded-xl bg-[var(--jarvis-surface-variant)] text-xs font-mono-hud text-[var(--jarvis-on-surface)] flex items-center justify-center gap-1.5 hover:opacity-90 cursor-pointer"
            >
              <Crosshair className="w-4 h-4" />
              <span>Nokta Kaydet</span>
            </button>
          </div>
        </section>

        {/* Proactive WhatsApp Assistant Card */}
        <section className="rounded-[18px] bg-[var(--jarvis-surface)] border border-[var(--jarvis-outline-variant)] p-4 flex flex-col gap-3">
          <h2 className="font-display font-medium text-base text-[var(--jarvis-on-surface)]">
            Proaktif WhatsApp Asistanı
          </h2>
          <p className="text-xs text-[var(--jarvis-on-surface-muted)]">
            Gelen WhatsApp mesajlarını önem sırasına göre süzer; eşik üstü mesajlar için özet, gerekçe ve hazır cevap taslağı üretir.
          </p>

          <SettingToggleRow
            title="Proaktif bildirim açık/kapalı"
            subtitle="Önemli WhatsApp mesajlarını otomatik analiz edip taslak hazırlar"
            checked={preferences.proactiveAlertsEnabled}
            onChange={onToggleProactiveAlerts}
          />

          <div className="flex flex-col gap-1.5">
            <div className="flex items-center justify-between">
              <span className="text-sm text-[var(--jarvis-on-surface)]">Önem Eşiği</span>
              <span className="px-2.5 py-1 rounded-lg bg-[var(--jarvis-primary-container)] text-xs font-mono-hud font-medium text-[var(--jarvis-on-primary-container)]">
                {preferences.importanceThreshold} / 10
              </span>
            </div>
            <p className="text-xs text-[var(--jarvis-on-surface-muted)]">
              Sadece {preferences.importanceThreshold} ve üzeri önem puanı alan mesajlar bildirilir (kısa/anlamsız mesajlar API&apos;ye gönderilmeden elenir).
            </p>
            <input
              type="range"
              min={1}
              max={10}
              step={1}
              disabled={!preferences.proactiveAlertsEnabled}
              value={preferences.importanceThreshold}
              onChange={(e) => onChangeImportanceThreshold(Number(e.target.value))}
              className="w-full accent-[var(--jarvis-primary)] cursor-pointer disabled:opacity-50"
            />
          </div>

          {/* Web WhatsApp Evaluation Test Trigger */}
          <div className="pt-2 border-t border-[var(--jarvis-outline-variant)] flex flex-col gap-2">
            <span className="font-mono-hud text-xs text-[var(--jarvis-primary)]">
              WhatsApp Önem Analizini Test Et
            </span>
            <input
              type="text"
              value={simSender}
              onChange={(e) => setSimSender(e.target.value)}
              placeholder="Gönderen adı"
              className="w-full rounded-xl bg-[var(--jarvis-bg)] border border-[var(--jarvis-outline-variant)] px-3 py-2 text-xs text-[var(--jarvis-on-surface)]"
            />
            <textarea
              value={simMessage}
              onChange={(e) => setSimMessage(e.target.value)}
              rows={2}
              placeholder="Gelen WhatsApp mesajı…"
              className="w-full rounded-xl bg-[var(--jarvis-bg)] border border-[var(--jarvis-outline-variant)] px-3 py-2 text-xs text-[var(--jarvis-on-surface)] resize-y"
            />
            <button
              type="button"
              disabled={isEvaluatingWhatsApp || !simSender.trim() || !simMessage.trim()}
              onClick={() => onSimulateWhatsAppMessage(simSender, simMessage)}
              className="self-start inline-flex items-center gap-1.5 px-3 py-2 rounded-xl bg-[var(--jarvis-surface-variant)] border border-[var(--jarvis-outline)] text-xs font-mono-hud text-[var(--jarvis-on-surface)] disabled:opacity-50 cursor-pointer"
            >
              <Send className="w-3.5 h-3.5" />
              <span>{isEvaluatingWhatsApp ? 'Analiz ediliyor…' : 'Mesajı Analiz Et'}</span>
            </button>
          </div>
        </section>

        {/* User Context Card */}
        <section className="rounded-[18px] bg-[var(--jarvis-surface)] border border-[var(--jarvis-outline-variant)] p-4 flex flex-col gap-2.5">
          <h2 className="font-display font-medium text-base text-[var(--jarvis-on-surface)]">
            Kullanıcı Bağlamı
          </h2>
          <p className="text-xs text-[var(--jarvis-on-surface-muted)]">
            JARVIS bu bilgileri her komutta arka plan bağlamı olarak kullanır: hitap şekli, sık kullandığın uygulamalar veya özel kurallar.
          </p>

          <textarea
            value={contextDraft}
            onChange={(e) => setContextDraft(e.target.value)}
            rows={4}
            placeholder="Örn: Yanıtlar kısa olsun. Müzik deyince Spotify aç."
            className="w-full rounded-[14px] bg-[var(--jarvis-bg)] border border-[var(--jarvis-outline-variant)] focus:border-[var(--jarvis-primary)] focus:outline-none px-3 py-2.5 text-sm text-[var(--jarvis-on-surface)] resize-y"
          />

          <div className="flex flex-wrap gap-2">
            {CONTEXT_PRESETS.map((preset) => (
              <button
                key={preset}
                type="button"
                onClick={() => {
                  if (!contextDraft.includes(preset)) {
                    setContextDraft((prev) =>
                      prev.trim() ? `${prev}\n- ${preset}` : preset
                    );
                  }
                }}
                className="px-2.5 py-1.5 rounded-[10px] bg-[var(--jarvis-surface-variant)] font-mono-hud text-[11px] text-[var(--jarvis-on-surface-muted)] hover:text-[var(--jarvis-on-surface)] cursor-pointer"
              >
                + {preset}
              </button>
            ))}
          </div>

          <button
            type="button"
            onClick={() => onSaveUserContext(contextDraft)}
            className="w-full py-2.5 px-4 rounded-xl bg-[var(--jarvis-primary)] text-[var(--jarvis-on-primary)] text-sm font-medium flex items-center justify-center gap-2 hover:opacity-90 cursor-pointer"
          >
            <Check className="w-4 h-4" />
            <span>Bağlamı Kaydet</span>
          </button>
        </section>

        {/* Assistant Behavior Card */}
        <section className="rounded-[18px] bg-[var(--jarvis-surface)] border border-[var(--jarvis-outline-variant)] p-4 flex flex-col gap-3">
          <h2 className="font-display font-medium text-base text-[var(--jarvis-on-surface)]">
            Asistan Davranışı
          </h2>

          <SettingToggleRow
            title="Aksiyonları otomatik çalıştır"
            subtitle="Uygulama açma, URL ve hatırlatıcıları anında uygular"
            checked={preferences.autoExecuteActions}
            onChange={onToggleAutoExecute}
          />

          <SettingToggleRow
            title="Yanıtları sesli oku (TTS)"
            subtitle="JARVIS yanıt özetlerini Türkçe seslendirir"
            checked={preferences.speakReplies}
            onChange={onToggleSpeakReplies}
          />

          <SettingToggleRow
            title="Obsidian Koyu HUD"
            subtitle="Yüksek kontrastlı gece komuta arayüzü"
            checked={preferences.darkTheme}
            onChange={onToggleDarkTheme}
          />
        </section>

        {/* News Broadcast Air Gesture Card */}
        <section className="rounded-[18px] bg-[var(--jarvis-surface)] border border-[var(--jarvis-outline-variant)] p-4 flex flex-col gap-3">
          <div className="flex items-center gap-2">
            <Hand className="w-4.5 h-4.5 text-[var(--jarvis-primary)]" />
            <h2 className="font-display font-medium text-base text-[var(--jarvis-on-surface)]">
              Haber Kanalı ve El Hareketi Kontrolü
            </h2>
          </div>
          <p className="text-xs text-[var(--jarvis-on-surface-muted)]">
            Gündem / Haber Bülteni modundayken ön kamera ile uzaktan sağa veya sola el kaydırarak haberler arasında geçiş yapabilirsiniz. Yayından çıkıldığında kamera anında kapatılır.
          </p>

          <SettingToggleRow
            title="Haber Modunda El Hareketi (Kamera Kontrolü)"
            subtitle="Cihaz içi MediaPipe Hand Landmarker ile temassız haber geçişi (1.2 sn bekleme)"
            checked={preferences.newsAirGestureEnabled}
            onChange={onToggleNewsAirGesture}
          />
        </section>

        {/* Installed Apps Card */}
        <section className="rounded-[18px] bg-[var(--jarvis-surface)] border border-[var(--jarvis-outline-variant)] p-4 flex flex-col gap-2.5">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <AppWindow className="w-4.5 h-4.5 text-[var(--jarvis-primary)]" />
              <span className="font-display font-medium text-sm text-[var(--jarvis-on-surface)]">
                Taranan Uygulamalar ({installedApps.length})
              </span>
            </div>
            <button
              type="button"
              onClick={() => setShowAppsPreview((v) => !v)}
              className="text-xs font-medium text-[var(--jarvis-primary)] cursor-pointer"
            >
              {showAppsPreview ? 'Gizle' : 'Göster'}
            </button>
          </div>

          {showAppsPreview && (
            <div className="flex flex-wrap gap-1.5 pt-1">
              {installedApps.map((app) => (
                <span
                  key={app.packageName}
                  className="px-2 py-1 rounded-lg bg-[var(--jarvis-surface-variant)] font-mono-hud text-[11px] text-[var(--jarvis-on-surface-muted)]"
                >
                  {app.label}
                </span>
              ))}
            </div>
          )}
        </section>

        {/* Clear History */}
        <button
          type="button"
          onClick={() => setShowClearConfirm(true)}
          className="w-full py-2.5 px-4 rounded-xl border border-[var(--jarvis-outline)] text-sm font-medium text-[var(--jarvis-on-surface)] flex items-center justify-center gap-2 hover:bg-[var(--jarvis-surface-variant)] cursor-pointer mb-3"
        >
          <Trash2 className="w-4 h-4" />
          <span>Sohbet Geçmişini Temizle</span>
        </button>
      </div>

      {showClearConfirm && (
        <div className="fixed inset-0 z-50 bg-black/70 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="w-full max-w-sm rounded-[20px] bg-[var(--jarvis-surface)] border border-[var(--jarvis-outline)] p-5 flex flex-col gap-3">
            <h3 className="font-display font-semibold text-base text-[var(--jarvis-on-surface)]">
              Geçmiş temizlensin mi?
            </h3>
            <p className="text-sm text-[var(--jarvis-on-surface-muted)]">
              Tüm konuşma ve aksiyon geçmişi kalıcı olarak silinecek.
            </p>
            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={() => setShowClearConfirm(false)}
                className="px-3 py-1.5 rounded-lg text-xs font-medium text-[var(--jarvis-on-surface-muted)] hover:bg-[var(--jarvis-surface-variant)] cursor-pointer"
              >
                Vazgeç
              </button>
              <button
                type="button"
                onClick={() => {
                  setShowClearConfirm(false);
                  onClearHistory();
                }}
                className="px-3 py-1.5 rounded-lg text-xs font-medium text-[#FB7185] hover:bg-[#FB7185]/10 cursor-pointer"
              >
                Temizle
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

const SettingToggleRow: React.FC<{
  title: string;
  subtitle: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
}> = ({ title, subtitle, checked, onChange }) => (
  <label className="flex items-center justify-between gap-3 cursor-pointer py-1">
    <div className="flex-1 min-w-0">
      <p className="text-sm text-[var(--jarvis-on-surface)]">{title}</p>
      <p className="text-xs text-[var(--jarvis-on-surface-muted)]">{subtitle}</p>
    </div>
    <input
      type="checkbox"
      checked={checked}
      onChange={(e) => onChange(e.target.checked)}
      className="sr-only peer"
    />
    <span className="relative w-11 h-6 rounded-full bg-[var(--jarvis-outline)] peer-checked:bg-[var(--jarvis-primary)] transition-colors shrink-0 after:content-[''] after:absolute after:top-0.5 after:left-0.5 after:w-5 after:h-5 after:rounded-full after:bg-white after:transition-transform peer-checked:after:translate-x-5" />
  </label>
);
