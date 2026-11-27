import { FilesetResolver, HandLandmarker } from '@mediapipe/tasks-vision';

export type AirGestureDirection = 'SWIPE_LEFT' | 'SWIPE_RIGHT';

export type GestureSensorStatus =
  | 'IDLE'
  | 'INITIALIZING'
  | 'ACTIVE'
  | 'UNSUPPORTED'
  | 'PERMISSION_DENIED'
  | 'ERROR';

interface PalmSample {
  x: number;
  t: number;
}

const COOLDOWN_MS = 1200;
const WINDOW_MS = 420;
const SWIPE_THRESHOLD = 0.19;
const MIN_FRAME_INTERVAL_MS = 55; // ~18 fps for low battery/CPU draw

export class AirGestureController {
  private stream: MediaStream | null = null;
  private videoEl: HTMLVideoElement | null = null;
  private landmarker: HandLandmarker | null = null;
  private rafId: number | null = null;
  private running = false;
  private lastInferenceAt = 0;
  private lastSwipeAt = 0;
  private samples: PalmSample[] = [];
  private statusCallback: ((status: GestureSensorStatus, note?: string) => void) | null = null;

  async start(params: {
    onSwipe: (direction: AirGestureDirection) => void;
    onStatusChange: (status: GestureSensorStatus, note?: string) => void;
  }): Promise<void> {
    this.stop();
    this.statusCallback = params.onStatusChange;

    if (
      typeof navigator === 'undefined' ||
      !navigator.mediaDevices ||
      typeof navigator.mediaDevices.getUserMedia !== 'function'
    ) {
      params.onStatusChange('UNSUPPORTED', 'Bu cihaz/tarayıcı kamera akışını desteklemiyor.');
      return;
    }

    this.running = true;
    params.onStatusChange('INITIALIZING');

    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: {
          facingMode: 'user',
          width: { ideal: 320 },
          height: { ideal: 240 },
        },
        audio: false,
      });

      if (!this.running) {
        stream.getTracks().forEach((t) => t.stop());
        return;
      }

      this.stream = stream;

      const video = document.createElement('video');
      video.playsInline = true;
      video.muted = true;
      video.autoplay = true;
      video.srcObject = stream;
      await video.play();

      if (!this.running) {
        this.releaseHardware();
        return;
      }

      this.videoEl = video;

      const vision = await FilesetResolver.forVisionTasks('/mediapipe/wasm');
      if (!this.running) {
        this.releaseHardware();
        return;
      }

      let handLandmarker: HandLandmarker;
      try {
        handLandmarker = await HandLandmarker.createFromOptions(vision, {
          baseOptions: {
            modelAssetPath: '/mediapipe/hand_landmarker.task',
            delegate: 'GPU',
          },
          runningMode: 'VIDEO',
          numHands: 1,
          minHandDetectionConfidence: 0.55,
          minHandPresenceConfidence: 0.55,
          minTrackingConfidence: 0.5,
        });
      } catch {
        // Fallback to CPU delegate if WebGL GPU delegate fails in constrained browsers
        handLandmarker = await HandLandmarker.createFromOptions(vision, {
          baseOptions: {
            modelAssetPath: '/mediapipe/hand_landmarker.task',
            delegate: 'CPU',
          },
          runningMode: 'VIDEO',
          numHands: 1,
          minHandDetectionConfidence: 0.55,
          minHandPresenceConfidence: 0.55,
          minTrackingConfidence: 0.5,
        });
      }

      if (!this.running) {
        handLandmarker.close();
        this.releaseHardware();
        return;
      }

      this.landmarker = handLandmarker;
      params.onStatusChange('ACTIVE');

      const loop = () => {
        if (!this.running || !this.videoEl || !this.landmarker) return;
        const now = performance.now();
        if (
          this.videoEl.readyState >= 2 &&
          now - this.lastInferenceAt >= MIN_FRAME_INTERVAL_MS
        ) {
          this.lastInferenceAt = now;
          try {
            const result = this.landmarker.detectForVideo(this.videoEl, now);
            const hand = result.landmarks?.[0];
            if (hand && hand.length > 9) {
              // Average wrist (0) + middle MCP (9), mirrored for natural front-camera direction
              const rawPalmX = (hand[0].x + hand[9].x) * 0.5;
              const mirroredX = 1 - rawPalmX;
              this.samples.push({ x: mirroredX, t: now });
              this.samples = this.samples.filter((s) => now - s.t <= WINDOW_MS);

              if (
                this.samples.length >= 3 &&
                now - this.lastSwipeAt >= COOLDOWN_MS
              ) {
                const oldest = this.samples[0];
                const latest = this.samples[this.samples.length - 1];
                const deltaX = latest.x - oldest.x;
                if (deltaX >= SWIPE_THRESHOLD) {
                  this.lastSwipeAt = now;
                  this.samples = [];
                  params.onSwipe('SWIPE_RIGHT');
                } else if (deltaX <= -SWIPE_THRESHOLD) {
                  this.lastSwipeAt = now;
                  this.samples = [];
                  params.onSwipe('SWIPE_LEFT');
                }
              }
            } else {
              // Reset stale trail when hand leaves frame
              this.samples = this.samples.filter((s) => now - s.t <= 160);
            }
          } catch {
            // Skip transient frame errors
          }
        }
        this.rafId = window.requestAnimationFrame(loop);
      };

      this.rafId = window.requestAnimationFrame(loop);
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err ?? '');
      const lower = msg.toLowerCase();
      this.releaseHardware();
      if (lower.includes('permission') || lower.includes('notallowed') || lower.includes('denied')) {
        params.onStatusChange('PERMISSION_DENIED', 'Kamera izni verilmedi.');
      } else {
        params.onStatusChange('ERROR', msg || 'El hareketi sensörü başlatılamadı.');
      }
    }
  }

  private releaseHardware(): void {
    if (this.rafId !== null) {
      window.cancelAnimationFrame(this.rafId);
      this.rafId = null;
    }
    if (this.landmarker) {
      try {
        this.landmarker.close();
      } catch {
        // Ignore close errors
      }
      this.landmarker = null;
    }
    if (this.videoEl) {
      try {
        this.videoEl.pause();
        this.videoEl.srcObject = null;
      } catch {
        // Ignore video cleanup errors
      }
      this.videoEl = null;
    }
    if (this.stream) {
      this.stream.getTracks().forEach((track) => track.stop());
      this.stream = null;
    }
    this.samples = [];
  }

  stop(): void {
    this.running = false;
    this.releaseHardware();
    this.statusCallback?.('IDLE');
  }
}
