import { Search, X } from 'lucide-react';
import { useEffect, useMemo, useRef, useState, type KeyboardEvent, type ReactNode } from 'react';
import {
  fetchSearchSuggestions,
  parseSearch,
  type SearchParseResponse,
  type SearchSuggestion
} from '@/api';
import { Badge } from '@/components/ui/badge';
import { cn } from '@/lib/utils';
import { quoteIfNeeded, joinCommaValues, normalizeExtension, splitCommaValues } from '@/features/search/search-query';

const SEARCH_PARSE_DEBOUNCE_MS = 250;
const SEARCH_SUGGEST_DEBOUNCE_MS = 150;
const SEARCH_FIELDS = [
  'library',
  'folder',
  'album',
  'tag',
  'extension',
  'after',
  'before',
  'on',
  'is'
] as const;
const DYNAMIC_FIELDS = new Set(['library', 'folder', 'album', 'tag', 'extension', 'is']);
const MULTI_VALUE_FIELDS = new Set(['library', 'album', 'tag', 'extension']);
const STARRED_SUGGESTION: SearchSuggestion = { value: 'is:starred', label: 'starred' };

type SearchPill = {
  id: string;
  field: string;
  value: string;
  negated: boolean;
};

type SearchToken = { start: number; end: number; value: string; field?: string; partial: string; negated: boolean };

type StructuredSearchProps = {
  disabled?: boolean;
  onChange: (value: string) => void;
  onValidQueryChange: (value: string) => void;
  placeholder?: string;
  value: string;
};

export function StructuredSearch({
  disabled = false,
  onChange,
  onValidQueryChange,
  placeholder = 'Search',
  value
}: StructuredSearchProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  // null until first emit/hydrate so an initial URL query is parsed into pills on mount
  const lastEmitted = useRef<string | null>(null);
  const pillId = useRef(0);
  const [focused, setFocused] = useState(false);
  const [pills, setPills] = useState<SearchPill[]>(() => []);
  const [draft, setDraft] = useState(() => value);
  const [parsed, setParsed] = useState<SearchParseResponse>(() => emptyParse(value));
  const [suggestions, setSuggestions] = useState<SearchSuggestion[]>([]);
  const [activeSuggestion, setActiveSuggestion] = useState(0);
  const token = useMemo(() => tokenAtCursor(draft, draft.length), [draft]);
  const composed = useMemo(() => composeQuery(pills, draft), [pills, draft]);

  function nextPillId() {
    pillId.current += 1;
    return `pill-${pillId.current}`;
  }

  function emit(nextPills: SearchPill[], nextDraft: string) {
    setPills(nextPills);
    setDraft(nextDraft);
    const next = composeQuery(nextPills, nextDraft);
    lastEmitted.current = next;
    onChange(next);
  }

  useEffect(() => {
    if (value === lastEmitted.current) return;
    let ignore = false;
    // Defer lastEmitted until apply so React Strict Mode's effect cleanup+rerun
    // does not skip hydration after cancelling the first in-flight parse.
    if (!value.includes(':')) {
      lastEmitted.current = value;
      setPills([]);
      setDraft(value);
      return;
    }
    parseSearch(value)
      .then((response) => {
        if (ignore) return;
        if (!response.valid) {
          lastEmitted.current = value;
          setPills([]);
          setDraft(value);
          return;
        }
        const nextPills = mergeMultiValuePills(
          response.clauses.map((clause) => ({
            id: nextPillId(),
            field: clause.field,
            value: clause.value,
            negated: clause.negated
          }))
        );
        const nextDraft = response.freeText;
        const normalized = composeQuery(nextPills, nextDraft);
        lastEmitted.current = normalized;
        setPills(nextPills);
        setDraft(nextDraft);
        if (normalized !== value) {
          onChange(normalized);
        }
      })
      .catch(() => {
        if (!ignore) {
          lastEmitted.current = value;
          setPills([]);
          setDraft(value);
        }
      });
    return () => {
      ignore = true;
    };
  }, [value, onChange]);

  useEffect(() => {
    if (!composed.includes(':') && pills.length === 0) {
      setParsed(emptyParse(composed));
      const handle = window.setTimeout(() => onValidQueryChange(composed.trim()), SEARCH_PARSE_DEBOUNCE_MS);
      return () => window.clearTimeout(handle);
    }
    let ignore = false;
    const handle = window.setTimeout(() => {
      parseSearch(composed)
        .then((response) => {
          if (ignore) return;
          setParsed(response);
          if (response.valid) onValidQueryChange(composed.trim());
        })
        .catch(() => {
          if (!ignore) {
            setParsed({
              ...emptyParse(composed),
              valid: false,
              errors: [{ code: 'PARSE_FAILED', message: 'Search could not be validated.', start: 0, end: composed.length }]
            });
          }
        });
    }, SEARCH_PARSE_DEBOUNCE_MS);
    return () => {
      ignore = true;
      window.clearTimeout(handle);
    };
  }, [composed, onValidQueryChange, pills.length]);

  useEffect(() => {
    if (!focused || !token) {
      setSuggestions([]);
      setActiveSuggestion(0);
      return;
    }
    if (!token.field) {
      const partial = token.value.replace(/^-/, '').toLowerCase();
      if (!partial) {
        setSuggestions([]);
        setActiveSuggestion(0);
        return;
      }
      const fieldSuggestions = SEARCH_FIELDS
        .filter((field) => field.startsWith(partial))
        .map((field) => ({ value: `${field}:`, label: `${field}:` }));
      const starredMatches = 'starred'.startsWith(partial);
      setSuggestions(starredMatches ? [...fieldSuggestions, STARRED_SUGGESTION] : fieldSuggestions);
      setActiveSuggestion(0);
      return;
    }
    if (!isSearchField(token.field)) {
      setSuggestions([]);
      setActiveSuggestion(0);
      return;
    }
    const typed = commitSuggestionForToken(token);
    if (!DYNAMIC_FIELDS.has(token.field)) {
      setSuggestions(typed ? [typed] : []);
      setActiveSuggestion(0);
      return;
    }
    let ignore = false;
    const handle = window.setTimeout(() => {
      fetchSearchSuggestions(token.field!, token.partial)
        .then((response) => {
          if (ignore) return;
          const remote = response.filter((item) => unquote(item.value).toLowerCase() !== token.partial.toLowerCase());
          const merged = typed ? [typed, ...remote] : remote;
          setSuggestions(merged);
          setActiveSuggestion(typed && remote.length > 0 ? 1 : 0);
        })
        .catch(() => {
          if (!ignore) {
            setSuggestions(typed ? [typed] : []);
            setActiveSuggestion(0);
          }
        });
    }, SEARCH_SUGGEST_DEBOUNCE_MS);
    return () => {
      ignore = true;
      window.clearTimeout(handle);
    };
  }, [focused, token]);

  function commitTokenAsPill(source: SearchToken = token!) {
    if (!source?.field || !source.partial.trim() || !isSearchField(source.field)) return false;
    const nextPills = mergeMultiValuePills([
      ...pills,
      ...pillsForFieldValue(source.field, source.partial, source.negated, nextPillId)
    ]);
    const nextDraft = `${draft.slice(0, source.start)}${draft.slice(source.end)}`.replace(/\s{2,}/g, ' ').trimStart();
    emit(nextPills, nextDraft);
    setSuggestions([]);
    focusDraft();
    return true;
  }

  function selectSuggestion(suggestion: SearchSuggestion) {
    if (!token) return;
    if (token.field) {
      const nextPills = mergeMultiValuePills([
        ...pills,
        ...pillsForFieldValue(token.field, suggestion.value, token.negated, nextPillId)
      ]);
      const nextDraft = `${draft.slice(0, token.start)}${draft.slice(token.end)}`.replace(/\s{2,}/g, ' ').trimStart();
      emit(nextPills, nextDraft);
      setSuggestions([]);
      focusDraft();
      return;
    }

    if (!suggestion.value.endsWith(':')) {
      const colon = suggestion.value.indexOf(':');
      const field = colon >= 0 ? suggestion.value.slice(0, colon) : suggestion.value;
      const value = colon >= 0 ? suggestion.value.slice(colon + 1) : 'true';
      const nextPills = mergeMultiValuePills([
        ...pills,
        ...pillsForFieldValue(field, value, token.negated, nextPillId)
      ]);
      const nextDraft = `${draft.slice(0, token.start)}${draft.slice(token.end)}`.replace(/\s{2,}/g, ' ').trimStart();
      emit(nextPills, nextDraft);
      setSuggestions([]);
      focusDraft();
      return;
    }

    const replacement = `${token.negated ? '-' : ''}${suggestion.value}`;
    const nextDraft = `${draft.slice(0, token.start)}${replacement}${draft.slice(token.end)}`;
    emit(pills, nextDraft);
    setSuggestions([]);
    window.requestAnimationFrame(() => {
      const cursor = token.start + replacement.length;
      inputRef.current?.focus();
      inputRef.current?.setSelectionRange(cursor, cursor);
    });
  }

  function focusDraft() {
    window.requestAnimationFrame(() => {
      inputRef.current?.focus();
      const cursor = inputRef.current?.value.length ?? 0;
      inputRef.current?.setSelectionRange(cursor, cursor);
    });
  }

  function removePill(id: string) {
    emit(pills.filter((pill) => pill.id !== id), draft);
    focusDraft();
  }

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'Backspace' && draft === '' && pills.length > 0 && !event.metaKey && !event.ctrlKey) {
      event.preventDefault();
      emit(pills.slice(0, -1), draft);
      return;
    }
    if (event.key === ' ' && token?.field && token.partial.trim()) {
      event.preventDefault();
      commitTokenAsPill();
      return;
    }
    if (event.key === 'ArrowDown' && suggestions.length > 0) {
      event.preventDefault();
      setActiveSuggestion((current) => (current + 1) % suggestions.length);
      return;
    }
    if (event.key === 'ArrowUp' && suggestions.length > 0) {
      event.preventDefault();
      setActiveSuggestion((current) => (current - 1 + suggestions.length) % suggestions.length);
      return;
    }
    if (event.key === 'Enter' || event.key === 'Tab') {
      if (suggestions.length > 0) {
        event.preventDefault();
        selectSuggestion(suggestions[activeSuggestion]!);
        return;
      }
      if (commitTokenAsPill()) {
        event.preventDefault();
      }
      return;
    }
    if (event.key === 'Escape') {
      setSuggestions([]);
    }
  }

  const showPanel = focused && (suggestions.length > 0 || parsed.errors.length > 0);
  const showPlaceholder = pills.length === 0 && draft.length === 0;

  return (
    <div className="relative w-full max-w-3xl">
      <div
        aria-disabled={disabled}
        className={cn(
          'relative flex min-h-9 w-full flex-wrap items-center gap-1.5 rounded-md border border-input bg-surface py-1 pl-9 pr-2 shadow-sm transition-colors',
          focused && 'border-ring ring-2 ring-ring/25',
          disabled && 'cursor-not-allowed opacity-60'
        )}
        onClick={() => {
          if (!disabled) inputRef.current?.focus();
        }}
      >
        <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" aria-hidden />
        {pills.map((pill) => (
          <Badge key={pill.id} className="max-w-full gap-1" variant="secondary">
            <PillLabel pill={pill} />
            <button
              aria-label={`Remove ${pillLabel(pill)}`}
              className="rounded-sm"
              disabled={disabled}
              onClick={(event) => {
                event.stopPropagation();
                removePill(pill.id);
              }}
              type="button"
            >
              <X className="h-3 w-3" aria-hidden />
            </button>
          </Badge>
        ))}
        <input
          ref={inputRef}
          aria-describedby={parsed.errors.length > 0 ? 'search-error' : undefined}
          aria-invalid={parsed.errors.length > 0}
          aria-label="Search"
          autoComplete="off"
          className="min-w-[8rem] flex-1 bg-transparent text-sm text-foreground outline-none placeholder:text-muted-foreground disabled:cursor-not-allowed"
          disabled={disabled}
          onBlur={() => window.setTimeout(() => setFocused(false), 100)}
          onChange={(event) => emit(pills, event.target.value)}
          onFocus={() => setFocused(true)}
          onKeyDown={handleKeyDown}
          placeholder={showPlaceholder ? placeholder : undefined}
          value={draft}
        />
      </div>
      {showPanel && (
        <div className="absolute left-0 right-0 top-[calc(100%+0.375rem)] z-50 rounded-md border border-border bg-popover p-2 text-popover-foreground shadow-lg">
          {parsed.errors[0] && <p className="px-2 py-1 text-xs text-destructive" id="search-error">{parsed.errors[0].message}</p>}
          {suggestions.length > 0 && (
            <div aria-label="Search suggestions" className="grid gap-0.5" role="listbox">
              {suggestions.map((suggestion, index) => (
                <button
                  aria-selected={index === activeSuggestion}
                  className={cn('rounded-sm px-2 py-1.5 text-left text-sm', index === activeSuggestion && 'bg-accent text-accent-foreground')}
                  key={`${suggestion.value}:${index}`}
                  onMouseDown={(event) => event.preventDefault()}
                  onClick={() => selectSuggestion(suggestion)}
                  role="option"
                  type="button"
                >
                  <SuggestionLabel label={suggestion.label} />
                </button>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function isSearchField(field: string): boolean {
  return (SEARCH_FIELDS as readonly string[]).includes(field);
}

function pillsForFieldValue(
  field: string,
  value: string,
  negated: boolean,
  nextId: () => string
): SearchPill[] {
  const values = MULTI_VALUE_FIELDS.has(field) ? splitCommaValues(value) : [unquote(value)];
  const normalized =
    field === 'extension'
      ? values.map((part) => normalizeExtension(part)).filter(Boolean)
      : values.map((part) => part.trim()).filter(Boolean);
  return normalized.map((part) => ({
    id: nextId(),
    field,
    value: part,
    negated
  }));
}

function mergeMultiValuePills(pills: SearchPill[]): SearchPill[] {
  const merged: SearchPill[] = [];
  for (const pill of pills) {
    if (!MULTI_VALUE_FIELDS.has(pill.field)) {
      merged.push(pill);
      continue;
    }
    const existing = merged.find(
      (entry) => entry.field === pill.field && entry.negated === pill.negated
    );
    if (!existing) {
      merged.push({ ...pill, value: joinCommaValues(splitCommaValues(pill.value)) });
      continue;
    }
    const values = [
      ...splitCommaValues(existing.value),
      ...splitCommaValues(pill.value)
    ];
    const unique: string[] = [];
    for (const value of values) {
      if (!unique.some((entry) => entry.toLowerCase() === value.toLowerCase())) {
        unique.push(value);
      }
    }
    existing.value = joinCommaValues(unique);
  }
  return merged;
}

function commitSuggestionForToken(token: SearchToken): SearchSuggestion | null {
  if (!token.field || !token.partial.trim()) return null;
  const value = unquote(token.partial);
  return {
    value: quoteIfNeeded(value),
    label: `${token.negated ? '-' : ''}${token.field}: ${value}`
  };
}

function composeQuery(pills: SearchPill[], draft: string): string {
  const parts = mergeMultiValuePills(pills).map(pillRaw);
  if (draft.length > 0) parts.push(draft);
  return parts.join(' ');
}

function pillRaw(pill: SearchPill): string {
  if (MULTI_VALUE_FIELDS.has(pill.field)) {
    return `${pill.negated ? '-' : ''}${pill.field}:${joinCommaValues(splitCommaValues(pill.value))}`;
  }
  return `${pill.negated ? '-' : ''}${pill.field}:${quoteIfNeeded(pill.value)}`;
}

function pillLabel(pill: SearchPill): string {
  return `${pill.negated ? 'Not ' : ''}${pill.field}: ${pill.value}`;
}

function unquote(value: string): string {
  if (value.length >= 2 && value.startsWith('"') && value.endsWith('"')) {
    return value.slice(1, -1).replace(/\\"/g, '"').replace(/\\\\/g, '\\');
  }
  return value;
}

function KeywordMark({ children }: { children: ReactNode }) {
  return <span className="text-[0.7rem] italic text-muted-foreground">{children}</span>;
}

function SuggestionLabel({ label }: { label: string }) {
  if (label === 'starred') return <KeywordMark>{label}</KeywordMark>;
  const colon = label.indexOf(':');
  if (colon < 0) return <>{label}</>;
  return (
    <>
      <KeywordMark>{label.slice(0, colon + 1)}</KeywordMark>
      {label.slice(colon + 1)}
    </>
  );
}

function PillLabel({ pill }: { pill: SearchPill }) {
  const prefix = `${pill.negated ? 'Not ' : ''}${pill.field}:`;
  return (
    <>
      <KeywordMark>{prefix}</KeywordMark>
      {' '}
      <span className="truncate">{pill.value}</span>
    </>
  );
}

function emptyParse(query: string): SearchParseResponse {
  return { query, freeText: query.trim(), clauses: [], errors: [], valid: true };
}

function tokenAtCursor(value: string, cursor: number): SearchToken | null {
  let start = cursor;
  while (start > 0 && !/\s/.test(value[start - 1]!)) start--;
  let end = cursor;
  while (end < value.length && !/\s/.test(value[end]!)) end++;
  const raw = value.slice(start, end);
  if (!raw) return { start, end, value: '', partial: '', negated: false };
  const negated = raw.startsWith('-');
  const candidate = negated ? raw.slice(1) : raw;
  const colon = candidate.indexOf(':');
  if (colon < 0) return { start, end, value: raw, partial: candidate, negated };
  return {
    start,
    end,
    value: raw,
    field: candidate.slice(0, colon).toLowerCase(),
    partial: candidate.slice(colon + 1).replace(/^"|"$/g, ''),
    negated
  };
}
