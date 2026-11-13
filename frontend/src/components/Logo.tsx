interface LogoProps {
  size?: number;
  /**
   * Renders the tile and the letter in `currentColor` and its inverse, for a
   * one-colour context: a print stylesheet, a favicon, an embossed card face.
   */
  mono?: boolean;
}

/** The MBank mark. */
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
