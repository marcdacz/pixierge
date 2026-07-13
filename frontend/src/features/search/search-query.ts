export type SearchClauseInput = {
  field: string;
  value: string;
  negated: boolean;
};

export type SearchFilterState = {
  freeText: string;
  libraries: string[];
  folder: string | null;
  albums: string[];
  tags: string[];
  extensions: string[];
  after: string | null;
  before: string | null;
  on: string | null;
  is: string[];
  negated: SearchClauseInput[];
};

export const SEARCH_IS_OPTIONS = ['duplicate', 'starred'] as const;
export const SEARCH_EXTENSION_OPTIONS = ['.jpg', '.png', '.heic', '.mp4', '.mov'] as const;

export function normalizeExtension(value: string): string {
  const withoutDot = value.trim().toLowerCase().replace(/^\.+/, '');
  return withoutDot ? `.${withoutDot}` : '';
}

export function emptyFilterState(freeText = ''): SearchFilterState {
  return {
    freeText,
    libraries: [],
    folder: null,
    albums: [],
    tags: [],
    extensions: [],
    after: null,
    before: null,
    on: null,
    is: [],
    negated: []
  };
}

export function filtersFromParsed(freeText: string, clauses: SearchClauseInput[]): SearchFilterState {
  const next = emptyFilterState(freeText);
  for (const clause of clauses) {
    if (clause.negated) {
      next.negated.push(clause);
      continue;
    }
    switch (clause.field) {
      case 'library':
        for (const value of splitCommaValues(clause.value)) {
          next.libraries = toggleMultiValue(next.libraries, value, true);
        }
        break;
      case 'folder':
        next.folder = clause.value;
        break;
      case 'album':
        for (const value of splitCommaValues(clause.value)) {
          next.albums = toggleMultiValue(next.albums, value, true);
        }
        break;
      case 'tag':
        for (const value of splitCommaValues(clause.value)) {
          next.tags = toggleMultiValue(next.tags, value, true);
        }
        break;
      case 'extension': {
        for (const part of splitCommaValues(clause.value)) {
          const extension = normalizeExtension(part);
          if (extension) {
            next.extensions = toggleMultiValue(next.extensions, extension, true);
          }
        }
        break;
      }
      case 'after':
        next.after = clause.value;
        break;
      case 'before':
        next.before = clause.value;
        break;
      case 'on':
        next.on = clause.value;
        break;
      case 'is':
        next.is = toggleMultiValue(next.is, clause.value, true);
        break;
      default:
        next.negated.push(clause);
        break;
    }
  }
  return next;
}

export function composeSearchQuery(filters: SearchFilterState): string {
  const parts: string[] = [];
  if (filters.libraries.length > 0) {
    parts.push(`library:${joinCommaValues(filters.libraries)}`);
  }
  if (filters.folder) parts.push(`folder:${quoteIfNeeded(filters.folder)}`);
  if (filters.albums.length > 0) {
    parts.push(`album:${joinCommaValues(filters.albums)}`);
  }
  if (filters.tags.length > 0) {
    parts.push(`tag:${joinCommaValues(filters.tags)}`);
  }
  if (filters.extensions.length > 0) {
    parts.push(`extension:${joinCommaValues(filters.extensions)}`);
  }
  if (filters.after) parts.push(`after:${filters.after}`);
  if (filters.before) parts.push(`before:${filters.before}`);
  if (filters.on) parts.push(`on:${filters.on}`);
  for (const value of filters.is) parts.push(`is:${value}`);
  for (const clause of filters.negated) {
    parts.push(`${clause.negated ? '-' : ''}${clause.field}:${quoteIfNeeded(clause.value)}`);
  }
  if (filters.freeText.trim()) parts.push(filters.freeText.trim());
  return parts.join(' ');
}

export function quoteIfNeeded(value: string): string {
  if (/\s/.test(value)) {
    return `"${value.replace(/\\/g, '\\\\').replace(/"/g, '\\"')}"`;
  }
  return value;
}

export function joinCommaValues(values: string[]): string {
  return values.map(quoteIfNeeded).join(',');
}

export function splitCommaValues(value: string): string[] {
  const parts: string[] = [];
  let current = '';
  let quoted = false;
  let escaped = false;
  for (let index = 0; index < value.length; index += 1) {
    const character = value[index]!;
    if (escaped) {
      current += character;
      escaped = false;
      continue;
    }
    if (character === '\\' && quoted) {
      escaped = true;
      continue;
    }
    if (character === '"') {
      quoted = !quoted;
      continue;
    }
    if (character === ',' && !quoted) {
      const trimmed = current.trim();
      if (trimmed) parts.push(trimmed);
      current = '';
      continue;
    }
    current += character;
  }
  const trimmed = current.trim();
  if (trimmed) parts.push(trimmed);
  return parts;
}

export function toggleMultiValue(values: string[], value: string, enabled: boolean): string[] {
  const normalized = value.trim();
  if (!normalized) {
    return values;
  }
  const without = values.filter((entry) => entry.toLowerCase() !== normalized.toLowerCase());
  return enabled ? [...without, normalized] : without;
}
