import { Maximize2, SlidersHorizontal } from 'lucide-react';
import { cn } from '@/lib/utils';

export type ThumbnailSizeControlProps = {
  id?: string;
  label?: string;
  value?: number;
  defaultValue?: number;
  onChange?: (value: number) => void;
  onFullscreen?: () => void;
  className?: string;
};

export function ThumbnailSizeControl({
  className,
  defaultValue = 48,
  id = 'thumbnail-size',
  label = 'Zoom media grid',
  onChange,
  onFullscreen,
  value
}: ThumbnailSizeControlProps) {
  return (
    <div
      className={cn(
        'hidden h-12 items-center gap-3 rounded-lg border border-border bg-surface-raised px-3 text-content-muted md:flex',
        className
      )}
    >
      <SlidersHorizontal aria-hidden="true" className="h-5 w-5" />
      <label className="sr-only" htmlFor={id}>
        {label}
      </label>
      <input
        className="h-1 w-28 accent-info"
        defaultValue={value === undefined ? defaultValue : undefined}
        id={id}
        max="100"
        min="0"
        onChange={(event) => onChange?.(event.currentTarget.valueAsNumber)}
        type="range"
        value={value}
      />
      <button
        aria-label="Fullscreen"
        className="grid h-8 w-8 place-items-center rounded-md transition-colors hover:bg-surface-hover hover:text-content focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
        onClick={onFullscreen}
        type="button"
      >
        <Maximize2 aria-hidden="true" className="h-4 w-4" />
      </button>
    </div>
  );
}
