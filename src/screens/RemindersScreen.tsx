import React from 'react';
import { AlarmClock, BellRing, Clock, Trash2 } from 'lucide-react';
import type { ReminderItem } from '../types/jarvis';

interface RemindersScreenProps {
  reminders: ReminderItem[];
  notificationPermission: NotificationPermission | 'unsupported';
  onRequestNotificationPermission: () => void;
  onToggleComplete: (reminder: ReminderItem) => void;
  onDeleteReminder: (id: number) => void;
}

export const RemindersScreen: React.FC<RemindersScreenProps> = ({
  reminders,
  notificationPermission,
  onRequestNotificationPermission,
  onToggleComplete,
  onDeleteReminder,
}) => {
  const activeCount = reminders.filter((r) => !r.isCompleted).length;

  const formatTriggerTime = (ms: number) => {
    const d = new Date(ms);
    const time = new Intl.DateTimeFormat('tr-TR', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    }).format(d);
    const date = new Intl.DateTimeFormat('tr-TR', {
      day: '2-digit',
      month: 'short',
    }).format(d);
    return `${time} · ${date}`;
  };

  return (
    <div className="flex flex-col h-full w-full bg-[var(--jarvis-bg)] items-center overflow-hidden">
      {/* Header */}
      <div className="w-full shrink-0">
        <div className="w-full max-w-[720px] mx-auto px-5 py-3.5 flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <BellRing className="w-5 h-5 text-[var(--jarvis-primary)]" />
            <h1 className="font-display font-semibold text-lg text-[var(--jarvis-on-surface)]">
              Hatırlatıcılar
            </h1>
          </div>
          <span className="px-2.5 py-1 rounded-lg bg-[var(--jarvis-surface-variant)] font-mono-hud text-xs text-[var(--jarvis-on-surface-muted)]">
            {activeCount} aktif
          </span>
        </div>
        <div className="h-px w-full bg-[var(--jarvis-outline-variant)]" />
      </div>

      {/* Browser Notification Permission Banner */}
      {notificationPermission === 'default' && (
        <div className="w-full max-w-[720px] px-4 pt-2.5 shrink-0">
          <div
            className="rounded-[14px] bg-[var(--jarvis-surface)] p-3.5 flex items-center gap-3"
            style={{ border: '1px solid rgba(251, 191, 36, 0.5)' }}
          >
            <Clock className="w-5 h-5 text-[#FBBF24] shrink-0" />
            <div className="flex-1 min-w-0">
              <p className="font-display font-medium text-sm text-[var(--jarvis-on-surface)]">
                Tarayıcı Bildirim İzni
              </p>
              <p className="text-xs text-[var(--jarvis-on-surface-muted)]">
                Zamanı gelen hatırlatıcılarda masaüstü/tarayıcı bildirimi almak için izin verin.
              </p>
            </div>
            <button
              type="button"
              onClick={onRequestNotificationPermission}
              className="px-3 py-1.5 rounded-lg border border-[var(--jarvis-outline)] font-mono-hud text-xs text-[var(--jarvis-on-surface)] hover:bg-[var(--jarvis-surface-variant)] cursor-pointer whitespace-nowrap"
            >
              Aç
            </button>
          </div>
        </div>
      )}

      {reminders.length === 0 ? (
        <div className="flex-1 w-full max-w-[480px] p-8 flex flex-col items-center justify-center text-center">
          <AlarmClock className="w-13 h-13 text-[var(--jarvis-primary)]/85 mb-3.5" />
          <h2 className="font-display font-semibold text-lg text-[var(--jarvis-on-surface)]">
            Aktif hatırlatıcı yok
          </h2>
          <p className="mt-2 text-sm text-[var(--jarvis-on-surface-muted)]">
            &quot;5 dakika sonra su içmeyi hatırlat&quot; veya &quot;saat 16:30&apos;da toplantıyı hatırlat&quot; diyerek ekleyebilirsiniz.
          </p>
        </div>
      ) : (
        <div className="flex-1 w-full max-w-[720px] overflow-y-auto p-4 flex flex-col gap-2.5">
          {reminders.map((item) => {
            const meta = item.durationLabel
              ? `${item.durationLabel}  •  ${formatTriggerTime(item.triggerAtMillis)}`
              : formatTriggerTime(item.triggerAtMillis);

            return (
              <div
                key={item.id}
                className="w-full rounded-2xl bg-[var(--jarvis-surface)] border border-[var(--jarvis-outline-variant)] px-3 py-2.5 flex items-center gap-2"
              >
                <input
                  type="checkbox"
                  checked={item.isCompleted}
                  onChange={() => onToggleComplete(item)}
                  aria-label="Hatırlatıcıyı tamamla"
                  className="w-4.5 h-4.5 accent-[var(--jarvis-primary)] cursor-pointer ml-1 shrink-0"
                />

                <div className="flex-1 min-w-0 px-1.5">
                  <p
                    className={`text-sm ${
                      item.isCompleted
                        ? 'line-through text-[var(--jarvis-on-surface-muted)]'
                        : 'text-[var(--jarvis-on-surface)]'
                    }`}
                  >
                    {item.message}
                  </p>
                  <p className="font-mono-hud text-xs text-[var(--jarvis-primary)] mt-0.5">
                    {meta}
                  </p>
                </div>

                <button
                  type="button"
                  onClick={() => onDeleteReminder(item.id)}
                  aria-label="Hatırlatıcıyı sil"
                  className="p-2 rounded-lg text-[var(--jarvis-on-surface-muted)] hover:text-[#FB7185] hover:bg-[var(--jarvis-surface-variant)] cursor-pointer shrink-0"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};
