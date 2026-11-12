interface LogoProps {
  size?: number;
  /**
   * Renders the tile and the letter in `currentColor` and its inverse, for a
   * one-colour context: a print stylesheet, a favicon, an embossed card face.
   */
  mono?: boolean;
}

/**
 * The MBank mark.
 *
 * A filled monogram M on a rounded square. That is the whole idea, and the
 * restraint is the point — a mark that has to survive at 20px in a sidebar, at
 * 16px in a browser tab and in one colour on the face of a card cannot afford a
 * gradient, a stroke weight or an inner shadow.
 *
 * What makes it MBank's rather than anyone's: the letter is deliberately wide
 * and squat, its legs are vertical rather than splayed, and the apex of the V is
 * cut flat instead of coming to a point. Together those read as something
 * engineered rather than typed — closer to a stamped mark than to a font.
 *
 * Drawn as a filled path rather than a stroked one. A stroke scales its weight
 * with the viewBox and thins out visibly under 24px; a filled outline holds its
 * proportions at any size, which is the difference between a logo and a picture
 * of a logo.
 */
export function Logo({ size = 34, mono = false }: LogoProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 40 40"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      role="img"
      aria-label="MBank"
    >
      <rect width="40" height="40" rx="8" fill={mono ? 'currentColor' : '#0B6B43'} />
      <path
        d="M9 31V10h6.2l4.8 8.6L24.8 10H31v21h-5.1V19.3l-4.2 7.2h-3.4l-4.2-7.2V31H9Z"
        fill={mono ? 'var(--surface, #fff)' : '#fff'}
      />
    </svg>
  );
}
