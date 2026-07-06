import { X } from 'lucide-react';
import { Button } from '@/components/ui/button';

export type ToastMessage = {
  id: string;
  title: string;
  description?: string;
  variant: 'error';
};

type ToastViewportProps = {
  onDismiss: (id: string) => void;
  toasts: ToastMessage[];
};

export function ToastViewport({ onDismiss, toasts }: ToastViewportProps) {
  if (toasts.length === 0) {
    return null;
  }

  return (
    <div aria-live="assertive" className="fixed right-4 top-4 z-50 grid w-[min(24rem,calc(100vw-2rem))] gap-3">
      {toasts.map((toast) => (
        <div
          className="rounded-md border border-zinc-700 bg-surface p-4 text-foreground shadow-lg"
          key={toast.id}
          role="alert"
        >
          <div className="flex items-start justify-between gap-3">
            <div className="grid gap-1">
              <p className="text-sm font-semibold">{toast.title}</p>
              {toast.description && <p className="text-sm text-muted-foreground">{toast.description}</p>}
            </div>
            <Button aria-label="Dismiss notification" onClick={() => onDismiss(toast.id)} size="icon" type="button" variant="ghost">
              <X className="h-4 w-4" aria-hidden />
            </Button>
          </div>
        </div>
      ))}
    </div>
  );
}
