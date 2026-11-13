import type { ElementType, HTMLAttributes, ReactNode } from 'react';

/** What kind of rectangle this is. */
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

/** A rectangle with a background, a hairline and a radius. */
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
