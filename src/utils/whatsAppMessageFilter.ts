import { normalizeTurkishCoordinateText } from './coordinateMatcher';

const URGENT_KEYWORDS = new Set([
  'acil', 'hemen', 'cabuk', 'çabuk', 'kaza', 'hastane', 'polis', 'ambulans',
  'ara', 'arayin', 'arayın', 'yardim', 'yardım', 'onemli', 'önemli', 'iptal',
  'toplantı', 'toplanti', 'fatura', 'odeme', 'ödeme', 'banka', 'sifre', 'şifre',
  'kod', 'ucus', 'uçuş', 'sinav', 'sınav', 'son', 'vakit', 'urgent', 'asap',
  'call', 'sos',
]);

const TRIVIAL_WORDS = new Set([
  'ok', 'okay', 'okey', 'okdir', 'k', 'kk', 'tamam', 'tamamdir', 'tamamdır',
  'tmm', 'tm', 'olur', 'oldu', 'peki', 'eyv', 'eyvallah', 'sagol', 'sağol',
  'saol', 'tesekkurler', 'teşekkürler', 'tesekkur', 'teşekkür', 'tsk', 'tşk',
  'rica', 'evet', 'evt', 'hayir', 'hayır', 'yok', 'aynen', 'he', 'ha', 'hi',
  'hı', 'hmm', 'hm', 'hmmm', 'hehe', 'haha', 'hahaha', 'hahahaha', 'lol',
  'lmao', 'sjsj', 'sjsjsj', 'asdf', 'slm', 'selam', 'mrb', 'merhaba', 'nbr',
  'iyiyim', 'iyi', 'guzel', 'güzel', 'super', 'süper', 'harika', 'bay', 'by',
  'bye', 'bb', 'gorusuruz', 'görüşürüz', 'byee', 'amin', 'insallah', 'inşallah',
  'masallah', 'maşallah',
]);

const SYSTEM_NOISE_PATTERNS = [
  /^\d+\s+(yeni\s+)?mesaj.*$/i,
  /^\d+\s+sohbetten\s+\d+\s+mesaj.*$/i,
  /^\d+\s+new\s+messages?.*$/i,
  /^\d+\s+messages?\s+from\s+\d+\s+chats?.*$/i,
  /.*mesajlar\s+kontrol\s+ediliyor.*/i,
  /.*checking\s+for\s+new\s+messages.*/i,
  /.*whatsapp\s+web.*aktif.*/i,
  /.*yedekleme.*/i,
  /.*backing\s+up\s+messages.*/i,
  /.*gelen\s+sesli\s+arama.*/i,
  /.*cevapsız\s+(sesli|görüntülü)\s+arama.*/i,
  /^bu\s+mesaj\s+silindi\.?$/i,
  /^this\s+message\s+was\s+deleted\.?$/i,
];

const PURE_LAUGH_OR_SMASH_REGEX = /^(ha|he|hi|sj|as|df|kd|wq|xo)+[haesjkdwqxo!?.]*$/i;

export function isSystemOrSummaryNoise(senderName: string, messageText: string): boolean {
  const cleanSender = senderName.trim();
  const cleanMsg = messageText.trim();
  if (!cleanSender || !cleanMsg) return true;

  const lowerSender = cleanSender.toLowerCase();
  if (lowerSender === 'whatsapp' || lowerSender === 'whatsapp business') {
    return true;
  }

  return SYSTEM_NOISE_PATTERNS.some((p) => p.test(cleanMsg));
}

export function shouldAnalyzeWhatsAppWithAi(senderName: string, messageText: string): boolean {
  if (isSystemOrSummaryNoise(senderName, messageText)) return false;

  const cleanMsg = messageText.trim();
  const normalized = normalizeTurkishCoordinateText(cleanMsg);
  if (!normalized) return false;

  const tokens = normalized.split(' ').filter(Boolean);
  if (tokens.some((t) => URGENT_KEYWORDS.has(t))) {
    return true;
  }

  const letterCount = (normalized.match(/[\p{L}\p{N}]/gu) ?? []).length;
  if (letterCount < 3) return false;

  if (tokens.every((t) => TRIVIAL_WORDS.has(t) || PURE_LAUGH_OR_SMASH_REGEX.test(t))) {
    return false;
  }

  if (tokens.length === 1 && normalized.length <= 4 && !cleanMsg.includes('?')) {
    return false;
  }

  return true;
}
