import { XMLParser } from 'fast-xml-parser';
import type { NewsCategory, RawRssNewsItem } from '../src/types/jarvis.ts';

interface RssFeedSource {
  url: string;
  sourceName: string;
  category: NewsCategory;
}

const RSS_FEED_SOURCES: RssFeedSource[] = [
  {
    url: 'https://www.trthaber.com/sondakika.rss',
    sourceName: 'TRT Haber',
    category: 'GÜNDEM',
  },
  {
    url: 'https://www.trthaber.com/bilim_teknoloji_articles.rss',
    sourceName: 'TRT Bilim & Teknoloji',
    category: 'TEKNOLOJİ',
  },
  {
    url: 'https://www.webtekno.com/rss.xml',
    sourceName: 'Webtekno',
    category: 'TEKNOLOJİ',
  },
  {
    url: 'https://www.trthaber.com/spor_articles.rss',
    sourceName: 'TRT Spor',
    category: 'SPOR',
  },
  {
    url: 'https://www.trthaber.com/dunya_articles.rss',
    sourceName: 'TRT Dünya',
    category: 'DÜNYA',
  },
  {
    url: 'https://feeds.bbci.co.uk/turkce/rss.xml',
    sourceName: 'BBC Türkçe',
    category: 'DÜNYA',
  },
  {
    url: 'https://feeds.bbci.co.uk/news/technology/rss.xml',
    sourceName: 'BBC Technology',
    category: 'TEKNOLOJİ',
  },
];

const xmlParser = new XMLParser({
  ignoreAttributes: false,
  attributeNamePrefix: '@_',
  cdataPropName: '__cdata',
  textNodeName: '#text',
  trimValues: true,
  parseTagValue: false,
});

function extractTextValue(node: unknown): string {
  if (node === null || node === undefined) return '';
  if (typeof node === 'string' || typeof node === 'number') return String(node);
  if (Array.isArray(node)) {
    return node.map((n) => extractTextValue(n)).filter(Boolean).join(' ');
  }
  if (typeof node === 'object') {
    const obj = node as Record<string, unknown>;
    if (typeof obj.__cdata === 'string') return obj.__cdata;
    if (typeof obj['#text'] === 'string') return obj['#text'];
    if (typeof obj['@_href'] === 'string') return obj['@_href'];
  }
  return '';
}

function decodeHtmlEntities(input: string): string {
  return input
    .replace(/&amp;/gi, '&')
    .replace(/&quot;/gi, '"')
    .replace(/&#39;|&apos;/gi, "'")
    .replace(/&lt;/gi, '<')
    .replace(/&gt;/gi, '>')
    .replace(/&nbsp;/gi, ' ')
    .replace(/&#(\d+);/g, (_m, dec: string) => {
      const code = Number.parseInt(dec, 10);
      return Number.isFinite(code) ? String.fromCodePoint(code) : '';
    });
}

function stripHtmlTags(raw: string): string {
  const withoutComments = raw.replace(/<!--[\s\S]*?-->/g, ' ');
  const withoutTags = withoutComments.replace(/<[^>]+>/g, ' ');
  return decodeHtmlEntities(withoutTags).replace(/\s+/g, ' ').trim();
}

function sanitizeImageUrl(url: string | null | undefined): string | null {
  if (!url) return null;
  const trimmed = decodeHtmlEntities(url).trim();
  if (!trimmed) return null;
  if (trimmed.startsWith('https://') || trimmed.startsWith('http://')) {
    return trimmed;
  }
  if (trimmed.startsWith('//')) {
    return `https:${trimmed}`;
  }
  return null;
}

function extractImageFromRawItem(item: Record<string, unknown>, rawHtmlBlob: string): string | null {
  // 1. <enclosure url="..." type="image/..." />
  const enclosure = item.enclosure;
  const enclosures = Array.isArray(enclosure) ? enclosure : enclosure ? [enclosure] : [];
  for (const enc of enclosures) {
    if (enc && typeof enc === 'object') {
      const obj = enc as Record<string, unknown>;
      const encUrl = sanitizeImageUrl(typeof obj['@_url'] === 'string' ? obj['@_url'] : null);
      const encType = typeof obj['@_type'] === 'string' ? obj['@_type'].toLowerCase() : '';
      if (
        encUrl &&
        (!encType ||
          encType.startsWith('image/') ||
          /\.(jpg|jpeg|png|webp|avif)(\?|$)/i.test(encUrl))
      ) {
        return encUrl;
      }
    }
  }

  // 2. <media:content url="..." /> or <media:thumbnail url="..." />
  for (const key of ['media:content', 'media:thumbnail']) {
    const media = item[key];
    const list = Array.isArray(media) ? media : media ? [media] : [];
    let bestUrl: string | null = null;
    let bestWidth = -1;
    for (const m of list) {
      if (m && typeof m === 'object') {
        const obj = m as Record<string, unknown>;
        const mUrl = sanitizeImageUrl(typeof obj['@_url'] === 'string' ? obj['@_url'] : null);
        const w = Number.parseInt(String(obj['@_width'] ?? '0'), 10);
        if (mUrl && (bestUrl === null || (Number.isFinite(w) && w > bestWidth))) {
          bestUrl = mUrl;
          bestWidth = Number.isFinite(w) ? w : 0;
        }
      }
    }
    if (bestUrl) return bestUrl;
  }

  // 3. <imageUrl> or <image><url>
  const directImgUrl = sanitizeImageUrl(extractTextValue(item.imageUrl));
  if (directImgUrl) return directImgUrl;

  if (item.image && typeof item.image === 'object') {
    const imgObj = item.image as Record<string, unknown>;
    const nestedUrl = sanitizeImageUrl(extractTextValue(imgObj.url));
    if (nestedUrl) return nestedUrl;
  }

  // 4. <img src="..."> inside description or content:encoded (including commented <img>)
  const imgRegex = /<img[^>]+src\s*=\s*["']([^"']+)["']/i;
  const match = imgRegex.exec(rawHtmlBlob);
  if (match?.[1]) {
    const extracted = sanitizeImageUrl(match[1]);
    if (extracted) return extracted;
  }

  return null;
}

function formatPublishedTime(rawDate: string): string {
  const trimmed = rawDate.trim();
  if (!trimmed) return 'Az önce';
  const parsed = new Date(trimmed);
  if (Number.isNaN(parsed.getTime())) {
    return trimmed.slice(0, 16);
  }
  return new Intl.DateTimeFormat('tr-TR', {
    hour: '2-digit',
    minute: '2-digit',
    day: '2-digit',
    month: 'short',
  }).format(parsed);
}

async function fetchSingleFeed(source: RssFeedSource): Promise<RawRssNewsItem[]> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 5000);
  try {
    const res = await fetch(source.url, {
      signal: controller.signal,
      headers: {
        'User-Agent': 'JARVIS-News-Reader/1.0 (+https://jarvis.local)',
        Accept: 'application/rss+xml, application/atom+xml, application/xml, text/xml;q=0.9',
      },
    });
    if (!res.ok) return [];
    const xml = await res.text();
    const parsed = xmlParser.parse(xml) as Record<string, unknown>;

    const rssChannel = (parsed.rss as Record<string, unknown> | undefined)?.channel as
      | Record<string, unknown>
      | undefined;
    const atomFeed = parsed.feed as Record<string, unknown> | undefined;

    const rawEntries = rssChannel?.item ?? atomFeed?.entry ?? [];
    const list = Array.isArray(rawEntries) ? rawEntries : [rawEntries];

    const out: RawRssNewsItem[] = [];
    for (let i = 0; i < Math.min(list.length, 8); i++) {
      const entry = list[i];
      if (!entry || typeof entry !== 'object') continue;
      const obj = entry as Record<string, unknown>;

      const title = stripHtmlTags(extractTextValue(obj.title));
      if (!title || title.length < 10) continue;

      const rawDesc = extractTextValue(obj.description ?? obj.summary);
      const rawEncoded = extractTextValue(obj['content:encoded'] ?? obj.content);
      const htmlBlob = `${rawDesc}\n${rawEncoded}`;

      const cleanDesc = stripHtmlTags(rawDesc).slice(0, 420);
      const cleanFull = stripHtmlTags(rawEncoded || rawDesc).slice(0, 1200);
      const imageUrl = extractImageFromRawItem(obj, htmlBlob);
      const link = extractTextValue(obj.link).trim() || source.url;
      const pubDate = formatPublishedTime(
        extractTextValue(obj.pubDate ?? obj.published ?? obj.updated)
      );

      out.push({
        id: `${source.category}_${source.sourceName.replace(/\s+/g, '')}_${i}`,
        title,
        description: cleanDesc || title,
        fullContent: cleanFull || cleanDesc || title,
        sourceName: source.sourceName,
        publishedAt: pubDate,
        link,
        imageUrl,
        hintCategory: source.category,
      });
    }
    return out;
  } catch {
    return [];
  } finally {
    clearTimeout(timer);
  }
}

export async function fetchOpenRssNewsPool(): Promise<{
  items: RawRssNewsItem[];
  activeSourcesCount: number;
}> {
  const results = await Promise.all(RSS_FEED_SOURCES.map((src) => fetchSingleFeed(src)));
  const activeSourcesCount = results.filter((arr) => arr.length > 0).length;

  // Interleave items across feeds for category diversity and deduplicate headlines
  const seenTitles = new Set<string>();
  const pooled: RawRssNewsItem[] = [];
  const maxPerFeed = Math.max(0, ...results.map((r) => r.length));

  for (let round = 0; round < maxPerFeed; round++) {
    for (const feedItems of results) {
      const item = feedItems[round];
      if (!item) continue;
      const normTitle = item.title
        .toLowerCase()
        .replace(/[^\p{L}\p{N}]+/gu, ' ')
        .trim();
      if (!normTitle || seenTitles.has(normTitle)) continue;
      seenTitles.add(normTitle);
      pooled.push(item);
      if (pooled.length >= 28) {
        return { items: pooled, activeSourcesCount };
      }
    }
  }

  return { items: pooled, activeSourcesCount };
}
