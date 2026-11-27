import type { TapCoordinateItem } from '../types/jarvis';

const TAP_STOP_WORDS = new Set([
  'tikla', 'tıkla', 'bas', 'dokun', 'gir', 'sec', 'seç', 'ac', 'aç', 'git',
  'sekmesine', 'sekmesi', 'butonuna', 'butonu', 'tusuna', 'tuşuna', 'tusu',
  'yerine', 'alanina', 'ekranina', 'menuye', 'menusune', 'koordinatina',
  'noktasina', 'uzerine', 'lutfen', 'hemen', 'simdi', 'bana', 'oraya',
]);

const TURKISH_SUFFIXES = new Set([
  'e', 'a', 'ye', 'ya', 'ne', 'na',
  'i', 'yi', 'ni',
  'de', 'da', 'te', 'ta',
  'den', 'dan', 'ten', 'tan',
  'le', 'la', 'yle', 'yla',
]);

export function normalizeTurkishCoordinateText(input: string): string {
  let out = '';
  for (const ch of input) {
    switch (ch) {
      case 'İ':
      case 'i':
        out += 'i';
        break;
      case 'I':
      case 'ı':
        out += 'i';
        break;
      case 'ş':
      case 'Ş':
        out += 's';
        break;
      case 'ğ':
      case 'Ğ':
        out += 'g';
        break;
      case 'ü':
      case 'Ü':
        out += 'u';
        break;
      case 'ö':
      case 'Ö':
        out += 'o';
        break;
      case 'ç':
      case 'Ç':
        out += 'c';
        break;
      case 'ə':
      case 'Ə':
        out += 'e';
        break;
      case "'":
      case '’':
      case '`':
        out += ' ';
        break;
      default:
        if (/[\p{L}\p{N}\s]/u.test(ch)) {
          out += ch.toLowerCase();
        } else {
          out += ' ';
        }
    }
  }
  return out
    .normalize('NFD')
    .replace(/\p{Mn}+/gu, '')
    .replace(/\s+/g, ' ')
    .trim();
}

function matchesTurkishStem(token: string, label: string): boolean {
  if (token === label) return true;
  if (label.length < 3 || token.length < label.length) return false;
  if (!token.startsWith(label)) return false;
  const suffix = token.slice(label.length);
  return TURKISH_SUFFIXES.has(suffix);
}

export function findZeroTokenCoordinateMatch(
  command: string,
  coordinates: TapCoordinateItem[]
): TapCoordinateItem | null {
  if (coordinates.length === 0) return null;
  const normalizedCommand = normalizeTurkishCoordinateText(command);
  if (!normalizedCommand) return null;

  const rawTokens = normalizedCommand.split(' ').filter(Boolean);
  const tokens = rawTokens.filter((t) => !TAP_STOP_WORDS.has(t));
  const commandCore = tokens.join(' ');
  const hasExplicitTapVerb = rawTokens.some((t) => TAP_STOP_WORDS.has(t));

  let bestItem: TapCoordinateItem | null = null;
  let bestScore = 0;

  for (const item of coordinates) {
    const normLabel = normalizeTurkishCoordinateText(item.label);
    if (!normLabel) continue;

    let score = 0;
    if (normalizedCommand === normLabel) score = 100;
    else if (commandCore === normLabel) score = 95;
    else if (commandCore && tokens.some((token) => matchesTurkishStem(token, normLabel))) score = 90;
    else if (normLabel.length >= 3 && commandCore.includes(normLabel)) score = 85;
    else if (hasExplicitTapVerb && normLabel.length >= 3 && normalizedCommand.includes(normLabel)) score = 80;

    if (score > bestScore) {
      bestScore = score;
      bestItem = item;
    }
  }

  return bestScore >= 80 ? bestItem : null;
}

export function findCoordinateByModelLabel(
  labelQuery: string,
  coordinates: TapCoordinateItem[]
): TapCoordinateItem | null {
  if (coordinates.length === 0) return null;
  const target = normalizeTurkishCoordinateText(labelQuery);
  if (!target) return null;

  return (
    coordinates.find((item) => normalizeTurkishCoordinateText(item.label) === target) ??
    coordinates.find((item) => {
      const nl = normalizeTurkishCoordinateText(item.label);
      return nl.includes(target) || target.includes(nl);
    }) ??
    null
  );
}
