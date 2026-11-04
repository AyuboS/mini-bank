import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';

interface CardCarouselProps {
  /** One entry per slide. Keys come from the caller, which knows what a card is. */
  items: { key: string; node: ReactNode }[];
  /** Called when the card in view changes, so the page can follow the selection. */
  onActiveChange?: (index: number) => void;
  ariaLabel?: string;
  className?: string;
}

/**
 * A horizontally swipeable row of cards.
 *
 * The browser does the scrolling — see the note in cards.css for why a
 * transform-based slider is the wrong tool. This component's whole job is the
 * other direction: watching which slide came to rest in view so the dots and the
 * page's selection can follow, and moving the scroll position when somebody uses
 * a dot or an arrow instead of their finger.
 *
 * `IntersectionObserver` rather than a scroll listener: it fires only when a
 * slide actually crosses the threshold, off the main thread, instead of on every
 * one of the hundreds of scroll events a single flick produces.
 */
export function CardCarousel({ items, onActiveChange, ariaLabel = 'Cards', className = '' }: CardCarouselProps) {
  const trackRef = useRef<HTMLDivElement | null>(null);
  const [active, setActive] = useState(0);

  // Kept in a ref so the observer effect does not need the callback in its
  // dependency list; a caller passing an inline arrow would otherwise tear down
  // and rebuild the observer on every render.
  const onActiveChangeRef = useRef(onActiveChange);
  useEffect(() => {
    onActiveChangeRef.current = onActiveChange;
  }, [onActiveChange]);

  useEffect(() => {
    const track = trackRef.current;
    if (!track) return;

    const slides = Array.from(track.querySelectorAll<HTMLElement>('[data-slide-index]'));
    if (slides.length === 0) return;

    const observer = new IntersectionObserver(
      (entries) => {
        // The most visible slide wins. Comparing ratios rather than taking the
        // first intersecting entry matters while two cards are both partly in
        // view mid-swipe, which is most of the time during a scroll.
        const best = entries
          .filter((entry) => entry.isIntersecting)
          .sort((a, b) => b.intersectionRatio - a.intersectionRatio)[0];
        if (!best) return;

        const index = Number((best.target as HTMLElement).dataset['slideIndex']);
        setActive((current) => {
          if (current !== index) onActiveChangeRef.current?.(index);
          return index;
        });
      },
      { root: track, threshold: [0.5, 0.75, 0.95] },
    );

    slides.forEach((slide) => observer.observe(slide));
    return () => observer.disconnect();
  }, [items.length]);

  const scrollTo = useCallback((index: number) => {
    const track = trackRef.current;
    const slide = track?.querySelector<HTMLElement>(`[data-slide-index="${index}"]`);
    if (!track || !slide) return;
    // scrollIntoView would also scroll the page vertically to reach the track;
    // setting scrollLeft moves only the carousel.
    track.scrollTo({
      left: slide.offsetLeft - (track.clientWidth - slide.clientWidth) / 2,
      behavior: 'smooth',
    });
  }, []);

  const clampedActive = Math.min(active, items.length - 1);
  const showChrome = items.length > 1;

  const dots = useMemo(
    () =>
      items.map((item, index) => (
        <button
          key={item.key}
          type="button"
          className="card-carousel__dot-hit"
          onClick={() => scrollTo(index)}
          aria-label={`Show card ${index + 1} of ${items.length}`}
          aria-current={index === clampedActive}
        >
          <span
            className={`card-carousel__dot ${index === clampedActive ? 'card-carousel__dot--active' : ''}`}
          />
        </button>
      )),
    [items, clampedActive, scrollTo],
  );

  return (
    <div className={`card-carousel ${items.length === 1 ? 'card-carousel--single' : ''} ${className}`}>
      <div
        ref={trackRef}
        className="card-carousel__track"
        role="group"
        aria-label={ariaLabel}
        // Keyboard users get arrow keys for free: a scroll container is
        // focusable and the browser scrolls it, provided it can take focus.
        tabIndex={0}
      >
        {items.map((item, index) => (
          <div className="card-carousel__slide" key={item.key} data-slide-index={index}>
            {item.node}
          </div>
        ))}
      </div>

      {showChrome && (
        /*
          A three-column grid, not a flex row with an invisible copy of the
          arrows on the right to balance it. The duplicate reserved real width,
          so at 320px with four cards the row measured 414px and pushed the
          whole page sideways. The grid centres the dots against the container
          rather than against its own contents, and the third column costs
          nothing.
        */
        <div className="card-carousel__chrome">
          <div className="card-carousel__arrows">
            <button
              type="button"
              className="icon-btn icon-btn--sm"
              onClick={() => scrollTo(Math.max(0, clampedActive - 1))}
              disabled={clampedActive === 0}
              aria-label="Previous card"
            >
              <ChevronLeft aria-hidden="true" />
            </button>
            <button
              type="button"
              className="icon-btn icon-btn--sm"
              onClick={() => scrollTo(Math.min(items.length - 1, clampedActive + 1))}
              disabled={clampedActive === items.length - 1}
              aria-label="Next card"
            >
              <ChevronRight aria-hidden="true" />
            </button>
          </div>

          <div className="card-carousel__dots">{dots}</div>
        </div>
      )}
    </div>
  );
}
