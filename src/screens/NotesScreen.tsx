import React, { useMemo, useState } from 'react';
import { Copy, Image as ImageIcon, NotebookPen, Plus, Search, Trash2, X } from 'lucide-react';
import type { NoteItem } from '../types/jarvis';

interface NotesScreenProps {
  notes: NoteItem[];
  onAddNote: (title: StringLike, content: StringLike) => void;
  onUpdateNote: (id: number, title: string, content: string) => void;
  onDeleteNote: (id: number) => void;
  onCopyNote: (text: string) => void;
}

type StringLike = string;

export const NotesScreen: React.FC<NotesScreenProps> = ({
  notes,
  onAddNote,
  onUpdateNote,
  onDeleteNote,
  onCopyNote,
}) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedNote, setSelectedNote] = useState<NoteItem | null>(null);
  const [showCreateDialog, setShowCreateDialog] = useState(false);

  const filteredNotes = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    if (!q) return notes;
    return notes.filter(
      (n) =>
        n.title.toLowerCase().includes(q) ||
        n.content.toLowerCase().includes(q) ||
        n.sourceAppName.toLowerCase().includes(q)
    );
  }, [notes, searchQuery]);

  const formatNoteDate = (ms: number) =>
    new Intl.DateTimeFormat('tr-TR', {
      day: '2-digit',
      month: 'short',
      hour: '2-digit',
      minute: '2-digit',
    }).format(new Date(ms));

  return (
    <div className="flex flex-col h-full w-full bg-[var(--jarvis-bg)] items-center overflow-hidden">
      {/* Header */}
      <div className="w-full shrink-0">
        <div className="w-full max-w-[720px] mx-auto px-5 py-3.5 flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <NotebookPen className="w-5 h-5 text-[var(--jarvis-primary)]" />
            <h1 className="font-display font-semibold text-lg text-[var(--jarvis-on-surface)]">
              Not Defteri
            </h1>
            {notes.length > 0 && (
              <span className="px-2 py-0.5 rounded-lg bg-[var(--jarvis-surface-variant)] font-mono-hud text-xs text-[var(--jarvis-on-surface-muted)]">
                {notes.length}
              </span>
            )}
          </div>

          <button
            type="button"
            onClick={() => setShowCreateDialog(true)}
            className="inline-flex items-center gap-1.5 px-3 py-2 rounded-xl bg-[var(--jarvis-surface-variant)] text-xs font-mono-hud text-[var(--jarvis-on-surface)] hover:opacity-90 cursor-pointer whitespace-nowrap"
          >
            <Plus className="w-4 h-4" />
            <span>Yeni Not</span>
          </button>
        </div>
        <div className="h-px w-full bg-[var(--jarvis-outline-variant)]" />
      </div>

      {notes.length === 0 ? (
        <div className="flex-1 w-full max-w-[480px] p-8 flex flex-col items-center justify-center text-center">
          <NotebookPen className="w-13 h-13 text-[var(--jarvis-primary)]/85 mb-3.5" />
          <h2 className="font-display font-semibold text-lg text-[var(--jarvis-on-surface)]">
            Not defteri boş
          </h2>
          <p className="mt-2 text-sm text-[var(--jarvis-on-surface-muted)]">
            Herhangi bir ekrandayken &quot;Jarvis, bu ekranı not al&quot; deyin veya sağ üstten yeni not ekleyin.
          </p>
        </div>
      ) : (
        <div className="flex-1 w-full max-w-[720px] flex flex-col min-h-0">
          {notes.length > 2 && (
            <div className="px-4 pt-3 pb-1 shrink-0">
              <div className="relative flex items-center">
                <Search className="w-4 h-4 text-[var(--jarvis-on-surface-muted)] absolute left-3.5 pointer-events-none" />
                <input
                  type="text"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  placeholder="Notlarda ara…"
                  className="w-full rounded-[14px] bg-[var(--jarvis-surface)] border border-[var(--jarvis-outline-variant)] focus:border-[var(--jarvis-primary)] focus:outline-none pl-10 pr-4 py-2.5 text-sm text-[var(--jarvis-on-surface)]"
                />
              </div>
            </div>
          )}

          <div className="flex-1 overflow-y-auto p-4 flex flex-col gap-2.5">
            {filteredNotes.map((note) => (
              <div
                key={note.id}
                onClick={() => setSelectedNote(note)}
                className="w-full rounded-2xl bg-[var(--jarvis-surface)] border border-[var(--jarvis-outline-variant)] hover:border-[var(--jarvis-outline)] p-3.5 flex flex-col gap-2 cursor-pointer transition-colors"
              >
                <div className="flex items-center justify-between gap-2">
                  <h3 className="font-display font-medium text-base text-[var(--jarvis-on-surface)] truncate flex-1">
                    {note.title}
                  </h3>
                  {note.screenshotDataUrl && (
                    <ImageIcon className="w-4 h-4 text-[#38BDF8] shrink-0" />
                  )}
                </div>

                {note.content && (
                  <p className="text-sm text-[var(--jarvis-on-surface-muted)] line-clamp-3 whitespace-pre-wrap">
                    {note.content}
                  </p>
                )}

                <div className="flex items-center justify-between gap-2 pt-1">
                  <div className="flex items-center gap-2 min-w-0">
                    <span className="px-2 py-0.5 rounded-lg bg-[var(--jarvis-surface-variant)] font-mono-hud text-[11px] text-[var(--jarvis-primary)] truncate">
                      {note.sourceAppName || 'Not'}
                    </span>
                    <span className="font-mono-hud text-[11px] text-[var(--jarvis-on-surface-muted)] whitespace-nowrap">
                      {formatNoteDate(note.updatedAtMillis)}
                    </span>
                  </div>

                  <div className="flex items-center gap-1">
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        onCopyNote(`${note.title}\n\n${note.content}`.trim());
                      }}
                      aria-label="Notu panoya kopyala"
                      className="p-2 rounded-lg text-[var(--jarvis-on-surface-muted)] hover:text-[var(--jarvis-on-surface)] hover:bg-[var(--jarvis-surface-variant)] cursor-pointer"
                    >
                      <Copy className="w-4 h-4" />
                    </button>
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        onDeleteNote(note.id);
                      }}
                      aria-label="Notu sil"
                      className="p-2 rounded-lg text-[#FB7185] hover:bg-[#FB7185]/10 cursor-pointer"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {selectedNote && (
        <NoteDetailEditDialog
          note={selectedNote}
          onSave={(newTitle, newContent) => {
            onUpdateNote(selectedNote.id, newTitle, newContent);
            setSelectedNote(null);
          }}
          onCopy={onCopyNote}
          onDelete={() => {
            onDeleteNote(selectedNote.id);
            setSelectedNote(null);
          }}
          onDismiss={() => setSelectedNote(null)}
        />
      )}

      {showCreateDialog && (
        <CreateNoteDialog
          onSave={(title, content) => {
            onAddNote(title, content);
            setShowCreateDialog(false);
          }}
          onDismiss={() => setShowCreateDialog(false)}
        />
      )}
    </div>
  );
};

const NoteDetailEditDialog: React.FC<{
  note: NoteItem;
  onSave: (title: string, content: string) => void;
  onCopy: (text: string) => void;
  onDelete: () => void;
  onDismiss: () => void;
}> = ({ note, onSave, onCopy, onDelete, onDismiss }) => {
  const [titleDraft, setTitleDraft] = useState(note.title);
  const [contentDraft, setContentDraft] = useState(note.content);

  return (
    <div className="fixed inset-0 z-50 bg-black/70 backdrop-blur-sm flex items-center justify-center p-4">
      <div className="w-full max-w-lg rounded-[20px] bg-[var(--jarvis-surface)] border border-[var(--jarvis-outline)] p-5 flex flex-col gap-3 max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between">
          <h2 className="font-display font-semibold text-base text-[var(--jarvis-on-surface)]">
            Notu Görüntüle / Düzenle
          </h2>
          <button
            type="button"
            onClick={onDismiss}
            className="p-1.5 rounded-lg text-[var(--jarvis-on-surface-muted)] hover:bg-[var(--jarvis-surface-variant)] cursor-pointer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <p className="font-mono-hud text-xs text-[var(--jarvis-primary)]">
          Kaynak: {note.sourceAppName}
        </p>

        {note.screenshotDataUrl && (
          <img
            src={note.screenshotDataUrl}
            alt="Kaydedilen ekran görüntüsü"
            className="w-full max-h-44 object-contain rounded-xl bg-[var(--jarvis-surface-variant)]"
          />
        )}

        <div>
          <label className="block text-xs text-[var(--jarvis-on-surface-muted)] mb-1">
            Başlık
          </label>
          <input
            type="text"
            value={titleDraft}
            onChange={(e) => setTitleDraft(e.target.value)}
            className="w-full rounded-xl bg-[var(--jarvis-bg)] border border-[var(--jarvis-outline-variant)] focus:border-[var(--jarvis-primary)] focus:outline-none px-3 py-2 text-sm text-[var(--jarvis-on-surface)]"
          />
        </div>

        <div>
          <label className="block text-xs text-[var(--jarvis-on-surface-muted)] mb-1">
            İçerik
          </label>
          <textarea
            value={contentDraft}
            onChange={(e) => setContentDraft(e.target.value)}
            rows={6}
            className="w-full rounded-xl bg-[var(--jarvis-bg)] border border-[var(--jarvis-outline-variant)] focus:border-[var(--jarvis-primary)] focus:outline-none px-3 py-2 text-sm text-[var(--jarvis-on-surface)] resize-y"
          />
        </div>

        <div className="flex flex-wrap items-center justify-between gap-2 pt-2">
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => onCopy(`${titleDraft.trim()}\n\n${contentDraft.trim()}`.trim())}
              className="px-3 py-1.5 rounded-lg text-xs font-medium text-[var(--jarvis-primary)] hover:bg-[var(--jarvis-surface-variant)] cursor-pointer"
            >
              Kopyala
            </button>
            <button
              type="button"
              onClick={onDelete}
              className="px-3 py-1.5 rounded-lg text-xs font-medium text-[#FB7185] hover:bg-[#FB7185]/10 cursor-pointer"
            >
              Sil
            </button>
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={onDismiss}
              className="px-3 py-1.5 rounded-lg text-xs font-medium text-[var(--jarvis-on-surface-muted)] hover:bg-[var(--jarvis-surface-variant)] cursor-pointer"
            >
              Kapat
            </button>
            <button
              type="button"
              disabled={!titleDraft.trim() && !contentDraft.trim()}
              onClick={() => onSave(titleDraft, contentDraft)}
              className="px-4 py-2 rounded-xl bg-[var(--jarvis-primary)] text-[var(--jarvis-on-primary)] text-xs font-medium disabled:opacity-50 cursor-pointer"
            >
              Kaydet
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

const CreateNoteDialog: React.FC<{
  onSave: (title: string, content: string) => void;
  onDismiss: () => void;
}> = ({ onSave, onDismiss }) => {
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');

  return (
    <div className="fixed inset-0 z-50 bg-black/70 backdrop-blur-sm flex items-center justify-center p-4">
      <div className="w-full max-w-md rounded-[20px] bg-[var(--jarvis-surface)] border border-[var(--jarvis-outline)] p-5 flex flex-col gap-3">
        <h2 className="font-display font-semibold text-base text-[var(--jarvis-on-surface)]">
          Yeni Not
        </h2>

        <div>
          <label className="block text-xs text-[var(--jarvis-on-surface-muted)] mb-1">
            Başlık
          </label>
          <input
            type="text"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            className="w-full rounded-xl bg-[var(--jarvis-bg)] border border-[var(--jarvis-outline-variant)] focus:border-[var(--jarvis-primary)] focus:outline-none px-3 py-2 text-sm text-[var(--jarvis-on-surface)]"
          />
        </div>

        <div>
          <label className="block text-xs text-[var(--jarvis-on-surface-muted)] mb-1">
            Not metni
          </label>
          <textarea
            value={content}
            onChange={(e) => setContent(e.target.value)}
            rows={5}
            className="w-full rounded-xl bg-[var(--jarvis-bg)] border border-[var(--jarvis-outline-variant)] focus:border-[var(--jarvis-primary)] focus:outline-none px-3 py-2 text-sm text-[var(--jarvis-on-surface)] resize-y"
          />
        </div>

        <div className="flex justify-end gap-2 pt-2">
          <button
            type="button"
            onClick={onDismiss}
            className="px-3 py-1.5 rounded-lg text-xs font-medium text-[var(--jarvis-on-surface-muted)] hover:bg-[var(--jarvis-surface-variant)] cursor-pointer"
          >
            İptal
          </button>
          <button
            type="button"
            disabled={!title.trim() && !content.trim()}
            onClick={() => onSave(title, content)}
            className="px-4 py-2 rounded-xl bg-[var(--jarvis-primary)] text-[var(--jarvis-on-primary)] text-xs font-medium disabled:opacity-50 cursor-pointer"
          >
            Kaydet
          </button>
        </div>
      </div>
    </div>
  );
};
