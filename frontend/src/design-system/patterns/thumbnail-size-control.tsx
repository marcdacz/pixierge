import { Maximize2, SlidersHorizontal } from 'lucide-react';
import { cn } from '@/lib/utils';

export type ThumbnailSizeControlProps = {
  id?: string;
  label?: string;
  value?: number;
  defaultValue?: number;
  min?: number;
  max?: number;
  step?: number;
  valueText?: string;
  onChange?: (value: number) => void;
  onFullscreen?: () => void;
  className?: string;
};

export function ThumbnailSizeControl({
  className,
  defaultValue = 48,
  id = 'thumbnail-size',
  label = 'Zoom media grid',
  max = 100,
  min = 0,
  onChange,
  onFullscreen,
  step = 1,
  value,
  valueText
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
        aria-valuemax={max}
        aria-valuemin={min}
        aria-valuenow={value}
        aria-valuetext={valueText}
        id={id}
        max={max}
        min={min}
        onChange={(event) => onChange?.(event.currentTarget.valueAsNumber)}
        step={step}
        type="range"
        value={value}
      />
      {onFullscreen ? (
        <button
          aria-label="Fullscreen"
          className="grid h-8 w-8 place-items-center rounded-md transition-colors hover:bg-surface-hover hover:text-content focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-focus"
          onClick={onFullscreen}
          type="button"
        >
          <Maximize2 aria-hidden="true" className="h-4 w-4" />
        </button>
      ) : null}
    </div>
  );
}
