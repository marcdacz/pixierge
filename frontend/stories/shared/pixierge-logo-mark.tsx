import { cn } from '../../src/lib/utils';
import logoMarkUrl from '../../src/assets/pixierge-logo-mark.png';

type PixiergeLogoMarkProps = {
  className?: string;
  showWordmark?: boolean;
};

export function PixiergeLogoMark({ className, showWordmark = false }: PixiergeLogoMarkProps) {
  return (
    <div className={cn('flex items-center gap-5', className)}>
      <img alt="" aria-hidden="true" className="h-12 w-auto object-contain" src={logoMarkUrl} />
      {showWordmark ? <span className="text-xl font-semibold tracking-normal text-content">PIXIERGE</span> : null}
    </div>
  );
}
