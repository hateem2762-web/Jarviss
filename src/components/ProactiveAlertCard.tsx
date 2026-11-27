import React, { useEffect, useState } from 'react';
import { BellRing, Copy, ExternalLink, Send, X } from 'lucide-react';
import type { ProactiveAlertItem } from '../types/jarvis';

interface ProactiveAlertCardProps {
  alert: ProactiveAlertItem;
  onDraftChange: (edited: string) => void;
  onCopyDraft: (draft: string) => void;
  onOpenWhatsApp: (draft: string) => void;
  onSendDirectReply: (draft: string) => void;
  onDismiss: () => void;
}

export const ProactiveAlertCard: React.FC<ProactiveAlertCardProps> = ({
  alert,
  onDraftChange,
  onCopyDraft,
  onOpenWhatsApp,
  onSendDirectReply,
  onDismiss,
}) => {
  const [draftText, setDraftText] = useState(alert.draftReply);

  useEffect(() => {
    setDraftText(alert.draftReply);
  }, [alert.id, alert.draftReply]);

  const scoreColor =
    alert.importanceScore >= 9
      ? '#FB7185'
      : alert.importanceScore >= 7
        ? '#FBBF24'
        : '#34D399';

  const sourceLabel =
    alert.sourcePackage === 'com.whatsapp.w4b' ? 'WA Business' : 'WhatsApp';

  return (
    <div
      className="w-full rounded-[18px] bg-[var(--jarvis-surface)] p-3.5 flex flex-col gap-2"
      style={{ border: `1px solid ${scoreColor}80` }}
    >
      <div className="flex items-center gap-2">
        <BellRing className="w-[18px] h-[18px] shrink-0" style={{ color: scoreColor }} />
        <div className="flex-1 min-w-0">
          <p className="font-display font-semibold text-sm text-[var(--jarvis-on-surface)] truncate">
            {alert.senderName}
          </p>
          <p className="font-mono-hud text-[11px]" style={{ color: scoreColor }}>
            {sourceLabel} • Önem: {alert.importanceScore}/10
          </p>
        </div>
        <button
          type="button"
          onClick={onDismiss}
          aria-label="Proaktif uyarıyı kapat"
          className="p-2 rounded-lg text-[var(--jarvis-on-surface-muted)] hover:text-[var(--jarvis-on-surface)] hover:bg-[var(--jarvis-surface-variant)] cursor-pointer"
        >
          <X className="w-4 h-4" />
        </button>
      </div>

      <p className="text-sm text-[var(--jarvis-on-surface)]">{alert.summary}</p>
      <p className="text-xs text-[var(--jarvis-on-surface-muted)]">
        Neden önemli: {alert.importanceReason}
      </p>

      <div>
        <label className="block text-[11px] font-mono-hud text-[var(--jarvis-on-surface-muted)] mb-1">
          Hazır Cevap Taslağı (Düzenlenebilir)
        </label>
        <textarea
          value={draftText}
          onChange={(e) => {
            setDraftText(e.target.value);
            onDraftChange(e.target.value);
          }}
          rows={2}
          className="w-full rounded-xl bg-[var(--jarvis-bg)] border border-[var(--jarvis-outline-variant)] focus:border-[var(--jarvis-primary)] focus:outline-none px-3 py-2 text-sm text-[var(--jarvis-on-surface)] resize-y"
        />
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <button
          type="button"
          onClick={() => onCopyDraft(draftText)}
          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl border border-[var(--jarvis-outline)] text-xs font-mono-hud text-[var(--jarvis-on-surface)] hover:bg-[var(--jarvis-surface-variant)] cursor-pointer"
        >
          <Copy className="w-3.5 h-3.5" />
          <span>Kopyala</span>
        </button>

        <button
          type="button"
          disabled={!draftText.trim()}
          onClick={() => onSendDirectReply(draftText)}
          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-[#34D399] text-[#090B0F] text-xs font-mono-hud font-medium disabled:opacity-50 cursor-pointer"
        >
          <Send className="w-3.5 h-3.5" />
          <span>Doğrudan Gönder</span>
        </button>

        <button
          type="button"
          onClick={() => onOpenWhatsApp(draftText)}
          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-[var(--jarvis-surface-variant)] text-[var(--jarvis-on-surface)] text-xs font-mono-hud hover:opacity-90 cursor-pointer"
        >
          <ExternalLink className="w-3.5 h-3.5" />
          <span>WhatsApp&apos;ta Aç</span>
        </button>
      </div>
    </div>
  );
};
