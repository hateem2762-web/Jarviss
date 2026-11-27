import React, { useEffect, useRef } from 'react';
import type { JarvisMood, JarvisVisualState } from '../types/jarvis';

interface NebulaParticle {
  baseAngle: number;
  baseRadiusNorm: number;
  orbitSpeed: number;
  radialFreq: number;
  phase: number;
  sizeScale: number;
  layer: number;
  baseAlpha: number;
  colorMix: number;
}

function createSeededRng(seed: number): () => number {
  let s = seed >>> 0;
  return () => {
    s = (s * 1664525 + 1013904223) >>> 0;
    return s / 4294967296;
  };
}

function createParticles(count: number): NebulaParticle[] {
  const rng = createSeededRng(4207);
  const list: NebulaParticle[] = [];
  for (let idx = 0; idx < count; idx++) {
    const layer = idx < count * 0.42 ? 0 : idx < count * 0.8 ? 1 : 2;
    const u = rng();
    const radiusNorm =
      layer === 0
        ? Math.sqrt(u) * 0.56
        : layer === 1
          ? 0.34 + Math.sqrt(u) * 0.56
          : 0.62 + u * 0.52;
    const dirSign = idx % 3 === 0 ? -1 : 1;
    list.push({
      baseAngle: rng() * Math.PI * 2,
      baseRadiusNorm: radiusNorm,
      orbitSpeed: dirSign * (0.14 + rng() * 0.36),
      radialFreq: 0.8 + rng() * 1.9,
      phase: rng() * Math.PI * 2,
      sizeScale:
        layer === 0
          ? 0.7 + rng() * 0.85
          : layer === 1
            ? 0.55 + rng() * 0.9
            : 0.4 + rng() * 0.65,
      layer,
      baseAlpha:
        layer === 0
          ? 0.55 + rng() * 0.45
          : layer === 1
            ? 0.4 + rng() * 0.45
            : 0.22 + rng() * 0.35,
      colorMix: rng(),
    });
  }
  return list;
}

type Rgb = [number, number, number];

function targetPalette(state: JarvisVisualState, mood: JarvisMood): [Rgb, Rgb] {
  if (state === 'ERROR' || mood === 'alert') {
    return [
      [225, 29, 72],
      [253, 164, 175],
    ];
  }
  if (state === 'LISTENING') {
    return [
      [6, 182, 212],
      [52, 211, 153],
    ];
  }
  switch (mood) {
    case 'neutral':
      return state === 'IDLE'
        ? [
            [23, 63, 158],
            [96, 165, 250],
          ]
        : [
            [37, 99, 235],
            [125, 211, 252],
          ];
    case 'serious':
      return [
        [217, 119, 6],
        [253, 224, 71],
      ];
    case 'analytical':
      return [
        [99, 102, 241],
        [192, 132, 252],
      ];
    case 'energetic':
      return [
        [6, 182, 212],
        [103, 232, 249],
      ];
  }
}

function lerpRgb(from: Rgb, to: Rgb, t: number): Rgb {
  const f = Math.min(1, Math.max(0, t));
  return [
    Math.round(from[0] + (to[0] - from[0]) * f),
    Math.round(from[1] + (to[1] - from[1]) * f),
    Math.round(from[2] + (to[2] - from[2]) * f),
  ];
}

function rgba(rgb: Rgb, alpha: number): string {
  return `rgba(${rgb[0]}, ${rgb[1]}, ${rgb[2]}, ${Math.min(1, Math.max(0, alpha))})`;
}

interface ParticleNebulaCanvasProps {
  visualState: JarvisVisualState;
  mood: JarvisMood;
  rmsLevel: number;
  compactMode?: boolean;
  className?: string;
}

export const ParticleNebulaCanvas: React.FC<ParticleNebulaCanvasProps> = ({
  visualState,
  mood,
  rmsLevel,
  compactMode = false,
  className = '',
}) => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const stateRef = useRef({
    visualState,
    mood,
    targetRms: Math.min(1, Math.max(0, rmsLevel)),
    compactMode,
  });

  const animRef = useRef<{
    smoothedRms: number;
    currentPrimary: Rgb;
    currentSecondary: Rgb;
    shockwaveProgress: number;
    elapsedSeconds: number;
    lastTime: number;
    prevVisualState: JarvisVisualState;
    prevMood: JarvisMood;
    particles: NebulaParticle[];
  }>({
    smoothedRms: 0,
    currentPrimary: [30, 86, 198],
    currentSecondary: [125, 211, 252],
    shockwaveProgress: 1,
    elapsedSeconds: 0,
    lastTime: 0,
    prevVisualState: visualState,
    prevMood: mood,
    particles: createParticles(compactMode ? 74 : 290),
  });

  useEffect(() => {
    const prev = animRef.current;
    const stateChanged = prev.prevVisualState !== visualState || prev.prevMood !== mood;
    const becameAlert =
      (visualState === 'ERROR' && prev.prevVisualState !== 'ERROR') ||
      (mood === 'alert' && prev.prevMood !== 'alert') ||
      (mood === 'energetic' && prev.prevMood !== 'energetic' && visualState === 'SPEAKING');

    if (becameAlert && stateChanged) {
      prev.shockwaveProgress = 0;
    }
    prev.prevVisualState = visualState;
    prev.prevMood = mood;

    if (stateRef.current.compactMode !== compactMode) {
      prev.particles = createParticles(compactMode ? 74 : 290);
    }

    stateRef.current = {
      visualState,
      mood,
      targetRms: Math.min(1, Math.max(0, rmsLevel)),
      compactMode,
    };
  }, [visualState, mood, rmsLevel, compactMode]);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let rafId = 0;

    const render = (now: number) => {
      const anim = animRef.current;
      const cur = stateRef.current;

      if (anim.lastTime === 0) {
        anim.lastTime = now;
      }
      const dt = Math.min(0.05, Math.max(0.001, (now - anim.lastTime) / 1000));
      anim.lastTime = now;

      const dpr = window.devicePixelRatio || 1;
      const rect = canvas.getBoundingClientRect();
      const targetW = Math.max(1, Math.floor(rect.width * dpr));
      const targetH = Math.max(1, Math.floor(rect.height * dpr));
      if (canvas.width !== targetW || canvas.height !== targetH) {
        canvas.width = targetW;
        canvas.height = targetH;
      }

      const w = rect.width;
      const h = rect.height;
      if (w > 0 && h > 0) {
        ctx.save();
        ctx.scale(dpr, dpr);
        ctx.clearRect(0, 0, w, h);

        const rmsAttack = cur.targetRms > anim.smoothedRms ? 14 : 6;
        anim.smoothedRms += (cur.targetRms - anim.smoothedRms) * Math.min(1, dt * rmsAttack);

        let speedMultiplier = 0.68;
        if (cur.visualState === 'ERROR') speedMultiplier = 2.3;
        else if (cur.visualState === 'LISTENING') speedMultiplier = 1.45 + anim.smoothedRms * 2.1;
        else if (cur.visualState === 'THINKING') speedMultiplier = 1.75;
        else if (cur.visualState === 'SPEAKING') {
          if (cur.mood === 'energetic') speedMultiplier = 1.9 + anim.smoothedRms * 1.1;
          else if (cur.mood === 'serious') speedMultiplier = 1.65 + anim.smoothedRms * 0.8;
          else if (cur.mood === 'analytical') speedMultiplier = 1.25 + anim.smoothedRms * 0.6;
          else if (cur.mood === 'alert') speedMultiplier = 2.1;
          else speedMultiplier = 1.35 + anim.smoothedRms * 0.7;
        } else {
          if (cur.mood === 'serious') speedMultiplier = 1.05;
          else if (cur.mood === 'analytical') speedMultiplier = 0.85;
          else if (cur.mood === 'energetic') speedMultiplier = 1.1;
          else if (cur.mood === 'alert') speedMultiplier = 1.5;
          else speedMultiplier = 0.68;
        }
        anim.elapsedSeconds += dt * speedMultiplier;

        const [targetPrim, targetSec] = targetPalette(cur.visualState, cur.mood);
        const colorLerp = Math.min(1, dt * 4.5);
        anim.currentPrimary = lerpRgb(anim.currentPrimary, targetPrim, colorLerp);
        anim.currentSecondary = lerpRgb(anim.currentSecondary, targetSec, colorLerp);

        if (anim.shockwaveProgress < 1) {
          anim.shockwaveProgress = Math.min(1, anim.shockwaveProgress + dt * 1.7);
        }

        const cx = w * 0.5;
        const cy = h * 0.5;
        const minDim = Math.min(w, h);
        const baseRadius = (cur.compactMode ? 0.34 : 0.36) * minDim;

        const breathAmp = cur.visualState === 'IDLE' ? 0.045 : 0.06;
        const breath = 1 + Math.sin(anim.elapsedSeconds * 1.45) * breathAmp;
        const audioBoost = anim.smoothedRms * (cur.visualState === 'LISTENING' ? 0.24 : 0.14);
        const burstScale =
          anim.shockwaveProgress < 1 && cur.visualState === 'ERROR'
            ? Math.sin(anim.shockwaveProgress * Math.PI) * 0.16
            : 0;
        const activeRadius = baseRadius * (breath + audioBoost + burstScale);

        // Core ambient glow
        const glowRadius = Math.max(1, activeRadius * 1.32);
        const grad = ctx.createRadialGradient(cx, cy, 0, cx, cy, glowRadius);
        grad.addColorStop(0, rgba(anim.currentSecondary, cur.compactMode ? 0.37 : 0.28));
        grad.addColorStop(0.52, rgba(anim.currentPrimary, cur.compactMode ? 0.19 : 0.14));
        grad.addColorStop(1, 'rgba(0,0,0,0)');
        ctx.fillStyle = grad;
        ctx.beginPath();
        ctx.arc(cx, cy, glowRadius, 0, Math.PI * 2);
        ctx.fill();

        // Optional shockwave ring
        if (anim.shockwaveProgress < 1) {
          const ringRadius = activeRadius * (0.55 + anim.shockwaveProgress * 0.9);
          const ringAlpha = ((1 - anim.shockwaveProgress) * 165) / 255;
          ctx.strokeStyle = rgba(anim.currentSecondary, ringAlpha);
          ctx.lineWidth = 1.6;
          ctx.beginPath();
          ctx.arc(cx, cy, ringRadius, 0, Math.PI * 2);
          ctx.stroke();
        }

        // Subtle segmented HUD orbital arcs in full mode
        if (!cur.compactMode) {
          const arcR = activeRadius * 1.15;
          ctx.strokeStyle = rgba(anim.currentSecondary, 46 / 255);
          ctx.lineWidth = 1.0;
          const rotRad = ((anim.elapsedSeconds * 14) % 360) * (Math.PI / 180);
          const sweep = (48 * Math.PI) / 180;
          ctx.beginPath();
          ctx.arc(cx, cy, arcR, rotRad, rotRad + sweep);
          ctx.stroke();
          ctx.beginPath();
          ctx.arc(cx, cy, arcR, rotRad + Math.PI, rotRad + Math.PI + sweep);
          ctx.stroke();
        }

        const jitterScale =
          cur.visualState === 'ERROR'
            ? (1 - anim.shockwaveProgress * 0.5) * 7
            : cur.visualState === 'LISTENING'
              ? anim.smoothedRms * 5.2
              : cur.visualState === 'SPEAKING'
                ? 0.8 + anim.smoothedRms * 3.5
                : 0;

        const waveStrength =
          cur.mood === 'analytical'
            ? 0.14
            : cur.mood === 'serious'
              ? 0.08
              : cur.mood === 'energetic'
                ? 0.16
                : cur.mood === 'alert'
                  ? 0.18
                  : 0.1;

        const orbitTightness = cur.mood === 'serious' ? 0.88 : 1.0;

        for (const p of anim.particles) {
          const layerMult = p.layer === 0 ? 1.15 : p.layer === 1 ? 0.8 : 0.45;
          const angle = p.baseAngle + anim.elapsedSeconds * p.orbitSpeed * layerMult;

          const organicEdge =
            1 +
            0.11 * Math.sin(angle * 3 + anim.elapsedSeconds * 0.75) +
            0.08 * Math.cos(angle * 5 - anim.elapsedSeconds * 1.1) +
            0.05 * Math.sin(angle * 7 + anim.elapsedSeconds * 1.5 + p.phase);

          const dispersion =
            Math.sin(anim.elapsedSeconds * p.radialFreq + p.phase) *
            waveStrength *
            (0.35 + p.baseRadiusNorm * 0.85);

          const audioExpand =
            p.layer >= 1
              ? anim.smoothedRms * 0.22 * p.baseRadiusNorm
              : anim.smoothedRms * 0.07;

          const rNorm = Math.min(
            1.32,
            Math.max(0.02, p.baseRadiusNorm * orbitTightness + dispersion + audioExpand)
          );
          const dist = activeRadius * rNorm * organicEdge;

          const jitterX =
            jitterScale > 0.1
              ? Math.sin(anim.elapsedSeconds * 21 + p.phase * 3) * jitterScale
              : 0;
          const jitterY =
            jitterScale > 0.1
              ? Math.cos(anim.elapsedSeconds * 19 + p.phase * 5) * jitterScale
              : 0;

          const px = cx + Math.cos(angle) * dist + jitterX;
          const py = cy + Math.sin(angle) * dist + jitterY;

          const baseDotPx = (cur.compactMode ? 1.1 : 1.45) * p.sizeScale;
          const dotRadius = baseDotPx * (1 + anim.smoothedRms * 0.28);

          const particleColor = lerpRgb(anim.currentPrimary, anim.currentSecondary, p.colorMix);
          const flicker = 0.76 + 0.24 * Math.sin(anim.elapsedSeconds * 2.2 + p.phase * 2);
          const edgeFade = Math.min(1, Math.max(0.22, 1.18 - rNorm * 0.48));
          const alpha = Math.min(1, Math.max(0.07, p.baseAlpha * flicker * edgeFade));

          if (p.sizeScale > 1.08) {
            ctx.fillStyle = rgba(particleColor, alpha * 0.26);
            ctx.beginPath();
            ctx.arc(px, py, dotRadius * 2.35, 0, Math.PI * 2);
            ctx.fill();
          }

          ctx.fillStyle = rgba(particleColor, alpha);
          ctx.beginPath();
          ctx.arc(px, py, dotRadius, 0, Math.PI * 2);
          ctx.fill();
        }

        ctx.restore();
      }

      rafId = requestAnimationFrame(render);
    };

    rafId = requestAnimationFrame(render);
    return () => cancelAnimationFrame(rafId);
  }, []);

  return <canvas ref={canvasRef} className={`w-full h-full block ${className}`} />;
};
