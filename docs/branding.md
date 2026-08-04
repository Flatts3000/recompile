# Branding

Two assets, made two different ways, and neither of them is AI output. That last part is a **ModJam
2026 contest rule** - "AI-generated project avatars and gallery images are not allowed" - so it is a
constraint on this file rather than a preference.

| Asset | What it is | How it is made |
| --- | --- | --- |
| `branding/logo.png` | 400x400 square. The **CurseForge project avatar**, and the in-jar mod-list icon (`src/main/resources/logo.png` is written from the same run) | Artwork drawn from geometry by `branding/compose_logo.py`, with the wordmark composited over it |
| `branding/wordmark_single_row.png` | 2048x352 transparent PNG. The full name, for the gallery, banners and any wide surface | Rendered in the Minecraft Title Generator, settings below |

The gallery is real in-game screenshots and needs no tooling.

## The avatar

`python branding/compose_logo.py` writes all of it. A green sprout rising out of a pile of junk - the
mod's pitch in one image - with `REC` stamped across the pile.

**It used to pixelate a painterly AI render and that had to go** (2026-08-04). The artwork is now a sky
ramp, a mound profile with junk scattered over it, a stem, and two leaves drawn as rotated ellipses.
Everything is drawn on the 50x50 low-res grid before the upscale, so every edge lands on a pixel
boundary - the same reason the Puzzle Cube's faces, the Luggage sprites and the pigeon's skin are
procedural rather than generated.

**The avatar carries the full wordmark** (owner, 2026-08-04), composited over the pile after the
upscale rather than stamped onto the low-res grid - it is a 3D render with real shading, and quantising
it to 50x50 cells would destroy the bevel that makes it read as the Minecraft title font.

It was `REC` first, on the reasoning that nine glyphs across a 64px icon is about six pixels a letter.
Worth recording that the prediction was too pessimistic: the copper reads against the dark pile and the
name is still legible in the thumbnail. `compose_logo.py` writes `gen/logo_64.png` on every run so that
cost stays visible instead of being discovered later.

The wordmark also ships on its own, for wide surfaces where a square crop would waste it.

## The wordmark

Tool: the [Minecraft Title Generator](https://ewanhowell.com/plugins/minecraft-title-generator/)
Blockbench plugin by Ewan Howell (renders are free to use). Same tool Sky Frogs and Trashlands used;
`../trashlands/docs/branding.md` is the fuller writeup.

One-click install and open: <https://web.blockbench.net/?plugins=minecraft_title_generator>

Settings used for the shipped wordmark:

| Setting | Value |
| --- | --- |
| Text | One text: `RECOMPILE` |
| Font | **Minecraft Ten** (the authentic vanilla title font) |
| Text Type / Angle | **Top** |
| Text Row | `0` - one row |
| Texture | **Copper** > variant **Copper** |
| Overlay | None |
| Camera | Position camera (the plugin's automatic angle) |
| Resolution | **2k** |
| Antialiasing | on |

Then Render > the preview dialog > **Save render**. Output is 2048x352 on a transparent background.

**Why raw Copper.** Trashlands took Oxidised Copper because weathered teal is what that pack is about,
and the two projects should not read as the same product - Recompile is the engine, Trashlands is the
pack built on it. Raw copper is rusted scrap metal, which is the engine's subject, and its orange sits
against the logo's olive pile rather than competing with the sprout's green.

**One row, not two.** Two rows is inherently about 1.9:1, which is a shape for a square icon. This
wordmark exists for wide surfaces, and `RECOMPILE` is one word - breaking it across rows would invent a
split (`RE` / `COMPILE`) that the name does not have.

**Blockbench shows a one-time Minecraft EULA prompt** the first time a feature pulls in Minecraft
assets (the font and block textures). It is Blockbench's gate, not the plugin's, and acceptance is
stored per browser profile in `localStorage`. A fresh browser profile - including an automated one -
asks again even if you have accepted it before on the same machine.
