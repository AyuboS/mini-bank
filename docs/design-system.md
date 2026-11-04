# The design system

How the interface is built, and why. The short version: MBank is a financial operating system
floating inside a **living glass environment**. Layered translucent materials over a slow, dark
atmosphere; hierarchy carried by how thick a pane is rather than by how loud its colour is.

---

## 0. What changed, and why

This is the third design of this product, and each one was a reaction to a real fault in the one
before it.

| Version | What it was | Why it was replaced |
|---|---|---|
| 1 — Glass | Translucent panels over a drifting aurora, three blur strengths | Every surface was equally quiet. No element was allowed to be louder than another, so nothing led the eye. It read as soulless. |
| 2 — Paper and ink | Opaque sheets on a bone canvas, forest and lime, display type | Correct, legible, and generic. It looked like a well-made dashboard for anything — a CRM, an analytics tool, a bank. Nothing about it said *money*. |
| 3 — Liquid glass | Layered materials, four thicknesses, dark-first | Current. |

The mistake in version 1 was **not enough hierarchy**. The mistake in version 2 was reading that as
"glass was wrong" instead of "flat glass was wrong". A material system needs *thickness* to be a
hierarchy, and version 1 had blur without thickness.

So version 3 keeps the glass and gives it structure: four named materials, each a different distance
from the viewer, with the amount of light a pane catches and the shadow it casts both derived from
that distance. A hero surface is not a regular surface with a brighter border — it is a thicker piece
of glass, and every property follows from that one fact.

**It is not a copy of anyone's system.** The iOS 26 era proved that layered translucency can carry a
whole interface; that is the idea taken. The palette, the material recipe, the four thicknesses, the
card designs, the motion curves and the component set are MBank's own.

---

## 1. The premise

> A bank that feels like an instrument, not a form.

Four things carry it:

| Ingredient | Implementation |
|---|---|
| **Depth** | Four materials at four thicknesses. The one a screen is about is the thickest. |
| **Light** | Every pane has a lit top edge and a shadowed bottom edge, because light comes from above. |
| **Atmosphere** | Four very large, very slow colour fields behind everything, so the room is never flat. |
| **Restraint** | One accent gradient. No neon, no glow for its own sake, no animation anybody notices. |

---

## 2. The room

Everything sits inside a fixed backdrop (`RoomBackdrop`, `styles/room.css`):

- a near-black base (`#06070C`) — blue-black rather than neutral, so the glass has something cool to
  refract;
- **four light fields**, 60–90vw across, blurred to 90–120px, drifting on 28–46 second cycles that
  are deliberately coprime so the four never return to the same arrangement;
- a **grain pass**, because a flat fill across a 27" display bands visibly and reads as unfinished
  CSS;
- a **vignette**, so the corners fall away and the content sits in the lit centre.

The animation is slow enough that nobody consciously notices it, which is the whole point — the room
should feel alive without ever asking to be watched. Under `prefers-reduced-motion` the fields stop
where they are; the composition still works because it was never the motion doing the work.

---

## 3. The material system

Four materials, in `styles/surfaces.css`. **This is the hierarchy** — a screen is organised by which
material each surface uses, not by colour.

| Material | Class | Blur | Used for |
|---|---|---|---|
| Ultra thin | `.mat-ultra` | 20px | Navigation, top bars, filter bars, tab tracks. Present, not noticed. |
| Regular | `.mat` | 30px | Cards, list panels, forms, statistics. The workhorse. |
| Thick | `.mat-thick` | 44px | Dialogs, sheets, the command palette. Lifted above the page. |
| Hero | `.mat-hero` | 56px | One per screen: the balance, the security status. |

Each pane is composed of ten layers, all on one element — no wrapper nests:

1. **body** — a translucent fill whose opacity rises with thickness;
2. **backdrop blur** — the room, softened;
3. **saturation** — 180%, so colour passing through glass is enriched rather than greyed;
4. **refraction** — a faint gradient across the pane, so light bends as it passes through;
5. **sheen** — a broad highlight from the top-left, where the room's light is;
6. **top edge** — a 1px lit rim;
7. **bottom edge** — a 1px shadowed rim;
8. **ambient shadow** — soft and wide, the pane's distance from the page;
9. **contact shadow** — tight and dark, where it nearly touches;
10. **grain** — the same texture as the room, so a pane belongs to it.

There is also `.mat-well`: the same system inverted — dark at the top, light at the bottom — which is
what makes an input read as a hollow cut into the glass rather than a pane laid on it.

### Dynamic and adaptive

- **Hover** lifts a pane 1px, brightens the fill by one step and firms the edge — three changes
  together read as a physical response; changing only the colour reads as a state swap.
- **Press** compresses it and pulls the shadow in.
- **Pointer tracking** (opt-in via `<LiquidGlass track>`) moves the sheen with the cursor. Two custom
  property writes per frame, mouse only — a tracked highlight on a touch device leaves a bright spot
  where the finger last was.
- **Modals** dim *and* blur the room behind them. Raising the foreground without pushing the
  background back leaves the two competing.

---

## 4. Colour

Dark is the product; light is a concession to a bright room and is chosen explicitly.

| Token | Dark | Role |
|---|---|---|
| `--room` | `#06070C` | The base everything floats on |
| `--brand` | `#4FE3E0` | Cyan. The accent, in one gradient with violet |
| `--brand-gradient` | cyan → blue → violet | Primary buttons, progress, the mark |
| `--ink` | `#F2F5FB` | Headings, balances — 16.6:1 |
| `--ink-2` | 80% white | Secondary copy — 10.2:1 |
| `--ink-3` | 64% white | Labels and metadata — 5.6:1 |
| `--ink-4` | 42% white | Placeholders and disabled — 3.1:1 |
| `--positive` | `#3FE0A0` | Money in |
| `--danger` | `#FF6B6B` | Refusals and destructive actions |

The ink opacities are **measured, not chosen by eye**. An earlier `--ink-3` at 50% measured 4.21:1
against the darkest glass — close enough to look fine, and far enough to fail AA. Every one of them
is now checked against the worst-case composite background, which is what allows `--ink-3` to carry
real text rather than being decorative.

Money never uses the brand colour. Incoming money is `--positive`, a green distinct enough from the
cyan accent that an amount is never mistaken for a call to action.

---

## 5. Typography

`Inter` throughout, with `Geist` for display. Both are variable, subset to Latin, and preloaded.

| Token | Size | Used for |
|---|---|---|
| `--text-hero` | clamp(2.75rem, 6vw, 4rem) | The balance, and nothing else |
| `--text-display` | clamp(2rem, 4vw, 2.75rem) | Page titles |
| `--text-title` | 1.375rem | Section headings |
| `--text-body` | 0.9375rem | Everything read |
| `--text-small` | 0.8125rem | Metadata |
| `--text-label` | 0.6875rem | Uppercase labels |

Every figure uses `font-variant-numeric: tabular-nums`. A balance counting up from zero would
otherwise jitter horizontally as the digit widths change, and a column of amounts would not align.

---

## 6. Motion

| Token | Duration | Used for |
|---|---|---|
| `--dur-micro` | 140ms | Button presses, hovers |
| `--dur-control` | 220ms | Toggles, tabs, tooltips |
| `--dur-panel` | 320ms | Sheets, dialogs |
| `--dur-page` | 380ms | Route transitions |

Two curves. `--ease-glass` (`cubic-bezier(0.32, 0.72, 0, 1)`) for almost everything: fast at the
start, settling at the end, the way a physical object with mass moves. `--ease-spring` for the two
places where a small overshoot is right — a sheet arriving, a success mark landing.

Nothing bounces on a money figure. This is somebody's balance.

`prefers-reduced-motion` stops the room, the card tilt, the balance count-up, the shimmer and the
page transition. Every one of them is decoration; the interface is complete without them.

---

## 7. Cards

Seven materials, at exactly **ID-1 proportions (1.586:1)** at every size. The aspect ratio is the
single strongest cue that the thing on screen is a payment card, and getting it wrong by a few
percent is noticeable without being nameable.

| Design | Material |
|---|---|
| **Aurora** | Dark glass with a conic ribbon of light turning slowly through it |
| **Midnight** | Brushed black metal, grain at the angle metal is actually brushed |
| **Ocean** | Deep blue resin with two drifting caustic patterns |
| **Sunset** | Violet through amber with a warm horizon band |
| **Crystal** | Genuinely translucent — the room shows through it, and its ink inverts to dark |
| **Carbon** | Woven twill, matte, with a much weaker sheen than the rest |
| **Holographic** | An iridescent film whose colour shifts with the pointer's angle |

All seven share five layers — body, depth, sheen, grain, edge — which is what stops seven skins from
looking like seven products. On desktop the card tilts up to 3° toward the cursor, driven by two CSS
custom properties written directly to the node and throttled to one write per frame; putting pointer
moves through React state would re-render the subtree at 120Hz to change one transform. On touch the
tilt is disabled in CSS, because a transform set by a tap has nothing to reset it.

The designs are MBank's own. No issuer's card art is reproduced, and the network marks are generic
geometric forms rather than any scheme's logo.

---

## 8. Layout

**Desktop.** A floating glass sidebar, inset from three edges, with the room visible around it — a
docked sidebar is furniture, a floating one is a control surface. Content is capped at 1200px.

**Tablet (768–1100px).** The sidebar narrows to icons. Labels stay in the DOM at `font-size: 0`, so
the accessible name survives.

**Phone (<768px).** The sidebar is replaced by a floating bar at the bottom, inset and above the home
indicator. Five destinations is a ceiling, not a target: past that the labels drop below legibility
and the targets fall under 44px on a 375px screen. At 320px the labels are dropped and the icons
carry the meaning.

Dialogs become bottom sheets below 768px — one component, because a dialog and a sheet are the same
thing seen at two viewport widths.

---

## 9. Accessibility

Not a pass at the end; the constraints that shaped the components.

- **Contrast.** Every ink token measured against the worst-case composite background. See §4.
- **Focus.** A two-layer ring — a dark halo then a cyan ring — so it is visible on glass of any
  thickness. Never removed, only replaced.
- **Targets.** 44px minimum on touch. A 6px carousel dot has a 24px hit area around it.
- **Real controls.** The switches are checkboxes, the tabs are a tablist with a roving tabindex, the
  FAQ is `<details>`, the select is native. A div with a click handler looks identical and is
  invisible to a screen reader, unreachable by keyboard, and impossible to operate by voice.
- **Dialogs.** Focus trapped, escape closes, scroll locked, focus restored to the control that opened
  it.
- **Announcements.** Errors are `role="alert"`. Loading regions are `aria-busy`. A spinner replacing
  a button label sets `aria-busy` on the button, because a spinner alone is silence.
- **Skip link.** First in the tab order, the only way past a long navigation.
- **Reduced motion.** Honoured in CSS for decoration and in JavaScript for anything JS drives, so the
  effect is never started rather than started and run at zero duration.

---

## 10. Rules

1. **Tokens only.** No component invents a colour, a radius, a duration or a shadow.
2. **Material carries hierarchy.** If something needs to feel more important, it gets thicker glass —
   not a brighter border.
3. **One accent.** The cyan-violet gradient. Money keeps its own greens and reds.
4. **Motion is felt, not watched.** If somebody notices an animation, it is too long.
5. **Every state is designed.** Loading, empty, error and offline are screens, not afterthoughts.
6. **Nothing is faked.** A control that appears to do something does it, server-side.
