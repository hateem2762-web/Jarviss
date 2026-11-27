import React, { useMemo, useState } from 'react';
import {
  AppWindow,
  CheckCircle2,
  Crosshair,
  MousePointerClick,
  Trash2,
  X,
} from 'lucide-react';
import {
  GENERAL_PROFILE_NAME,
  GENERAL_PROFILE_PACKAGE,
  type InstalledAppInfo,
  type JarvisPreferences,
  type TapCoordinateItem,
} from '../types/jarvis';

interface CoordinatesScreenProps {
  coordinates: TapCoordinateItem[];
  installedApps: InstalledAppInfo[];
  preferences: JarvisPreferences;
  onSelectManualProfile: (packageName: string, displayName: string) => void;
  onStartCrosshair: () => void;
  onTestTapCoordinate: (coord: TapCoordinateItem) => void;
  onDeleteCoordinate: (id: number) => void;
}

export const CoordinatesScreen: React.FC<CoordinatesScreenProps> = ({
  coordinates,
  installedApps,
  preferences,
  onSelectManualProfile,
  onStartCrosshair,
  onTestTapCoordinate,
  onDeleteCoordinate,
}) => {
  const [showProfilePicker, setShowProfilePicker] = useState(false);

  const activeProfileName =
    preferences.manualProfileName.trim() || GENERAL_PROFILE_NAME;
  const activeProfilePkg =
    preferences.manualProfilePackageName.trim() || GENERAL_PROFILE_PACKAGE;

  const groupedCoordinates = useMemo(() => {
    const groups = new Map<string, TapCoordinateItem[]>();
    for (const item of coordinates) {
      const existing = groups.get(item.profilePackageName) ?? [];
      existing.push(item);
      groups.set(item.profilePackageName, existing);
    }
    return Array.from(groups.entries());
  }, [coordinates]);

  return (
    <div className="flex flex-col h-full w-full bg-[var(--jarvis-bg)] items-center overflow-hidden">
      {/* Header */}
      <div className="w-full bg-[var(--jarvis-surface)] shrink-0">
        <div className="w-full max-w-[720px] mx-auto px-5 py-3.5 flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <Crosshair className="w-5 h-5 text-[var(--jarvis-primary)]" />
            <h1 className="font-display font-semibold text-lg text-[var(--jarvis-on-surface)]">
              Noktalar
            </h1>
          </div>
          <span className="px-2.5 py-1 rounded-[10px] bg-[var(--jarvis-primary-container)] font-mono-hud text-xs text-[var(--jarvis-on-primary-container)]">
            {coordinates.length} kayıt
          </span>
        </div>
        <div className="h-px w-full bg-[var(--jarvis-outline-variant)]" />
      </div>

      <div className="flex-1 w-full max-w-[720px] overflow-y-auto p-4 flex flex-col gap-3">
        {/* Automation Status Card */}
        <div
          className="rounded-[18px] bg-[var(--jarvis-surface)] p-4 flex flex-col gap-2.5"
          style={{ border: '1px solid rgba(52, 211, 153, 0.55)' }}
        >
          <div className="flex items-center gap-2.5">
            <div className="w-9.5 h-9.5 rounded-full bg-[#34D399]/15 flex items-center justify-center shrink-0">
              <CheckCircle2 className="w-5 h-5 text-[#34D399]" />
            </div>
            <div className="flex-1 min-w-0">
              <h2 className="font-display font-medium text-base text-[var(--jarvis-on-surface)]">
                Dokunma Asistanı Aktif (Web Simülasyon)
              </h2>
              <p className="text-xs text-[var(--jarvis-on-surface-muted)]">
                Kayıtlı koordinat etiketleriyle 0-token hızlı eşleştirme ve test dokunması hazır.
              </p>
            </div>
          </div>
        </div>

        {/* Active Profile & Crosshair Card */}
        <div className="rounded-[18px] bg-[var(--jarvis-surface)] border border-[var(--jarvis-outline-variant)] p-4 flex flex-col gap-3">
          <div className="flex items-center justify-between gap-3">
            <div className="min-w-0 flex-1">
              <h2 className="font-display font-medium text-base text-[var(--jarvis-on-surface)]">
                Aktif Oyun / Uygulama Profili
              </h2>
              <p className="text-xs text-[#38BDF8] truncate mt-0.5">
                Manuel profil: {activeProfileName}
              </p>
              <p className="font-mono-hud text-[11px] text-[var(--jarvis-on-surface-muted)] truncate">
                {activeProfilePkg}
              </p>
            </div>

            <button
              type="button"
              onClick={() => setShowProfilePicker(true)}
              className="inline-flex items-center gap-1.5 px-3 py-2 rounded-xl bg-[var(--jarvis-surface-variant)] text-xs font-mono-hud text-[var(--jarvis-on-surface)] hover:opacity-90 cursor-pointer whitespace-nowrap shrink-0"
            >
              <AppWindow className="w-4 h-4" />
              <span>Profil Seç</span>
            </button>
          </div>

          <button
            type="button"
            onClick={onStartCrosshair}
            className="w-full py-2.5 px-4 rounded-[14px] bg-[var(--jarvis-primary)] text-[var(--jarvis-on-primary)] font-display font-medium text-sm flex items-center justify-center gap-2 hover:opacity-90 cursor-pointer"
          >
            <Crosshair className="w-4 h-4" />
            <span>Ekranda Nişangah ile Nokta Kaydet</span>
          </button>

          <p className="text-xs text-[var(--jarvis-on-surface-muted)]">
            İpucu: Kayıtlı etiketler (&quot;Transfer&apos;e tıkla&quot; gibi komutlar) sıfır API token harcayarak anında eşleşir.
          </p>
        </div>

        {coordinates.length === 0 ? (
          <div className="rounded-[18px] bg-[var(--jarvis-surface-variant)]/45 border border-[var(--jarvis-outline-variant)] p-6 flex flex-col items-center text-center gap-2">
            <MousePointerClick className="w-8.5 h-8.5 text-[var(--jarvis-primary)]" />
            <h3 className="font-display font-medium text-base text-[var(--jarvis-on-surface)]">
              Henüz kayıtlı ekran noktası yok
            </h3>
            <p className="text-xs text-[var(--jarvis-on-surface-muted)] max-w-md">
              Yukarıdaki butona dokunup nişangahı istediğin noktanın üstüne yerleştirerek ilk noktanı kaydedebilirsin.
            </p>
          </div>
        ) : (
          groupedCoordinates.map(([pkgName, itemsInProfile]) => {
            const groupTitle = itemsInProfile[0]?.profileName || pkgName;
            return (
              <div key={pkgName} className="flex flex-col gap-2">
                <div className="flex items-center justify-between px-1 pt-1.5">
                  <div className="min-w-0 flex-1">
                    <h3 className="font-display font-semibold text-sm text-[var(--jarvis-on-surface)] truncate">
                      {groupTitle}
                    </h3>
                    <p className="font-mono-hud text-[11px] text-[var(--jarvis-on-surface-muted)] truncate">
                      {pkgName}
                    </p>
                  </div>
                  <span className="font-mono-hud text-[11px] text-[#38BDF8] shrink-0">
                    {itemsInProfile.length} nokta
                  </span>
                </div>

                {itemsInProfile.map((coord) => {
                  const xPct = (coord.xPercent * 100).toFixed(1);
                  const yPct = (coord.yPercent * 100).toFixed(1);
                  return (
                    <div
                      key={coord.id}
                      className="rounded-2xl bg-[var(--jarvis-surface)] border border-[var(--jarvis-outline-variant)] px-3.5 py-2.5 flex items-center gap-2.5"
                    >
                      <div className="w-10 h-10 rounded-full bg-[#38BDF8]/15 flex items-center justify-center shrink-0">
                        <MousePointerClick className="w-5 h-5 text-[#38BDF8]" />
                      </div>

                      <div className="flex-1 min-w-0">
                        <p className="font-display font-medium text-sm text-[var(--jarvis-on-surface)] truncate">
                          {coord.label}
                        </p>
                        <p className="font-mono-hud text-[11px] text-[var(--jarvis-on-surface-muted)] truncate">
                          Oran: ({xPct}%, {yPct}%) • {coord.xPx}×{coord.yPx} px
                        </p>
                      </div>

                      <button
                        type="button"
                        onClick={() => onTestTapCoordinate(coord)}
                        className="px-2.5 py-1.5 rounded-[10px] border border-[var(--jarvis-outline)] font-mono-hud text-xs text-[var(--jarvis-on-surface)] hover:bg-[var(--jarvis-surface-variant)] cursor-pointer"
                      >
                        Dokun
                      </button>

                      <button
                        type="button"
                        onClick={() => onDeleteCoordinate(coord.id)}
                        aria-label="Kayıtlı noktayı sil"
                        className="p-2 rounded-lg text-[#FB7185] hover:bg-[#FB7185]/10 cursor-pointer"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  );
                })}
              </div>
            );
          })
        )}
      </div>

      {showProfilePicker && (
        <ProfilePickerDialog
          selectedPackageName={activeProfilePkg}
          installedApps={installedApps}
          onSelectProfile={(pkg, label) => {
            onSelectManualProfile(pkg, label);
            setShowProfilePicker(false);
          }}
          onDismiss={() => setShowProfilePicker(false)}
        />
      )}
    </div>
  );
};

const ProfilePickerDialog: React.FC<{
  selectedPackageName: string;
  installedApps: InstalledAppInfo[];
  onSelectProfile: (pkg: string, label: string) => void;
  onDismiss: () => void;
}> = ({ selectedPackageName, installedApps, onSelectProfile, onDismiss }) => {
  const [query, setQuery] = useState('');

  const filteredApps = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return installedApps;
    return installedApps.filter(
      (a) =>
        a.label.toLowerCase().includes(q) || a.packageName.toLowerCase().includes(q)
    );
  }, [installedApps, query]);

  return (
    <div className="fixed inset-0 z-50 bg-black/70 backdrop-blur-sm flex items-center justify-center p-4">
      <div className="w-full max-w-md rounded-[20px] bg-[var(--jarvis-surface)] border border-[var(--jarvis-outline)] p-5 flex flex-col gap-3">
        <div className="flex items-center justify-between">
          <h2 className="font-display font-semibold text-base text-[var(--jarvis-on-surface)]">
            Oyun / Uygulama Profili Seç
          </h2>
          <button
            type="button"
            onClick={onDismiss}
            className="p-1.5 rounded-lg text-[var(--jarvis-on-surface-muted)] hover:bg-[var(--jarvis-surface-variant)] cursor-pointer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <input
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Uygulama veya oyun ara…"
          className="w-full rounded-xl bg-[var(--jarvis-bg)] border border-[var(--jarvis-outline-variant)] focus:border-[var(--jarvis-primary)] focus:outline-none px-3 py-2 text-sm text-[var(--jarvis-on-surface)]"
        />

        <div className="max-h-72 overflow-y-auto flex flex-col gap-1.5">
          <button
            type="button"
            onClick={() => onSelectProfile(GENERAL_PROFILE_PACKAGE, GENERAL_PROFILE_NAME)}
            className={`w-full text-left px-3 py-2 rounded-xl border transition-colors cursor-pointer ${
              selectedPackageName === GENERAL_PROFILE_PACKAGE
                ? 'bg-[var(--jarvis-primary-container)] border-[var(--jarvis-primary)]'
                : 'bg-[var(--jarvis-surface-variant)]/55 border-[var(--jarvis-outline-variant)]'
            }`}
          >
            <p className="font-display font-medium text-sm text-[var(--jarvis-on-surface)]">
              {GENERAL_PROFILE_NAME}
            </p>
            <p className="font-mono-hud text-[11px] text-[var(--jarvis-on-surface-muted)]">
              Tüm ekranlarda geçerli varsayılan profil
            </p>
          </button>

          {filteredApps.map((app) => (
            <button
              key={app.packageName}
              type="button"
              onClick={() => onSelectProfile(app.packageName, app.label)}
              className={`w-full text-left px-3 py-2 rounded-xl border transition-colors cursor-pointer ${
                selectedPackageName === app.packageName
                  ? 'bg-[var(--jarvis-primary-container)] border-[var(--jarvis-primary)]'
                  : 'bg-[var(--jarvis-surface-variant)]/55 border-[var(--jarvis-outline-variant)]'
              }`}
            >
              <p className="font-display font-medium text-sm text-[var(--jarvis-on-surface)] truncate">
                {app.label}
              </p>
              <p className="font-mono-hud text-[11px] text-[var(--jarvis-on-surface-muted)] truncate">
                {app.packageName}
              </p>
            </button>
          ))}
        </div>
      </div>
    </div>
  );
};
