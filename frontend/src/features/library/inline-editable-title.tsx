import { Pencil } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { cn } from '@/lib/utils';

type InlineEditableTitleProps = {
  value: string;
  onSave: (next: string) => Promise<void> | void;
  className?: string;
  inputClassName?: string;
  size?: 'lg' | 'md' | 'sm';
  'aria-label'?: string;
};

export function InlineEditableTitle({
  value,
  onSave,
  className,
  inputClassName,
  size = 'lg',
  'aria-label': ariaLabel = 'Name'
}: InlineEditableTitleProps) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(value);
  const [saving, setSaving] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const cancelingRef = useRef(false);

  useEffect(() => {
    if (!editing) {
      setDraft(value);
    }
  }, [editing, value]);

  useEffect(() => {
    if (editing) {
      cancelingRef.current = false;
      inputRef.current?.focus();
      inputRef.current?.select();
    }
  }, [editing]);

  async function commit() {
    if (cancelingRef.current) {
      cancelingRef.current = false;
      return;
    }
    const trimmed = draft.trim();
    if (!trimmed || trimmed === value) {
      setDraft(value);
      setEditing(false);
      return;
    }
    setSaving(true);
    try {
      await onSave(trimmed);
      setEditing(false);
    } catch {
      setDraft(value);
      setEditing(false);
    } finally {
      setSaving(false);
    }
  }

  function cancel() {
    cancelingRef.current = true;
    setDraft(value);
    setEditing(false);
  }

  const titleClasses =
    size === 'sm'
      ? 'truncate text-sm text-muted-foreground'
      : size === 'md'
        ? 'truncate text-lg font-semibold text-foreground'
        : 'truncate text-2xl font-semibold text-foreground';
  const fieldClasses =
    size === 'sm'
      ? 'h-7 max-w-xs text-sm'
      : size === 'md'
        ? 'h-8 max-w-md text-lg font-semibold'
        : 'h-9 max-w-md text-2xl font-semibold';
  const pencilClass = size === 'sm' ? 'h-3 w-3' : size === 'md' ? 'h-3.5 w-3.5' : 'h-4 w-4';

  if (editing) {
    return (
      <Input
        aria-label={ariaLabel}
        className={cn(fieldClasses, inputClassName)}
        disabled={saving}
        onBlur={() => void commit()}
        onChange={(event) => setDraft(event.target.value)}
        onKeyDown={(event) => {
          if (event.key === 'Enter') {
            event.preventDefault();
            void commit();
          }
          if (event.key === 'Escape') {
            event.preventDefault();
            cancel();
          }
        }}
        ref={inputRef}
        value={draft}
      />
    );
  }

  return (
    <div className="group flex min-w-0 items-center">
      {size === 'sm' ? (
        <p className={cn(titleClasses, className)}>{value}</p>
      ) : size === 'md' ? (
        <h3 className={cn(titleClasses, className)}>{value}</h3>
      ) : (
        <h1 className={cn(titleClasses, className)}>{value}</h1>
      )}
      <Button
        aria-label={`Rename ${ariaLabel.toLowerCase()}`}
        className={cn(
          'shrink-0 overflow-hidden p-0 text-muted-foreground opacity-0 transition-[width,opacity,margin] hover:text-foreground',
          'ml-0 w-0 group-hover:ml-1 group-hover:opacity-100 focus-visible:ml-1 focus-visible:opacity-100 group-focus-within:ml-1 group-focus-within:opacity-100',
          size === 'sm' || size === 'md'
            ? 'h-6 group-hover:w-6 focus-visible:w-6 group-focus-within:w-6'
            : 'group-hover:w-10 focus-visible:w-10 group-focus-within:w-10'
        )}
        onClick={() => setEditing(true)}
        size="icon"
        type="button"
        variant="ghost"
      >
        <Pencil className={pencilClass} aria-hidden />
      </Button>
    </div>
  );
}
