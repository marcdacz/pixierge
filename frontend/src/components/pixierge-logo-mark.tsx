import logoMarkUrl from '@/assets/pixierge-logo-mark.png';
import { cn } from '@/lib/utils';

type PixiergeLogoMarkProps = {
  className?: string;
  showWordmark?: boolean;
};

export function PixiergeLogoMark({ className, showWordmark = false }: PixiergeLogoMarkProps) {
  return (
    <div className={cn('flex min-w-0 items-center gap-3', className)}>
      <img alt="" aria-hidden="true" className="h-10 w-auto object-contain" src={logoMarkUrl} />
      {showWordmark ? <span className="text-lg font-semibold tracking-normal text-content">PIXIERGE</span> : null}
    </div>
  );
}
