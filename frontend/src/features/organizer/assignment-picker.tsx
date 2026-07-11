import { useEffect, useId, useMemo, useRef, useState, type KeyboardEvent } from 'react';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { cn } from '@/lib/utils';

export type AssignmentDestination = { id: string; name: string };

type AssignmentPickerProps = {
  open: boolean;
  title: string;
  createVerb: string;
  destinations: AssignmentDestination[];
  loading?: boolean;
  error?: string | null;
  onClose: () => void;
  onCreate: (name: string) => Promise<AssignmentDestination>;
  onApply: (ids: string[]) => Promise<void>;
};

export function AssignmentPicker({
  open,
  title,
  createVerb,
  destinations,
  loading = false,
  error = null,
  onClose,
  onCreate,
  onApply
}: AssignmentPickerProps) {
  const titleId = useId();
  const listboxId = useId();
  const inputRef = useRef<HTMLInputElement>(null);
  const [query, setQuery] = useState('');
  const [selected, setSelected] = useState<AssignmentDestination[]>([]);
  const [activeIndex, setActiveIndex] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [mutationError, setMutationError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);

  useEffect(() => {
    if (!open) {
      return;
    }
    setQuery('');
    setSelected([]);
    setActiveIndex(0);
    setMutationError(null);
    setSubmitting(false);
    setCreating(false);
    const frame = window.requestAnimationFrame(() => inputRef.current?.focus());
    return () => window.cancelAnimationFrame(frame);
  }, [open]);

  const normalizedQuery = query.trim();
  const selectedIds = useMemo(() => new Set(selected.map((item) => item.id)), [selected]);
  const matches = useMemo(
    () =>
      destinations.filter(
        (item) =>
          !selectedIds.has(item.id) &&
          item.name.toLowerCase().includes(normalizedQuery.toLowerCase())
      ),
    [destinations, normalizedQuery, selectedIds]
  );
  const exactMatch = destinations.some(
    (item) => item.name.toLowerCase() === normalizedQuery.toLowerCase()
  );
  const canCreate = normalizedQuery.length > 0 && !exactMatch;
  const showSuggestions = normalizedQuery.length > 0 && !loading && !error;
  const optionCount = showSuggestions ? matches.length + (canCreate ? 1 : 0) : 0;

  useEffect(() => {
    setActiveIndex(0);
  }, [normalizedQuery, matches.length, canCreate]);

  if (!open) {
    return null;
  }

  function addDestination(destination: AssignmentDestination) {
    setSelected((current) =>
      current.some((item) => item.id === destination.id) ? current : [...current, destination]
    );
    setQuery('');
    inputRef.current?.focus();
  }

  function removeDestination(id: string) {
    setSelected((current) => current.filter((item) => item.id !== id));
    inputRef.current?.focus();
  }

  async function create() {
    if (!canCreate || creating) {
      return;
    }
    setCreating(true);
    setMutationError(null);
    try {
      const created = await onCreate(normalizedQuery);
      addDestination(created);
    } catch (caught) {
      setMutationError(caught instanceof Error ? caught.message : `Could not create ${createVerb}.`);
    } finally {
      setCreating(false);
    }
  }

  async function apply() {
    if (selected.length === 0 || submitting) {
      return;
    }
    setSubmitting(true);
    setMutationError(null);
    try {
      await onApply(selected.map((item) => item.id));
      onClose();
    } catch (caught) {
      setMutationError(caught instanceof Error ? caught.message : 'Assignment failed.');
    } finally {
      setSubmitting(false);
    }
  }

  function selectActiveOption() {
    if (!showSuggestions || optionCount === 0) {
      return false;
    }
    if (canCreate && activeIndex === matches.length) {
      void create();
      return true;
    }
    const match = matches[activeIndex];
    if (match) {
      addDestination(match);
      return true;
    }
    return false;
  }

  function onKeyDown(event: KeyboardEvent) {
    if (event.key === 'Escape') {
      event.preventDefault();
      onClose();
      return;
    }
    if (event.key === 'ArrowDown' && optionCount > 0) {
      event.preventDefault();
      setActiveIndex((current) => (current + 1) % optionCount);
      return;
    }
    if (event.key === 'ArrowUp' && optionCount > 0) {
      event.preventDefault();
      setActiveIndex((current) => (current - 1 + optionCount) % optionCount);
      return;
    }
    if (event.key === 'Backspace' && query.length === 0 && selected.length > 0) {
      event.preventDefault();
      removeDestination(selected[selected.length - 1]!.id);
      return;
    }
    if (event.key === 'Enter') {
      event.preventDefault();
      if (selectActiveOption()) {
        return;
      }
      if (normalizedQuery.length === 0 && selected.length > 0) {
        void apply();
      }
    }
  }

  return (
    <div
      aria-labelledby={titleId}
      aria-modal="true"
      className="fixed inset-0 z-50 grid place-items-center bg-black/60 p-4"
      onClick={(event) => {
        if (event.target === event.currentTarget) {
          onClose();
        }
      }}
      onKeyDown={onKeyDown}
      role="dialog"
    >
      <div className="grid w-full max-w-md gap-3 rounded-md border border-border bg-surface p-5 text-foreground shadow-lg">
        <h2 className="text-lg font-semibold" id={titleId}>
          {title}
        </h2>
        <div className="relative grid gap-1">
          <div
            className={cn(
              'flex min-h-10 w-full flex-wrap items-center gap-1.5 rounded-md border border-input bg-background px-2 py-1.5 shadow-sm transition-colors',
              'focus-within:border-ring focus-within:ring-2 focus-within:ring-ring/25'
            )}
            onClick={() => inputRef.current?.focus()}
          >
            {selected.map((item) => (
              <Badge className="gap-1 pr-1 font-medium" key={item.id} variant="secondary">
                {item.name}
                <button
                  aria-label={`Remove ${item.name}`}
                  className="rounded-sm px-1 text-muted-foreground hover:text-foreground"
                  onClick={(event) => {
                    event.stopPropagation();
                    removeDestination(item.id);
                  }}
                  type="button"
                >
                  ×
                </button>
              </Badge>
            ))}
            <Input
              aria-activedescendant={
                showSuggestions && optionCount > 0 ? `${listboxId}-option-${activeIndex}` : undefined
              }
              aria-autocomplete="list"
              aria-controls={listboxId}
              aria-expanded={showSuggestions && optionCount > 0}
              aria-label={`Search ${title.toLowerCase()}`}
              className="h-7 min-w-[8rem] flex-1 border-0 bg-transparent px-1 shadow-none focus-visible:border-0 focus-visible:ring-0"
              disabled={submitting}
              onChange={(event) => setQuery(event.target.value)}
              onKeyDown={onKeyDown}
              placeholder={selected.length === 0 ? `Search ${title.toLowerCase()}` : 'Add more…'}
              ref={inputRef}
              role="combobox"
              value={query}
            />
          </div>
          {showSuggestions && (
            <div
              className="absolute top-full z-10 mt-1 max-h-60 w-full overflow-y-auto rounded-md border border-border bg-surface shadow-lg"
              id={listboxId}
              role="listbox"
            >
              {matches.map((item, index) => (
                <button
                  aria-selected={index === activeIndex}
                  className={cn(
                    'flex w-full px-3 py-2 text-left text-sm hover:bg-muted',
                    index === activeIndex && 'bg-muted'
                  )}
                  id={`${listboxId}-option-${index}`}
                  key={item.id}
                  onClick={() => addDestination(item)}
                  role="option"
                  type="button"
                >
                  {item.name}
                </button>
              ))}
              {canCreate && (
                <button
                  aria-selected={activeIndex === matches.length}
                  className={cn(
                    'flex w-full px-3 py-2 text-left text-sm hover:bg-muted',
                    activeIndex === matches.length && 'bg-muted'
                  )}
                  disabled={creating}
                  id={`${listboxId}-option-${matches.length}`}
                  onClick={() => void create()}
                  role="option"
                  type="button"
                >
                  Create {createVerb} “{normalizedQuery}”
                </button>
              )}
              {matches.length === 0 && !canCreate && (
                <p className="p-3 text-sm text-muted-foreground">No matches.</p>
              )}
            </div>
          )}
        </div>
        {loading && <p className="text-sm text-muted-foreground">Loading...</p>}
        {(mutationError || error) && (
          <p className="text-sm text-destructive">{mutationError ?? error}</p>
        )}
        <p className="text-xs text-muted-foreground">Press Enter to apply · Esc to cancel</p>
      </div>
    </div>
  );
}
