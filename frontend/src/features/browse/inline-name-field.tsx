import { useEffect, useRef, useState, type KeyboardEvent } from 'react';
import { Input } from '@/components/ui/input';

type InlineNameFieldProps = {
  ariaLabel: string;
  initialValue?: string;
  onCancel: () => void;
  onCommit: (name: string) => Promise<void> | void;
  placeholder?: string;
};

export function InlineNameField({
  ariaLabel,
  initialValue = '',
  onCancel,
  onCommit,
  placeholder
}: InlineNameFieldProps) {
  const [draft, setDraft] = useState(initialValue);
  const [saving, setSaving] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const cancelingRef = useRef(false);
  const committingRef = useRef(false);

  useEffect(() => {
    cancelingRef.current = false;
    committingRef.current = false;
    inputRef.current?.focus();
    inputRef.current?.select();
  }, []);

  async function commit() {
    if (cancelingRef.current || committingRef.current) {
      cancelingRef.current = false;
      return;
    }
    const trimmed = draft.trim();
    if (!trimmed || trimmed === initialValue) {
      onCancel();
      return;
    }
    committingRef.current = true;
    setSaving(true);
    try {
      await onCommit(trimmed);
    } catch {
      onCancel();
    } finally {
      committingRef.current = false;
      setSaving(false);
    }
  }

  function cancel() {
    cancelingRef.current = true;
    onCancel();
  }

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'Enter') {
      event.preventDefault();
      void commit();
    }
    if (event.key === 'Escape') {
      event.preventDefault();
      cancel();
    }
  }

  return (
    <Input
      aria-label={ariaLabel}
      className="h-7 min-w-0 flex-1 px-2 text-sm shadow-none"
      disabled={saving}
      onBlur={() => void commit()}
      onChange={(event) => setDraft(event.target.value)}
      onClick={(event) => event.stopPropagation()}
      onKeyDown={handleKeyDown}
      placeholder={placeholder}
      ref={inputRef}
      value={draft}
    />
  );
}
