import React, { useRef, useState } from 'react';
import { Crosshair, Mic, Minimize2, Plus, Send, X } from 'lucide-react';
import type { JarvisMood, JarvisVisualState } from '../types/jarvis';
import { ParticleNebulaCanvas } from './ParticleNebulaCanvas';

interface CrosshairRecorderModalProps {
  activeProfileName: string;
  activeProfilePkg: string;
  onSavePoint: (params: {
    label: string;
    xPercent: number;
    yPercent: number;
    xPx: number;
    yPx: number;
  }) => void;
  onClose: () => void;
}

export const CrosshairRecorderModal: React.FC<CrosshairRecorderModalProps> = ({
  activeProfileName,
  activeProfilePkg,
  onSavePoint,
  onClose,
}) => {
  const surfaceRef = useRef<HTMLDivElement | null>(null);
  const [coords, setCoords] = useState({ xPercent: 0.5, yPercent: 0.5, xPx: 540, yPx: 960 });
  const [label, setLabel] = useState('');

  const updatePointer = (clientX: number, clientY: number) => {
    const el = surfaceRef.current;
    if (!el) return;
    const rect = el.getBoundingClientRect();
    const xPct = Math.min(1, Math.max(0, (clientX - rect.left) / rect.width));
    const yPct = Math.min(1, Math.max(0, (clientY - rect.top) / rect.height));
    setCoords({
      xPercent: xPct,
      yPercent: yPct,
      xPx: Math.round(xPct * rect.width),
      yPx: Math.round(yPct * rect.height),
    });
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/75 backdrop-blur-sm flex items-center justify-center p-4">
      <div className="w-full max-w-2xl rounded-[20px] bg-[var(--jarvis-surface)] border border-[var(--jarvis-outline)] p-5 flex flex-col gap-4">
        <div className="flex items-center justify-between gap-3">
          <div>
            <h2 className="font-display font-semibold text-base text-[var(--jarvis-on-surface)]">
              Nişangah ile Ekran Noktası Kaydet
            </h2>
            <p className="text-xs text-[var(--jarvis-on-surface-muted)]">
              Profil: {activeProfileName} ({activeProfilePkg})
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-2 rounded-lg text-[var(--jarvis-on-surface-muted)] hover:text-[var(--jarvis-on-surface)] hover:bg-[var(--jarvis-surface-variant)] cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div
          ref={surfaceRef}
          onClick={(e) => updatePointer(e.clientX, e.clientY)}
          className="relative w-full h-64 rounded-2xl bg-[var(--jarvis-bg)] border border-[var(--jarvis-outline)] overflow-hidden cursor-crosshair select-none"
          style={{
            backgroundImage:
              'radial-gradient(circle at 1px 1px, rgba(148,163,184,0.18) 1px, transparent 0)',
            backgroundSize: '20px 20px',
          }}
        >
          <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
            <span className="font-mono-hud text-xs text-[var(--jarvis-on-surface-muted)]/60">
              Hedef noktayı seçmek için alana tıklayın
            </span>
          </div>

          {/* Crosshair lines */}
          <div
            className="absolute top-0 bottom-0 w-px bg-[#38BDF8]/50 pointer-events-none"
            style={{ left: `${coords.xPercent * 100}%` }}
          />
          <div
            className="absolute left-0 right-0 h-px bg-[#38BDF8]/50 pointer-events-none"
            style={{ top: `${coords.yPercent * 100}%` }}
          />

          <div
            className="absolute -translate-x-1/2 -translate-y-1/2 pointer-events-none flex items-center justify-center w-9 h-9 rounded-full border-2 border-[#38BDF8] bg-[#38BDF8]/15"
            style={{
              left: `${coords.xPercent * 100}%`,
              top: `${coords.yPercent * 100}%`,
            }}
          >
            <Crosshair className="w-4 h-4 text-[#38BDF8]" />
          </div>
        </div>

        <div className="flex flex-wrap items-center justify-between gap-2 font-mono-hud text-xs text-[#38BDF8]">
          <span>
            Oran: ({(coords.xPercent * 100).toFixed(1)}%, {(coords.yPercent * 100).toFixed(1)}%)
          </span>
          <span>
            Piksel: {coords.xPx} × {coords.yPx} px
          </span>
        </div>

        <div className="flex flex-col sm:flex-row gap-2">
          <input
            type="text"
            value={label}
            onChange={(e) => setLabel(e.target.value)}
            placeholder="Nokta etiketi (ör. Transfer, Başlat, Gönder)…"
            className="flex-1 rounded-xl bg-[var(--jarvis-bg)] border border-[var(--jarvis-outline-variant)] focus:border-[var(--jarvis-primary)] focus:outline-none px-3.5 py-2.5 text-sm text-[var(--jarvis-on-surface)]"
          />
          <button
            type="button"
            disabled={!label.trim()}
            onClick={() => {
              onSavePoint({
                label: label.trim(),
                ...coords,
              });
              onClose();
            }}
            className="px-4 py-2.5 rounded-xl bg-[var(--jarvis-primary)] text-[var(--jarvis-on-primary)] text-sm font-medium disabled:opacity-50 cursor-pointer whitespace-nowrap"
          >
            Noktayı Kaydet
          </button>
        </div>
      </div>
    </div>
  );
};

interface FloatingMiniHudProps {
  showBubble: boolean;
  showWindow: boolean;
  visualState: JarvisVisualState;
  mood: JarvisMood;
  rmsLevel: number;
  lastReply: string;
  isProcessing: boolean;
  onOpenWindow: () => void;
  onMinimizeWindow: () => void;
  onCloseAll: () => void;
  onSendCommand: (cmd: string) => void;
  onVoiceClick: () => void;
  onOpenCrosshair: () => void;
}

export const FloatingMiniHud: React.FC<FloatingMiniHudProps> = ({
  showBubble,
  showWindow,
  visualState,
  mood,
  rmsLevel,
  lastReply,
  isProcessing,
  onOpenWindow,
  onMinimizeWindow,
  onCloseAll,
  onSendCommand,
  onVoiceClick,
  onOpenCrosshair,
}) => {
  const [miniInput, setMiniInput] = useState('');

  if (!showBubble && !showWindow) return null;

  return (
    <>
      {showBubble && !showWindow && (
        <div className="fixed right-5 bottom-24 sm:bottom-6 z-40 flex items-center gap-1.5">
          <button
            type="button"
            onClick={onOpenWindow}
            title="JARVIS Kayan Pencereyi Aç"
            className="w-14 h-14 rounded-full bg-[var(--jarvis-surface)] border border-[var(--jarvis-primary)]/60 shadow-lg overflow-hidden cursor-pointer flex items-center justify-center"
          >
            <ParticleNebulaCanvas
              visualState={visualState}
              mood={mood}
              rmsLevel={rmsLevel}
              compactMode
            />
          </button>
          <button
            type="button"
            onClick={onCloseAll}
            aria-label="Kayan baloncuğu kapat"
            className="w-6 h-6 rounded-full bg-[var(--jarvis-surface-variant)] border border-[var(--jarvis-outline)] text-[var(--jarvis-on-surface-muted)] hover:text-[var(--jarvis-on-surface)] flex items-center justify-center cursor-pointer"
          >
            <X className="w-3.5 h-3.5" />
          </button>
        </div>
      )}

      {showWindow && (
        <div className="fixed right-4 bottom-24 sm:bottom-6 z-40 w-[330px] max-w-[calc(100vw-2rem)] rounded-[20px] bg-[var(--jarvis-surface)] border border-[var(--jarvis-outline)] shadow-2xl p-3.5 flex flex-col gap-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <div className="w-7 h-7 rounded-full overflow-hidden bg-[var(--jarvis-bg)]">
                <ParticleNebulaCanvas
                  visualState={visualState}
                  mood={mood}
                  rmsLevel={rmsLevel}
                  compactMode
                />
              </div>
              <span className="font-display font-semibold text-sm text-[var(--jarvis-on-surface)]">
                JARVIS Hızlı Pencere
              </span>
            </div>
            <div className="flex items-center gap-1">
              <button
                type="button"
                onClick={onOpenCrosshair}
                title="Nokta Kaydet"
                className="p-1.5 rounded-lg text-[var(--jarvis-primary)] hover:bg-[var(--jarvis-surface-variant)] cursor-pointer"
              >
                <Plus className="w-4 h-4" />
              </button>
              <button
                type="button"
                onClick={onMinimizeWindow}
                title="Küçült"
                className="p-1.5 rounded-lg text-[var(--jarvis-on-surface-muted)] hover:bg-[var(--jarvis-surface-variant)] cursor-pointer"
              >
                <Minimize2 className="w-4 h-4" />
              </button>
              <button
                type="button"
                onClick={onCloseAll}
                title="Kapat"
                className="p-1.5 rounded-lg text-[var(--jarvis-on-surface-muted)] hover:bg-[var(--jarvis-surface-variant)] cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
          </div>

          <div className="rounded-xl bg-[var(--jarvis-bg)] border border-[var(--jarvis-outline-variant)] p-2.5 text-xs text-[var(--jarvis-on-surface)] max-h-24 overflow-y-auto">
            {isProcessing ? 'İşleniyor…' : lastReply || 'Hızlı komut vermeye hazır.'}
          </div>

          <form
            onSubmit={(e) => {
              e.preventDefault();
              if (miniInput.trim() && !isProcessing) {
                onSendCommand(miniInput.trim());
                setMiniInput('');
              }
            }}
            className="flex items-center gap-1.5"
          >
            <input
              type="text"
              value={miniInput}
              onChange={(e) => setMiniInput(e.target.value)}
              placeholder="Hızlı komut…"
              className="flex-1 rounded-xl bg-[var(--jarvis-bg)] border border-[var(--jarvis-outline-variant)] focus:border-[var(--jarvis-primary)] focus:outline-none px-3 py-2 text-xs text-[var(--jarvis-on-surface)]"
            />
            <button
              type="button"
              onClick={onVoiceClick}
              className="p-2 rounded-xl bg-[var(--jarvis-surface-variant)] text-[var(--jarvis-primary)] cursor-pointer"
              aria-label="Sesli komut"
            >
              <Mic className="w-4 h-4" />
            </button>
            <button
              type="submit"
              disabled={!miniInput.trim() || isProcessing}
              className="p-2 rounded-xl bg-[var(--jarvis-primary)] text-[var(--jarvis-on-primary)] disabled:opacity-50 cursor-pointer"
              aria-label="Gönder"
            >
              <Send className="w-4 h-4" />
            </button>
          </form>
        </div>
      )}
    </>
  );
};
