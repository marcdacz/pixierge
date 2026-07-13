import { Calendar, Check, ChevronDown, ChevronRight, Images, type LucideIcon } from 'lucide-react';
import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react';
import {
  assetThumbnailUrl,
  fetchAlbums,
  fetchTags,
  parseSearch,
  type LibrarySummary
} from '@/api';
import { Input } from '@/components/ui/input';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger
} from '@/components/ui/dropdown-menu';
import { cn } from '@/lib/utils';
import {
  composeSearchQuery,
  emptyFilterState,
  filtersFromParsed,
  normalizeExtension,
  SEARCH_EXTENSION_OPTIONS,
  SEARCH_IS_OPTIONS,
  toggleMultiValue,
  type SearchFilterState
} from '@/features/search/search-query';

type SearchFiltersProps = {
  libraries: LibrarySummary[];
  query: string;
  onQueryChange: (query: string) => void;
};

const FILTER_SECTION_TITLE_CLASS =
  'text-xs font-medium uppercase tracking-wide text-muted-foreground';

const FILTER_FIELD_CLASS =
  'h-8 min-w-0 w-full rounded-md border border-input bg-background px-3 text-left text-sm text-muted-foreground shadow-sm outline-none transition-colors placeholder:text-muted-foreground hover:bg-muted hover:text-foreground focus-visible:border-ring focus-visible:ring-2 focus-visible:ring-ring/25';

const FILTER_FIELD_WRAP_CLASS = 'min-w-0 px-2';

type AlbumOption = {
  name: string;
  coverAssetId: string | null;
  itemCount: number;
};

type TagOption = {
  name: string;
  assetCount: number;
};

export function SearchFilters({ libraries, query, onQueryChange }: SearchFiltersProps) {
  const [filters, setFilters] = useState<SearchFilterState>(() => emptyFilterState());
  const [albums, setAlbums] = useState<AlbumOption[]>([]);
  const [tags, setTags] = useState<TagOption[]>([]);
  const [libraryOpen, setLibraryOpen] = useState(true);
  const [albumOpen, setAlbumOpen] = useState(true);
  const [tagOpen, setTagOpen] = useState(true);

  useEffect(() => {
    let ignore = false;
    Promise.all([fetchAlbums(), fetchTags()])
      .then(([albumRows, tagRows]) => {
        if (ignore) return;
        setAlbums(
          albumRows
            .filter((album) => album.kind === 'user')
            .map((album) => ({
              name: album.name,
              coverAssetId: album.coverAssetId,
              itemCount: album.itemCount
            }))
        );
        setTags(tagRows.map((tag) => ({ name: tag.name, assetCount: tag.assetCount })));
      })
      .catch(() => {
        if (!ignore) {
          setAlbums([]);
          setTags([]);
        }
      });
    return () => {
      ignore = true;
    };
  }, []);

  useEffect(() => {
    let ignore = false;
    const handle = window.setTimeout(() => {
      if (!query.trim()) {
        setFilters(emptyFilterState());
        return;
      }
      parseSearch(query)
        .then((response) => {
          if (ignore || !response.valid) return;
          const next = filtersFromParsed(
            response.freeText,
            response.clauses.map((clause) => ({
              field: clause.field,
              value: clause.value,
              negated: clause.negated
            }))
          );
          setFilters(next);
        })
        .catch(() => undefined);
    }, 150);
    return () => {
      ignore = true;
      window.clearTimeout(handle);
    };
  }, [query]);

  function commit(next: SearchFilterState) {
    setFilters(next);
    onQueryChange(composeSearchQuery(next));
  }

  return (
    <div className="flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto pr-1">
      <CollapsibleFilterSection open={libraryOpen} onOpenChange={setLibraryOpen} title="Library">
        <FilterRow
          active={filters.libraries.length === 0}
          label="Any library"
          onClick={() => commit({ ...filters, libraries: [] })}
        />
        {libraries.map((library) => {
          const active = filters.libraries.some(
            (value) => value.toLowerCase() === library.name.toLowerCase()
          );
          return (
            <FilterRow
              active={active}
              key={library.id}
              label={library.name}
              onClick={() =>
                commit({
                  ...filters,
                  libraries: toggleMultiValue(filters.libraries, library.name, !active)
                })
              }
            />
          );
        })}
      </CollapsibleFilterSection>

      <CollapsibleFilterSection open={albumOpen} onOpenChange={setAlbumOpen} title="Album">
        {albums.length === 0 ? (
          <p className="px-2 py-1 text-sm text-muted-foreground">No albums yet.</p>
        ) : (
          albums.map((album) => {
            const active = filters.albums.some((value) => value.toLowerCase() === album.name.toLowerCase());
            return (
              <FilterRow
                active={active}
                count={album.itemCount}
                icon={Images}
                imageUrl={album.coverAssetId ? assetThumbnailUrl(album.coverAssetId, 'tiny') : null}
                key={album.name}
                label={album.name}
                onClick={() =>
                  commit({ ...filters, albums: toggleMultiValue(filters.albums, album.name, !active) })
                }
              />
            );
          })
        )}
      </CollapsibleFilterSection>

      <CollapsibleFilterSection open={tagOpen} onOpenChange={setTagOpen} title="Tag">
        {tags.length === 0 ? (
          <p className="px-2 py-1 text-sm text-muted-foreground">No tags yet.</p>
        ) : (
          tags.map((tag) => {
            const active = filters.tags.some((value) => value.toLowerCase() === tag.name.toLowerCase());
            return (
              <FilterRow
                active={active}
                count={tag.assetCount}
                key={tag.name}
                label={tag.name}
                onClick={() => commit({ ...filters, tags: toggleMultiValue(filters.tags, tag.name, !active) })}
              />
            );
          })
        )}
      </CollapsibleFilterSection>

      <FilterSection title="Status">
        {SEARCH_IS_OPTIONS.map((value) => {
          const active = filters.is.includes(value);
          return (
            <FilterRow
              active={active}
              key={value}
              label={value}
              onClick={() => commit({ ...filters, is: toggleMultiValue(filters.is, value, !active) })}
            />
          );
        })}
      </FilterSection>

      <FilterSection title="Extension">
        <div className={FILTER_FIELD_WRAP_CLASS}>
          <ExtensionFilter
            onChange={(extensions) => commit({ ...filters, extensions })}
            values={filters.extensions}
          />
        </div>
      </FilterSection>

      <FilterSection title="Captured">
        <CapturedDateFilter
          after={filters.after}
          before={filters.before}
          on={filters.on}
          onChange={(next) =>
            commit({
              ...filters,
              after: next.after,
              before: next.before,
              on: next.on
            })
          }
        />
      </FilterSection>
    </div>
  );
}

function CollapsibleFilterSection({
  title,
  open,
  onOpenChange,
  children
}: {
  title: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  children: ReactNode;
}) {
  return (
    <section className="grid gap-1">
      <button
        aria-expanded={open}
        className="flex w-full items-center gap-1 px-2 text-left transition-colors hover:text-foreground"
        onClick={() => onOpenChange(!open)}
        type="button"
      >
        {open ? (
          <ChevronDown className="h-3 w-3 shrink-0 text-muted-foreground" aria-hidden />
        ) : (
          <ChevronRight className="h-3 w-3 shrink-0 text-muted-foreground" aria-hidden />
        )}
        <h3 className={FILTER_SECTION_TITLE_CLASS}>{title}</h3>
      </button>
      {open && <div className="grid gap-0.5">{children}</div>}
    </section>
  );
}

function FilterSection({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="grid min-w-0 gap-1">
      <h3 className={cn('px-2', FILTER_SECTION_TITLE_CLASS)}>{title}</h3>
      <div className="grid min-w-0 gap-0.5">{children}</div>
    </section>
  );
}

function FilterRow({
  active,
  label,
  onClick,
  count,
  icon: RowIcon,
  imageUrl
}: {
  active: boolean;
  label: string;
  onClick: () => void;
  count?: number;
  icon?: LucideIcon;
  imageUrl?: string | null;
}) {
  return (
    <div
      className={cn(
        'group flex min-h-7 items-center gap-1 rounded-md py-0 pl-2 pr-2 text-sm text-muted-foreground transition-colors hover:bg-muted hover:text-foreground',
        active && 'bg-muted text-foreground'
      )}
    >
      <button
        aria-pressed={active}
        className="flex min-h-7 min-w-0 flex-1 items-center gap-2 overflow-hidden text-left"
        onClick={onClick}
        type="button"
      >
        {(RowIcon || imageUrl) && <FilterRowIcon icon={RowIcon} imageUrl={imageUrl} />}
        <span className="truncate">
          {label}
          {typeof count === 'number' && (
            <>
              {' '}
              <span className="text-xs tabular-nums">({count})</span>
            </>
          )}
        </span>
      </button>
    </div>
  );
}

function FilterRowIcon({ icon: RowIcon, imageUrl }: { icon?: LucideIcon; imageUrl?: string | null }) {
  const [failedUrl, setFailedUrl] = useState<string | null>(null);
  const showImage = imageUrl && imageUrl !== failedUrl;

  if (showImage) {
    return (
      <img
        alt=""
        aria-hidden
        className="h-5 w-5 shrink-0 rounded-sm object-cover"
        onError={() => setFailedUrl(imageUrl)}
        src={imageUrl}
      />
    );
  }

  if (RowIcon) {
    return <RowIcon className="h-4 w-4 shrink-0" aria-hidden />;
  }

  return null;
}

function ExtensionFilter({
  values,
  onChange
}: {
  values: string[];
  onChange: (values: string[]) => void;
}) {
  const [customDraft, setCustomDraft] = useState('');
  const knownLower = new Set(SEARCH_EXTENSION_OPTIONS.map((option) => option.toLowerCase()));
  const customValues = values.filter((value) => !knownLower.has(value.toLowerCase()));
  const label = values.length === 0 ? 'Any extension' : values.join(', ');

  function toggle(extension: string, enabled: boolean) {
    onChange(toggleMultiValue(values, extension, enabled));
  }

  function addCustom(event: FormEvent) {
    event.preventDefault();
    const normalized = normalizeExtension(customDraft);
    if (!normalized) return;
    onChange(toggleMultiValue(values, normalized, true));
    setCustomDraft('');
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <button
          aria-label="Extension filter"
          className={cn('flex items-center justify-between', FILTER_FIELD_CLASS)}
          type="button"
        >
          <span className="truncate">{label}</span>
          <ChevronDown className="h-3.5 w-3.5 shrink-0" aria-hidden />
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent
        align="start"
        className="min-w-[var(--radix-dropdown-menu-trigger-width)]"
        onCloseAutoFocus={(event) => event.preventDefault()}
      >
        <DropdownMenuItem
          onSelect={(event) => {
            event.preventDefault();
            onChange([]);
          }}
        >
          <Check
            aria-hidden
            className={cn('h-3.5 w-3.5', values.length > 0 && 'opacity-0')}
          />
          Any extension
        </DropdownMenuItem>
        {SEARCH_EXTENSION_OPTIONS.map((extension) => {
          const active = values.some((value) => value.toLowerCase() === extension.toLowerCase());
          return (
            <DropdownMenuItem
              key={extension}
              onSelect={(event) => {
                event.preventDefault();
                toggle(extension, !active);
              }}
            >
              <Check aria-hidden className={cn('h-3.5 w-3.5', !active && 'opacity-0')} />
              {extension}
            </DropdownMenuItem>
          );
        })}
        {customValues.map((extension) => (
          <DropdownMenuItem
            key={extension}
            onSelect={(event) => {
              event.preventDefault();
              toggle(extension, false);
            }}
          >
            <Check aria-hidden className="h-3.5 w-3.5" />
            {extension}
          </DropdownMenuItem>
        ))}
        <form className="border-t border-border p-1 pt-1.5" onSubmit={addCustom}>
          <Input
            aria-label="Custom extension"
            className={cn(FILTER_FIELD_CLASS, 'h-7 shadow-none')}
            onChange={(event) => setCustomDraft(event.target.value)}
            onClick={(event) => event.stopPropagation()}
            onKeyDown={(event) => event.stopPropagation()}
            placeholder=".ext"
            value={customDraft}
          />
        </form>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

type CapturedDates = {
  after: string | null;
  before: string | null;
  on: string | null;
};

type CapturedMode = 'any' | 'last7' | 'last30' | 'last90' | 'on' | 'range';

function CapturedDateFilter({
  after,
  before,
  on,
  onChange
}: CapturedDates & { onChange: (next: CapturedDates) => void }) {
  const [menuOpen, setMenuOpen] = useState(false);
  const detected = detectCapturedMode({ after, before, on });
  const [editor, setEditor] = useState<'on' | 'range' | null>(
    detected === 'on' || detected === 'range' ? detected : null
  );
  const [draftOn, setDraftOn] = useState(on ?? '');
  const [draftAfter, setDraftAfter] = useState(after ?? '');
  const [draftBefore, setDraftBefore] = useState(before ?? '');

  useEffect(() => {
    const next = detectCapturedMode({ after, before, on });
    const range = orderedRange(after ?? '', before ?? '');
    setDraftOn(on ?? '');
    setDraftAfter(range.after ?? '');
    setDraftBefore(range.before ?? '');
    if ((after || before) && (after !== range.after || before !== range.before)) {
      onChange({ ...range, on: null });
    }
    if (next === 'on' || next === 'range') {
      setEditor(next);
    } else if (next === 'last7' || next === 'last30' || next === 'last90') {
      setEditor(null);
    }
  }, [after, before, on]);

  function applyPreset(nextMode: CapturedMode) {
    if (nextMode === 'any') {
      setEditor(null);
      onChange({ after: null, before: null, on: null });
      setMenuOpen(false);
      return;
    }
    if (nextMode === 'last7' || nextMode === 'last30' || nextMode === 'last90') {
      const days = nextMode === 'last7' ? 7 : nextMode === 'last30' ? 30 : 90;
      setEditor(null);
      onChange({ after: daysAgo(days), before: null, on: null });
      setMenuOpen(false);
      return;
    }
    if (nextMode === 'on') {
      setEditor('on');
      onChange({ after: null, before: null, on: draftOn || null });
      setMenuOpen(false);
      return;
    }
    setEditor('range');
    const range = orderedRange(draftAfter, draftBefore);
    setDraftAfter(range.after ?? '');
    setDraftBefore(range.before ?? '');
    onChange({ ...range, on: null });
    setMenuOpen(false);
  }

  return (
    <div className={cn('grid gap-2', FILTER_FIELD_WRAP_CLASS)}>
      <DropdownMenu open={menuOpen} onOpenChange={setMenuOpen}>
        <DropdownMenuTrigger asChild>
          <button
            aria-label="Captured date filter"
            className={cn('flex items-center justify-between', FILTER_FIELD_CLASS)}
            type="button"
          >
            <span className="truncate">{capturedSummary({ after, before, on }, editor)}</span>
            <ChevronDown className="h-3.5 w-3.5 shrink-0" aria-hidden />
          </button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="start" className="w-64 p-1">
          <DropdownMenuItem onSelect={() => applyPreset('any')}>Any date</DropdownMenuItem>
          <DropdownMenuItem onSelect={() => applyPreset('last7')}>Last 7 days</DropdownMenuItem>
          <DropdownMenuItem onSelect={() => applyPreset('last30')}>Last 30 days</DropdownMenuItem>
          <DropdownMenuItem onSelect={() => applyPreset('last90')}>Last 90 days</DropdownMenuItem>
          <DropdownMenuItem onSelect={() => applyPreset('on')}>On date…</DropdownMenuItem>
          <DropdownMenuItem onSelect={() => applyPreset('range')}>Custom range…</DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>

      {editor === 'on' && (
        <label className="grid min-w-0 gap-1 text-xs text-muted-foreground">
          <span>On</span>
          <DateInput
            aria-label="On date"
            onChange={(value) => {
              setDraftOn(value);
              onChange({ after: null, before: null, on: value || null });
            }}
            value={draftOn}
          />
        </label>
      )}

      {editor === 'range' && (
        <div className="grid min-w-0 gap-2">
          <label className="grid min-w-0 gap-1 text-xs text-muted-foreground">
            <span>From</span>
            <DateInput
              aria-label="Captured after date"
              max={draftBefore || undefined}
              onChange={(value) => {
                const next = orderedRange(value, draftBefore);
                setDraftAfter(next.after ?? '');
                setDraftBefore(next.before ?? '');
                onChange({ ...next, on: null });
              }}
              value={draftAfter}
            />
          </label>
          <label className="grid min-w-0 gap-1 text-xs text-muted-foreground">
            <span>To</span>
            <DateInput
              aria-label="Captured before date"
              min={draftAfter || undefined}
              onChange={(value) => {
                const next = orderedRange(draftAfter, value);
                setDraftAfter(next.after ?? '');
                setDraftBefore(next.before ?? '');
                onChange({ ...next, on: null });
              }}
              value={draftBefore}
            />
          </label>
        </div>
      )}
    </div>
  );
}

function DateInput({
  'aria-label': ariaLabel,
  max,
  min,
  onChange,
  value
}: {
  'aria-label': string;
  max?: string;
  min?: string;
  onChange: (value: string) => void;
  value: string;
}) {
  const inputRef = useRef<HTMLInputElement>(null);

  function openPicker() {
    const input = inputRef.current;
    if (!input) return;
    if (typeof input.showPicker === 'function') {
      try {
        input.showPicker();
        return;
      } catch {
        // NotAllowedError when not triggered by a user gesture, or unsupported.
      }
    }
    input.focus();
  }

  return (
    <div className="relative min-w-0">
      <Input
        ref={inputRef}
        aria-label={ariaLabel}
        className={cn(FILTER_FIELD_CLASS, 'pr-9 [&::-webkit-calendar-picker-indicator]:hidden')}
        max={max}
        min={min}
        onChange={(event) => onChange(event.target.value)}
        type="date"
        value={value}
      />
      <button
        aria-label={`Open ${ariaLabel} calendar`}
        className="absolute inset-y-0 right-0 flex w-8 items-center justify-center text-muted-foreground transition-colors hover:text-foreground"
        type="button"
        onClick={openPicker}
      >
        <Calendar aria-hidden className="h-3.5 w-3.5" />
      </button>
    </div>
  );
}

function orderedRange(after: string, before: string): Pick<CapturedDates, 'after' | 'before'> {
  const nextAfter = after || null;
  const nextBefore = before || null;
  if (nextAfter && nextBefore && nextAfter > nextBefore) {
    return { after: nextBefore, before: nextAfter };
  }
  return { after: nextAfter, before: nextBefore };
}

function formatLocalDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function daysAgo(days: number): string {
  const date = new Date();
  date.setHours(0, 0, 0, 0);
  date.setDate(date.getDate() - days);
  return formatLocalDate(date);
}

function detectCapturedMode({ after, before, on }: CapturedDates): CapturedMode {
  if (on) return 'on';
  if (!after && !before) return 'any';
  if (after && !before) {
    if (after === daysAgo(7)) return 'last7';
    if (after === daysAgo(30)) return 'last30';
    if (after === daysAgo(90)) return 'last90';
  }
  return 'range';
}

function capturedSummary(
  { after, before, on }: CapturedDates,
  editor: 'on' | 'range' | null
): string {
  const mode = detectCapturedMode({ after, before, on });
  if (editor === 'on' || mode === 'on') return on ? `On ${on}` : 'On date';
  if (editor === 'range' || mode === 'range') return 'Custom';
  if (mode === 'last7') return 'Last 7 days';
  if (mode === 'last30') return 'Last 30 days';
  if (mode === 'last90') return 'Last 90 days';
  return 'Any date';
}
