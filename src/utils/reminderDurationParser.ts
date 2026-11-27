export interface ParsedDuration {
  minutes: number | null;
  seconds: number | null;
  totalMillis: number;
}

const NUMBER_WORDS: Record<string, number> = {
  bir: 1,
  iki: 2,
  uc: 3,
  dort: 4,
  bes: 5,
  alti: 6,
  yedi: 7,
  sekiz: 8,
  dokuz: 9,
  on: 10,
  onbir: 11,
  'on bir': 11,
  oniki: 12,
  'on iki': 12,
  onbes: 15,
  'on bes': 15,
  yirmi: 20,
  'yirmi bes': 25,
  otuz: 30,
  kirk: 40,
  'kirk bes': 45,
  elli: 50,
  altmis: 60,
  one: 1,
  two: 2,
  three: 3,
  four: 4,
  five: 5,
  ten: 10,
  fifteen: 15,
  twenty: 20,
  thirty: 30,
  'forty five': 45,
};

function normalizeTurkish(input: string): string {
  return input
    .toLowerCase()
    .replace(/ç/g, 'c')
    .replace(/ğ/g, 'g')
    .replace(/ı/g, 'i')
    .replace(/İ/g, 'i')
    .replace(/ö/g, 'o')
    .replace(/ş/g, 's')
    .replace(/ü/g, 'u')
    .replace(/\s+/g, ' ')
    .trim();
}

function escapeRegex(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function replaceNumberWordsBeforeUnits(text: string): string {
  let result = text;
  const sortedWords = Object.entries(NUMBER_WORDS).sort((a, b) => b[0].length - a[0].length);
  for (const [word, value] of sortedWords) {
    const regex = new RegExp(`\\b${escapeRegex(word)}\\s+(dakika|dk|saniye|sn|saat|sa)\\b`, 'g');
    result = result.replace(regex, (_m, unit) => `${value} ${unit}`);
  }
  return result;
}

function buildDuration(minutes: number | null, seconds: number | null): ParsedDuration {
  const m = minutes && minutes > 0 ? minutes : null;
  const s = seconds && seconds > 0 ? seconds : null;
  return {
    minutes: m,
    seconds: s,
    totalMillis: (m ?? 0) * 60_000 + (s ?? 0) * 1_000,
  };
}

export function parseReminderDuration(
  rawPrompt: string,
  nowMillis: number = Date.now()
): ParsedDuration | null {
  const norm = normalizeTurkish(rawPrompt);
  if (!norm) return null;

  const replaced = replaceNumberWordsBeforeUnits(norm);

  // 1. "yarım saat" / "çeyrek saat"
  const halfHourMatch = /\b(yarim|ceyrek)\s*(?:saat(?:te|lik)?|sa)\b/.exec(replaced);
  if (halfHourMatch) {
    const mins = halfHourMatch[1] === 'ceyrek' ? 15 : 30;
    return buildDuration(mins, null);
  }

  // 2. "1 buçuk saat" / "bir buçuk saat"
  const bucukMatch = /\b(\d+|bir|iki|uc|dort|bes|alti)\s*bucuk\s*(?:saat(?:te|lik)?|sa)\b/.exec(replaced);
  if (bucukMatch) {
    const rawNum = bucukMatch[1];
    const parsedNum = Number.parseInt(rawNum, 10);
    const hours = Number.isFinite(parsedNum) ? parsedNum : NUMBER_WORDS[rawNum] ?? 1;
    return buildDuration(hours * 60 + 30, null);
  }

  // 3. Explicit hours (+ optional minutes)
  const hourMinMatch =
    /(\d+(?:[.,]\d+)?)\s*(?:saat(?:te|lik)?|sa|hours?|hrs?)\b(?:\s*(?:sonra|icinde)\b)?(?:\s*(\d+)\s*(?:dakika(?:da|lik)?|dk|mins?|minutes?)\b)?/.exec(
      replaced
    );
  if (hourMinMatch) {
    const hoursFloat = Number.parseFloat(hourMinMatch[1].replace(',', '.')) || 0;
    const extraMins = hourMinMatch[2] ? Number.parseInt(hourMinMatch[2], 10) || 0 : 0;
    const totalMins = Math.round(hoursFloat * 60) + extraMins;
    if (totalMins > 0) {
      return buildDuration(totalMins, null);
    }
  }

  // 4. Explicit minutes (+ optional seconds)
  const minSecMatch =
    /(\d+(?:[.,]\d+)?)\s*(?:dakika(?:da|lik)?|dk|mins?|minutes?)\b(?:\s*(\d+)\s*(?:saniye(?:de|lik)?|sn|secs?|seconds?)\b)?/.exec(
      replaced
    );
  if (minSecMatch) {
    const minsFloat = Number.parseFloat(minSecMatch[1].replace(',', '.')) || 0;
    const extraSecs = minSecMatch[2] ? Number.parseInt(minSecMatch[2], 10) || 0 : 0;
    const totalSeconds = Math.round(minsFloat * 60) + extraSecs;
    if (totalSeconds > 0) {
      const mins = Math.floor(totalSeconds / 60);
      const secs = totalSeconds % 60;
      return buildDuration(mins > 0 ? mins : null, secs > 0 ? secs : null);
    }
  }

  // 5. Explicit seconds only
  const secOnlyMatch = /(\d+)\s*(?:saniye(?:de|lik)?|sn|secs?|seconds?)\b/.exec(replaced);
  if (secOnlyMatch) {
    const secs = Number.parseInt(secOnlyMatch[1], 10) || 0;
    if (secs > 0) {
      return buildDuration(Math.floor(secs / 60) || null, secs % 60 || null);
    }
  }

  // 6. Clock time e.g. "saat 16:30'da"
  const hasClockKeyword =
    replaced.includes('saat') ||
    replaced.includes('hatirlat') ||
    replaced.includes('alarm') ||
    replaced.includes('kur');
  if (hasClockKeyword) {
    const clockMatch = /(?:saat\s*)?([01]?\d|2[0-3])[:.]([0-5]\d)(?:'?[dt][ae]|['\s]|$)/.exec(replaced);
    if (clockMatch) {
      const targetHour = Number.parseInt(clockMatch[1], 10);
      const targetMin = Number.parseInt(clockMatch[2], 10);
      const targetDate = new Date(nowMillis);
      targetDate.setHours(targetHour, targetMin, 0, 0);
      if (targetDate.getTime() <= nowMillis + 5_000) {
        targetDate.setDate(targetDate.getDate() + 1);
      }
      const diffSeconds = Math.max(1, Math.floor((targetDate.getTime() - nowMillis) / 1_000));
      const mins = Math.floor(diffSeconds / 60);
      const secs = diffSeconds % 60;
      return buildDuration(mins > 0 ? mins : null, secs > 0 ? secs : null);
    }
  }

  return null;
}
