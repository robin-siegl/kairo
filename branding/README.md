# Kairo branding

Kairo uses a rounded ribbon K with two open counters. The mark is drawn from a single master path in [build_branding.py](../scripts/build_branding.py).

## Colors

| Use | Color |
|---|---|
| Brand blue and light-mode primary | `#3367DB` |
| Dark-mode primary | `#ADC6FF` |
| White mark | `#FFFFFF` |
| Dark wordmark | `#182238` |

The tagline is “Recurring payments, in view.” App screens use the Android system font. The SVG wordmark uses the system sans-serif stack.

## Assets

| Asset | Files |
|---|---|
| Blue mark | [SVG](logo-blue.svg), [PNG](logo-blue.png) |
| Black mark | [SVG](logo-black.svg), [PNG](logo-black.png) |
| White mark | [SVG](logo-white.svg), [PNG](logo-white.png) |
| Launcher preview | [SVG](app-icon.svg), [PNG](app-icon.png) |
| Circular launcher preview | [SVG](app-icon-circle.svg), [PNG](app-icon-circle.png) |
| Wordmark on light backgrounds | [SVG](wordmark-light.svg) |
| Wordmark on dark backgrounds | [SVG](wordmark-dark.svg) |

SVG masters and PNG exports have transparent backgrounds. Launcher previews include the blue background and their preview mask. Android uses separate foreground and background layers so the launcher can apply its own shape. The same foreground supplies the themed monochrome icon, splash mark, and notification symbol.

## Usage

Keep at least one stem-width of clear space around the standalone mark. Use the mark alone below a 120 px wordmark width. At small sizes, keep both counters visible and use a solid high-contrast foreground. Preserve the proportions and use the supplied colors.

## Launch animation

On Android 12 and newer, the system splash shows the white K on brand blue. When the app is ready to draw, the mark grows gently by 12 percent while the launch surface fades away over 320 ms. The transition follows Android's animator duration setting and skips motion when animations are disabled. Returning to an already running activity uses Android's normal transition. Android 10 and 11 use the blue launch background.

## Rebuild

Run `python3 scripts/build_branding.py` from the repository root with Python 3 and Inkscape installed. This regenerates the SVG kit, PNG exports, and Android vector drawables from the same path. Review launcher previews after changing the master.

Kairo's original branding assets are included under the repository's MIT license.
