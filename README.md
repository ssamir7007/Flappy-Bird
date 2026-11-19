# Flappy Bird (Java Swing)

## Browser version / Vercel

The original Swing game remains available. `web/` contains a standalone HTML,
CSS and JavaScript port suitable for static hosting on Vercel, with the same
physics, difficulty ramp, artwork, wing animation, parallax and sounds. It also
supports touch input and responsive layouts. No dependencies or build step are
required to deploy `web/` (framework preset: Other).

Run `node serve-web.mjs` from this folder to preview at `http://127.0.0.1:4173`.
Run `node web-test.mjs` for browser-engine regression tests. To regenerate the
optimized browser assets from the originals:

```sh
javac FlappyBird.java BuildWebAssets.java
java BuildWebAssets
```

Web high scores use localStorage in the current browser and domain, independently
of the desktop save. Clearing site data removes that record. Sound starts muted;
use the Sound button or M to enable it. Exit shows a farewell screen because
browsers generally prevent pages from closing tabs they did not open.
Switching tabs pauses the simulation until the game is visible again.

Vercel project: `flappy-bird-arcade` in `shadmantx588-3689s-projects`.
Live game: https://flappy-bird-arcade-blond.vercel.app
Production deployment: `dpl_Ee15VgD7u54swSzft1ToDb6hhXG8`.
Deploy only the contents of `web/`; desktop sources and saves are not uploaded.

A simple, playable game with gravity, jumping, randomly placed pipes, collision
detection, scoring, and a best score retained between launches. Includes
custom artwork and sound effects; no external libraries are required. Animation
uses 120 Hz fixed physics steps, animated wing sprites, eased bird tilt, flap squash, idle bobbing,
layered parallax scenery, scrolling ground, a score pulse, and a brief collision flash.

Clouds, distant mountains, and nearby hills scroll left at 8%, 20%, and 45% of
pipe speed, respectively. The ground scrolls at full pipe speed. A stationary
sky gradient anchors the scene. Scenery speeds follow difficulty progression;
motion continues in menus and freezes at game over. Returning to the menu or
restarting preserves layer positions for a smooth transition. Alternating
mirrored tiles repeat continuously without abrupt edge seams. Layers are scaled
and cached at startup, and scrolling uses elapsed simulation time.

## Run

Install a JDK (Java 8 or newer), then run these commands in this folder:

```sh
javac FlappyBird.java
java FlappyBird
```

## Controls

- **Play**: start a round from the menu.
- **Instructions**: view controls and rules; **Back to menu** returns.
- **Exit**: close the game.
- Menu buttons support **Tab** navigation and **Enter / Space** activation.
- **Space**, **Up arrow**, or **left click**: flap during play.
- After a collision, use the same controls to play again.
- **R**: restart immediately.
- **M**: mute/unmute effects.
- **Esc**: return to the start menu (ends the current round).
- **1 / 2 / 3**: select Easy / Normal / Hard on the start or game-over screen.

Pass completely through each pair of pipes to earn one point. Touching a pipe,
the ground, or the top of the window ends the round.

## Difficulty

Normal is selected by default. Each mode gradually increases pipe speed and
gravity during active play, reaching its cap after 90 seconds:

| Mode | Pipe speed, start → cap (pixels/s) | Gravity, start → cap (pixels/s²) |
| --- | --- | --- |
| Easy | 150 → 210 | 900 → 1125 |
| Normal | 175 → 245 | 1050 → 1312.5 |
| Hard | 205 → 287 | 1200 → 1500 |

The HUD shows the selected mode and current increases. Speed rises by up to 40%
and gravity by up to 25%, smoothly rather than in sudden jumps. Ground scrolling
matches pipe speed. Jump strength, gap size, and spawn interval stay constant.
Restarting resets the ramp and keeps your selected mode. Difficulty selection
is disabled during a round. The saved best score is shared across modes.

## Saved high score

New records are saved immediately to `.flappy-bird/high-score.txt` inside your
user home folder (on Windows, normally `C:\Users\<name>\.flappy-bird\high-score.txt`).
The game loads this record automatically at startup, regardless of the folder
you launch it from. Restarting a round or changing difficulty keeps the record.

The file contains one nonnegative integer. Missing saves start at zero; invalid
saves are reported and replaced when you earn a new record. Save failures leave
the current best in memory and show a notice in the game. A failed write is
retried at game over. Saves use a temporary file and atomic replacement when
supported, to reduce the risk of an interrupted write corrupting the record.

## Required assets (included)

Keep the `assets` folder beside the compiled classes (or include it at the root
of a JAR). Resources load from the classpath, not the working directory.

| File | Purpose / format |
| --- | --- |
| `assets/clouds.png` | Transparent cloud layer; slowest scrolling |
| `assets/mountains.png` | Transparent distant mountain layer; medium scrolling |
| `assets/hills.png` | Transparent foreground hill layer; fastest scenery scrolling |
| `assets/bird-flap.png` | Transparent 2172 x 724 sprite sheet: three square cells, wings up / level / down |
| `assets/pipe.png` | Green pipe with its lip at the top; transparent PNG, flipped for upper pipes |
| `assets/jump.wav` | Rising chirp on every flap; 0.14 seconds |
| `assets/score.wav` | Two-note chime after clearing a pipe; 0.24 seconds |
| `assets/collision.wav` | Soft impact on collision; 0.30 seconds |

All WAV files are 44.1 kHz, 16-bit, mono PCM. Images were generated specifically
for this project with the built-in image generation tool; sounds were synthesized
locally. The exact artwork prompts are in `assets/PROMPTS.md` and
`assets/PARALLAX-PROMPTS.md`.

The original `assets/background.png` and `assets/bird.png` remain as artwork references and are no longer
required at runtime. Bird animation cycles up → level → down → level, using
55 ms per frame during jumps/ascent and 140 ms during falling. Each jump restarts
the downstroke. Menus use a gentle 220 ms cadence; collision freezes the wings.
Animation uses elapsed simulation time, independently of repaint frequency.

Replacement bird sheets must have three equally sized square cells in one row,
with the body aligned identically in each. Shared transparent bounds are trimmed
at load time, keeping frame scale and position consistent. Pipe images also have
transparent borders trimmed at load time.
The pipe's top ninth is treated as its lip, with the remainder stretched into
the shaft. Keep that layout when replacing it. The bird retains a forgiving
28-pixel circular collision body inside its 38-pixel illustration.

If audio hardware or a sound file is unavailable, the game logs a message and
continues silently. Missing or invalid PNGs report a startup error.

## Verification

```sh
javac FlappyBird.java FlappyBirdTest.java
java -Djava.awt.headless=true FlappyBirdTest
```

Checks menu navigation, instructions, Play, Exit cleanup, persistent records,
invalid/unwritable saves, asset loading, WAV decoding, mute, jumping, gravity, scoring, collisions,
restart, and rendering. Writes a gameplay preview to `build/preview.png`.
These checks do not verify audible playback on your speakers.
Menu and instructions previews are also written to `build/menu.png` and
`build/instructions.png`.
Parallax checks cover depth speeds, difficulty scaling, wraparound, frame-rate
independence, and collision freeze. Scene previews are written to
`build/parallax-start.png` and `build/parallax-seams.png`.
