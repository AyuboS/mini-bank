import type { ElementType, HTMLAttributes, ReactNode } from 'react';

/**
 * What kind of rectangle this is. See styles/surfaces.css.
 *
 * `default` for content — cards, panels, list containers, and most of the
 * product. `chrome` for navigation, the one place a blur still earns its cost.
 * `raised` for anything sitting above the page. `hero` for the single block a
 * screen is about, filled with ink so the eye lands on it first.
 */
export type SurfaceTone = 'default' | 'chrome' | 'raised' | 'hero';

const TONE_CLASS: Record<SurfaceTone, string> = {
  default: 'surface',
  chrome: 'surface--chrome',
  raised: 'surface--raised',
  hero: 'surface--hero',
};

interface SurfaceProps extends HTMLAttributes<HTMLElement> {
  tone?: SurfaceTone;
  /** Pointer cursor, a hairline that firms up on hover, and a press response. */
  interactive?: boolean;
  /** Rises 2px on hover. For a surface standing in for an object you can open. */
  lift?: boolean;
  as?: ElementType;
  /** Valid when `as="button"`. Declared rather than widening props to accept anything. */
  type?: 'button' | 'submit' | 'reset';
  disabled?: boolean;
  children?: ReactNode;
}

/**
 * A rectangle with a background, a hairline and a radius.
 *
 * Deliberately thin. The previous version of this component tracked the pointer
 * across the surface at 120Hz to move a highlight, which is the sort of thing
 * that reads as craft in a demo and as jitter in a bank. All this one does now
 * is pick a class; everything visible lives in CSS, so a surface costs one
 * element and no JavaScript.
 */
export function Surface({
  tone = 'default',
  interactive = false,
  lift = false,
  as: Tag = 'div',
  className = '',
  children,
  ...rest
}: SurfaceProps) {
  const classes = [
    TONE_CLASS[tone],
    interactive ? 'surface--interactive' : '',
    lift ? 'surface--lift' : '',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <Tag className={classes} {...rest}>
      {children}
    </Tag>
  );
}
