import { useLayoutEffect, useState } from 'react';

export function useMeasuredWidth<TElement extends HTMLElement>() {
  const [element, setElement] = useState<TElement | null>(null);
  const [width, setWidth] = useState(0);

  useLayoutEffect(() => {
    if (!element) {
      return;
    }

    function updateWidth() {
      setWidth(element?.getBoundingClientRect().width ?? 0);
    }

    const frame = window.requestAnimationFrame(updateWidth);

    if (typeof ResizeObserver === 'undefined') {
      return () => window.cancelAnimationFrame(frame);
    }

    const observer = new ResizeObserver((entries) => {
      const nextWidth = entries[0]?.contentRect.width ?? element.getBoundingClientRect().width;
      setWidth(nextWidth);
    });
    observer.observe(element);

    return () => {
      window.cancelAnimationFrame(frame);
      observer.disconnect();
    };
  }, [element]);

  return [setElement, width] as const;
}
