// Browser Web Speech API (SpeechRecognition + SpeechSynthesis) wrapper

interface SpeechRecognitionAlternativeLike {
  transcript: string;
}

interface SpeechRecognitionResultLike {
  isFinal: boolean;
  0: SpeechRecognitionAlternativeLike;
}

interface SpeechRecognitionEventLike {
  resultIndex: number;
  results: ArrayLike<SpeechRecognitionResultLike>;
}

interface SpeechRecognitionErrorEventLike {
  error: string;
}

interface SpeechRecognitionLike {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  onresult: ((event: SpeechRecognitionEventLike) => void) | null;
  onerror: ((event: SpeechRecognitionErrorEventLike) => void) | null;
  onend: (() => void) | null;
  start: () => void;
  stop: () => void;
  abort: () => void;
}

type SpeechRecognitionCtor = new () => SpeechRecognitionLike;

function getSpeechRecognitionCtor(): SpeechRecognitionCtor | null {
  const win = window as unknown as {
    SpeechRecognition?: SpeechRecognitionCtor;
    webkitSpeechRecognition?: SpeechRecognitionCtor;
  };
  return win.SpeechRecognition ?? win.webkitSpeechRecognition ?? null;
}

export function isSpeechRecognitionSupported(): boolean {
  return getSpeechRecognitionCtor() !== null;
}

export class VoiceRecognitionController {
  private recognition: SpeechRecognitionLike | null = null;
  private active = false;

  start(callbacks: {
    onPartial: (text: string) => void;
    onFinal: (text: string) => void;
    onError: (msg: string) => void;
    onEnd: () => void;
  }): void {
    const Ctor = getSpeechRecognitionCtor();
    if (!Ctor) {
      callbacks.onError('Bu tarayıcıda ses tanıma (Web Speech API) desteklenmiyor.');
      return;
    }

    this.stop();
    const recognition = new Ctor();
    recognition.lang = 'tr-TR';
    recognition.continuous = false;
    recognition.interimResults = true;
    this.active = true;

    let finalTranscript = '';

    recognition.onresult = (event: SpeechRecognitionEventLike) => {
      let interim = '';
      for (let i = event.resultIndex; i < event.results.length; i++) {
        const res = event.results[i];
        const txt = res[0]?.transcript ?? '';
        if (res.isFinal) {
          finalTranscript += txt;
        } else {
          interim += txt;
        }
      }
      const combined = (finalTranscript || interim).trim();
      if (combined) {
        callbacks.onPartial(combined);
      }
      if (finalTranscript.trim()) {
        const toSubmit = finalTranscript.trim();
        finalTranscript = '';
        this.active = false;
        callbacks.onFinal(toSubmit);
      }
    };

    recognition.onerror = (event: SpeechRecognitionErrorEventLike) => {
      this.active = false;
      if (event.error === 'no-speech') {
        callbacks.onError('Ses algılanamadı. Tekrar deneyin.');
      } else if (event.error === 'not-allowed') {
        callbacks.onError('Mikrofon izni reddedildi.');
      } else if (event.error !== 'aborted') {
        callbacks.onError(`Ses tanıma hatası: ${event.error}`);
      }
      callbacks.onEnd();
    };

    recognition.onend = () => {
      this.active = false;
      callbacks.onEnd();
    };

    this.recognition = recognition;
    try {
      recognition.start();
    } catch (err) {
      this.active = false;
      callbacks.onError(err instanceof Error ? err.message : 'Ses tanıma başlatılamadı.');
      callbacks.onEnd();
    }
  }

  stop(): void {
    this.active = false;
    if (this.recognition) {
      try {
        this.recognition.stop();
      } catch {
        // Ignore stop errors
      }
      this.recognition = null;
    }
  }

  isActive(): boolean {
    return this.active;
  }
}

export class WakeWordController {
  private recognition: SpeechRecognitionLike | null = null;
  private shouldRun = false;
  private paused = false;

  start(onWakeWordDetected: (commandAfterWake?: string) => void): boolean {
    const Ctor = getSpeechRecognitionCtor();
    if (!Ctor) return false;

    this.shouldRun = true;
    this.paused = false;
    this.spawnLoop(Ctor, onWakeWordDetected);
    return true;
  }

  pause(): void {
    this.paused = true;
    if (this.recognition) {
      try {
        this.recognition.abort();
      } catch {
        // Ignore
      }
      this.recognition = null;
    }
  }

  resume(onWakeWordDetected: (commandAfterWake?: string) => void): void {
    if (!this.shouldRun) return;
    this.paused = false;
    const Ctor = getSpeechRecognitionCtor();
    if (Ctor && !this.recognition) {
      this.spawnLoop(Ctor, onWakeWordDetected);
    }
  }

  stop(): void {
    this.shouldRun = false;
    this.paused = false;
    if (this.recognition) {
      try {
        this.recognition.abort();
      } catch {
        // Ignore
      }
      this.recognition = null;
    }
  }

  private spawnLoop(
    Ctor: SpeechRecognitionCtor,
    onWakeWordDetected: (commandAfterWake?: string) => void
  ): void {
    if (!this.shouldRun || this.paused) return;

    const recognition = new Ctor();
    recognition.lang = 'tr-TR';
    recognition.continuous = true;
    recognition.interimResults = true;

    recognition.onresult = (event: SpeechRecognitionEventLike) => {
      for (let i = event.resultIndex; i < event.results.length; i++) {
        const res = event.results[i];
        const text = (res[0]?.transcript ?? '').toLowerCase();
        const wakeIdx = text.indexOf('jarvis');
        if (wakeIdx !== -1) {
          const remainder = res.isFinal ? text.slice(wakeIdx + 'jarvis'.length).trim() : '';
          this.pause();
          onWakeWordDetected(remainder || undefined);
          return;
        }
      }
    };

    recognition.onerror = () => {
      // Restart quietly if still enabled
    };

    recognition.onend = () => {
      this.recognition = null;
      if (this.shouldRun && !this.paused) {
        window.setTimeout(() => {
          this.spawnLoop(Ctor, onWakeWordDetected);
        }, 450);
      }
    };

    this.recognition = recognition;
    try {
      recognition.start();
    } catch {
      this.recognition = null;
    }
  }
}

export function speakTurkishText(
  text: string,
  options?: { volume?: number; onStart?: () => void; onEnd?: () => void }
): boolean {
  if (typeof window === 'undefined' || !('speechSynthesis' in window) || !text.trim()) {
    return false;
  }
  try {
    window.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = 'tr-TR';
    utterance.volume = options?.volume ?? 1;
    utterance.onstart = () => options?.onStart?.();
    utterance.onend = () => options?.onEnd?.();
    utterance.onerror = () => options?.onEnd?.();
    window.speechSynthesis.speak(utterance);
    return true;
  } catch {
    return false;
  }
}

export function stopSpeaking(): void {
  if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
    window.speechSynthesis.cancel();
  }
}
