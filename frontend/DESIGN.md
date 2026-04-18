# rotrack — Design System

**Concept name:** "Tangerine Studio"
**One-liner:** A warm, paper-white canvas punctuated by Asimov-orange pops, chunky display type, and tasteful playful bubbles. Minimalism with personality.

This document is the source of truth for all future rotrack UI work.
Read it before generating components, pages, or marketing material so the
aesthetic stays coherent across the app.

---

## 1. Design tenets

1. **Honest, not harsh.** rotrack is a procrastination-study tool. The UI is
   playful enough to feel kind, minimal enough to feel serious.
2. **Orange is a gesture, not a wall.** Dominant cream/white canvas, with
   orange used as a pointer — highlights, CTAs, single accent words.
3. **Shapes do the decoration.** No photographic illustrations or stock art.
   Composition is carried by circles, orbs, soft dashed rings, and chunky
   typography.
4. **Everything has one display moment.** Per section, pick one element to
   carry weight (a huge headline, a stripe panel, a rotating card). Never
   two loud things at the same level.
5. **Whitespace is a feature.** Cards breathe; sections have generous
   vertical rhythm (min ~`py-28` on desktop).

---

## 2. Color palette

Expose all colors as CSS variables in `globals.css` and reference them
via the `--rt-` prefix (e.g. `var(--rt-orange)`) or through Tailwind
arbitrary values such as `text-[var(--rt-ink)]` and `bg-[var(--rt-cream)]`.

| Token               | Hex        | Purpose                                  |
| ------------------- | ---------- | ---------------------------------------- |
| `--rt-cream`        | `#F5F2EC`  | Primary page background (warm off-white) |
| `--rt-cream-soft`   | `#EFEBE3`  | Slightly deeper panels                   |
| `--rt-paper`        | `#FFFFFF`  | Crisp cards, marquee strip, nav pill     |
| `--rt-ink`          | `#0A0A0A`  | Primary text, dark cards, hard CTAs      |
| `--rt-ink-soft`     | `#1F1F1F`  | Secondary text, headline bodies          |
| `--rt-ink-muted`    | `#6B6B6B`  | Tertiary text, meta                      |
| `--rt-orange`       | `#EC6B0E`  | Primary accent (CS:GO Asimov orange)     |
| `--rt-orange-deep`  | `#D4580A`  | Hover / pressed orange                   |
| `--rt-orange-soft`  | `#F9C99E`  | Tinted bubbles, soft fills               |
| `--rt-line`         | `#E5E1D8`  | Hairlines, card borders                  |

**Distribution rule of thumb (60 / 30 / 10):**
- **60%** cream + paper (canvas)
- **30%** ink (text, dark cards, one dark hero)
- **10%** orange (accents, CTAs, single highlighted words)

**Never do:** purple gradients, pastel rainbows, multi-hue charts. If a
second accent is needed, use ink — not a new color.

---

## 3. Typography

Two typefaces. Both already live in `/public/fonts/`.

| Role     | Font              | Use                                              |
| -------- | ----------------- | ------------------------------------------------ |
| Display  | **Migha Black**   | Wordmark, all hero & section headlines, big numbers. Very tight tracking (`-0.02em`). |
| Body     | **Figtree**       | All paragraphs, nav, chips, buttons, meta text.  |
| Mono     | **Digital-7**     | LED/clock readouts only. Never for body.         |

Use the `font-display` utility (defined in `globals.css`) for display type.
Default `body` uses Figtree.

**Scale (display, clamp-driven, mobile → desktop):**
- Hero H1: `clamp(3.2rem, 8.5vw, 8rem)` / `leading-[0.9]`
- Section H2: `clamp(2.4rem, 5.5vw, 4.8rem)` / `leading-[0.95]`
- Card H3: `1.6rem` – `3rem` display, `1.25` leading
- Body: `1rem` – `1.15rem`, `1.6` leading
- Eyebrow: `0.8rem`, `uppercase`, `tracking-[0.2em]`, color `--rt-orange`

Headlines like lowercase setting. Small decorative flourishes allowed
(single italicized word, one underline swoosh) — never both in one
headline.

---

## 4. Shapes, bubbles & motion

Bubbles are the signature motif. They ride in three flavors:

1. **Solid orange** (`--rt-orange`) — loud, use sparingly, one per section.
2. **Soft orange** (`--rt-orange-soft`) — ambient fill, safe to use larger.
3. **Ink outline** (`border-2 border-[var(--rt-ink)] bg-transparent`) — the
   thinking-man's bubble. Good for balancing an orange one.

Placement: absolute-positioned inside a section with `overflow-x-clip` on
the parent. Sizes range `h-4 w-4` (punctuation) to `h-56 w-56` (ambient).

**Motion utilities (see `globals.css`):**
- `rt-float` — gentle Y bob, 7s
- `rt-float-slow` — drift + scale, 11s
- `rt-rise` — 28px rise + fade, 900ms, used with `animation-delay` for
  staggered hero reveal
- `rt-marquee` — 40s linear horizontal loop
- `rt-spin-slow` — 28s rotation, for orbital accents

**Rules:** at most 3–4 animated elements on screen at once. No bouncing,
no rubber-banding, no easing that screams. Everything ease-in-out.

---

## 5. Components

### Nav
Fixed, floating pill. `rounded-full`, paper bg at 80% alpha +
`backdrop-blur-md`, hairline border. Logo on the left; minimal text links
(orange on hover); ink-colored primary CTA button on the right.

### Buttons
- **Primary:** `rounded-full`, orange bg, cream text,
  `shadow-[0_10px_30px_-10px_rgba(236,107,14,0.6)]`. Hover → darker
  orange + `-translate-y-0.5`.
- **Secondary (on light):** `rounded-full`, `border border-[var(--rt-ink)]`,
  transparent. Hover → ink bg, cream text.
- **Secondary (on dark):** `rounded-full`, `border border-[var(--rt-cream)]/30`.
  Hover → cream bg, ink text.

### Cards
`rounded-[28px]` or larger (28–40px). Either:
- **Paper:** `bg-[var(--rt-paper)]` + `border border-[var(--rt-line)]`.
- **Ink:** `bg-[var(--rt-ink)] text-[var(--rt-cream)]` for the *one*
  inverted card per section.

Decorative behaviour: every major card has a large off-screen orange orb
that scales on hover.

### Chips / tags
`rounded-full`, `px-2.5 py-1`, `text-[0.75rem]`, hairline border. Used in
bucket cards to show examples. Never more than 4 per card.

### Dividers
1px `var(--rt-line)`. Used as section endings and column separators in
the "how it works" grid.

---

## 6. Layout

- Max container: `max-w-[1400px]` with `px-6 md:px-10`.
- Section rhythm: `py-28 md:py-36` for marketing sections.
- Hero uses a 12-column split (copy 7 / graphic 5 on `lg:`).
- "How it works" uses an equal 3-column grid with a top hairline border
  and vertical hairline separators between columns.
- Prefer **asymmetry**: tilt cards (-4° / +3° / +5°), let graphics bleed
  past their container via negative margins, and use overlapping floating
  cards over the webp stripe panel instead of centered compositions.

---

## 7. The orange-stripe webp

File: `/public/efecto-2026-02-21T04-51-28.webp`.
It is a **design element**, not a wallpaper. Two approved uses:

1. **Hero graphic panel.** Tilted rounded card on the right side of the
   hero with floating info chips layered on top.
2. **Final CTA accent.** Masked/faded into a dark card from the right
   edge using a CSS `mask-image: linear-gradient(to left, black 40%, transparent 100%)`.

Never place body text directly over the stripes. Never use it as a full
page background.

---

## 8. Imagery & iconography

- No stock photography. Ever.
- Icons: lucide-react if needed, stroked at `1.6`, small.
- Inline SVG is preferred for one-off marks (the underline swoosh, the
  logo cross, the arrow-right in the CTA).
- The logo mark is a composition primitive: orange disk + cream inner
  disk + ink plus-sign. Reuse it as a favicon motif.

---

## 9. Voice & microcopy

- Lowercase headlines with one confident full-stop per line.
- Short. Honest. A little dry. Never motivational-poster.
- Example good copy: *"stop guessing where your hours go."*,
  *"three taps. one honest picture."*, *"every minute lands in one of
  three places."*
- Label the three buckets consistently: **rot**, **stagnant**, **work**.
  Capitalize only as section headings.

---

## 10. Do / Don't cheat-sheet

**Do**
- Use orange as a single word / single accent per headline.
- Let whitespace carry sections.
- Stagger hero reveals with `rt-rise` + `animationDelay`.
- Tilt decorative cards by 3–5 degrees.
- Use tabular-nums for any numbers.

**Don't**
- Put text over the stripe webp.
- Introduce new accent colors (blue, green, purple).
- Use Inter, Roboto, or system-ui for display.
- Stack more than one "loud" element per section.
- Use shadows heavier than `0_40px_80px_-30px_rgba(10,10,10,0.3)`.

---

_Last updated: 2026-04-17_
