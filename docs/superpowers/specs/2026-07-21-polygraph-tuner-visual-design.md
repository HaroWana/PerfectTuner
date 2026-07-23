# Polygraph Tuner Visual — Design Spec

**Date:** 2026-07-21
**Status:** Approved (brainstormed with visual mockups; layout D3 selected)
**Replaces:** Strobe ring visual (phase 05 design)

## Motivation

The strobe ring encodes pitch deviation in rotation speed, which proved hard to
read on device: motion is not smooth and the mapping (speed + direction → cents)
is confusing. This redesign replaces it with a polygraph-style scrolling pitch
trace (GuitarTuna-like), which encodes pitch deviation *positionally* — a far
more direct read.

## Screen layout (portrait)

```
┌──────────────────────────┐
│ ┌──────────────────────┐ │
│ │   -25    0    +25    │ │  Trace panel (~55% height, #1A1A1A, rounded)
│ │    ·     ┊     ·     │ │  Pen head near top edge; paper scrolls DOWN
│ │      ~~~~┊           │ │  ~5 s of history visible
│ │   ~~~    ┊           │ │  X axis = cents: left flat, right sharp, ±50c clamp
│ │ ~~       ┊           │ │  Dashed green center line = in tune
│ └──────────────────────┘ │  Faint gridlines + small labels at −25 / 0 / +25
│      ╲ ╲  │  ╱ ╱         │
│     ╲  ╲  │  ╱  ╱        │  Perspective strings (bottom half): 6 lines
│    ╲   ╲  │  ╱   ╱       │  converging toward a vanishing point behind
│   E  A  D │ G  B  E      │  the trace panel; note labels at the near plane
│                  [chip]  │  Tuning-selector AssistChip stays bottom-right
└──────────────────────────┘
```

Removed from the current screen: strobe ring, 80sp note readout, cents text,
background vignette.

## Behavior

### Pitch source
The trace draws `TunerState.centsOffset` — the target-string-based, EMA-smoothed
cents (commit a9dbc5b). The pen converges on the center line exactly when the
correct string reaches its target. Chromatic mode falls back to
nearest-semitone cents (existing engine behavior). No engine changes.

### Color states
- **Out of tune:** trace and detected string use the detected string's palette
  color (`StringColors.palette`); other strings dim (white 15% alpha, as today).
- **In tune (|cents| ≤ 5, `TunerState.isInTune`):** trace segments captured
  while in tune render in `StringColors.inTuneGreen`, with a soft translucent
  green fill between the trace line and the center line — green streaks mark
  in-tune stretches in the history. The detected string line and its label also
  turn green.
- **Silence (`TunerState.isSilent`):** the pen holds its last cents value,
  drawing a straight line while the paper keeps scrolling (amended after
  on-device testing — originally the pen lifted). It lifts only before the
  first note and to break the line when a different string starts after a
  held stretch. Strings return to dim; panel chrome stays visible.

### Scroll
Visible history window: **5 seconds** (`WINDOW_MS = 5000`). Scroll speed is
derived: panelHeight / 5 s, frame-rate independent (positions computed from
timestamps, not per-frame increments).

### String labels
From the active tuning's `GuitarString.noteName` (drop-D shows D A D G B E).
Chromatic mode uses Standard-tuning labels, matching the existing overlay
behavior of lighting the nearest standard string.

## Components

### `TraceGeometry.kt` (new, pure — the TDD seam)
No Compose dependencies. Owns:
- `TraceSample(timeMs: Long, cents: Float?, inTune: Boolean, stringIndex: Int?)`
  — `cents = null` means pen lifted (silence).
- Eviction: drop samples older than `nowMs - WINDOW_MS`.
- Geometry building, parameterized by panel size:
  - time → y: pen row at top, `y = (nowMs - timeMs) / WINDOW_MS * height`
  - cents → x: `x = centerX + clamp(cents, ±50) / 50 * (width / 2 - inset)`
  - splits the sample list into contiguous polyline segments (broken at
    silence gaps), each tagged with color state (string color vs in-tune
    green), plus closed fill-region paths between in-tune segments and the
    center line.

### `PitchTrace.kt` (new composable)
- Ring buffer of `TraceSample` (plain deque, not snapshot state).
- Frame loop (`LaunchedEffect(Unit)` + `withInfiniteAnimationFrameMillis`, same
  pattern as StrobeRing): appends one sample per display frame from
  `rememberUpdatedState`-captured `TunerState` fields, evicts old samples,
  bumps a frame-tick state to drive redraw.
- Canvas draw pass renders panel background, gridlines/labels, dashed center
  line, then the segments/fills from `TraceGeometry`. Reused `Path` objects;
  no per-frame allocations in the hot path (per code-review lessons: dt-based
  math, no boxing, no per-draw object churn).

### `StringsOverlay.kt` (modified)
- Ring clip (`ringRadiusDp`, `clipPath`) removed.
- Vanishing point raised so lines converge behind the trace panel; near plane
  at the screen bottom.
- Note labels drawn at the near-plane line ends (active tuning's note names);
  detected string's label rendered in its line color (green when in tune).

### `TunerScreen.kt` (recomposed)
- Column/Box layout: trace panel top (~55%), strings overlay bottom.
- Keeps: `AssistChip` + `ModalBottomSheet` tuning picker, lifecycle
  start/stop-listening, string color animation (`animateColorAsState`).
- Removes: `StrobeRing` usage, note/cents `Text`s, vignette `drawWithCache`.

### Removals
- `StrobeRing.kt` deleted (including `RING_RADIUS_DP`).
- `TunerState.waveformSamples` and `TunerEngine.downsampleFrame()` removed —
  nothing renders audio waveforms anymore. `TunerEngine`'s tuning logic
  (target-based cents, EMA, isInTune) is untouched.

## Error handling / edge cases
- First frames before any sample: empty trace, panel chrome only.
- Cents beyond ±50: clamped to panel edge (engine can emit up to ±200).
- String switch mid-trace: samples carry their own `stringIndex`/`inTune`, so
  history keeps the color it was captured with; only new samples take the new
  string's color.
- App pause/resume: listening stops/starts via existing `LifecycleResumeEffect`;
  on resume the buffer naturally refills (stale samples evicted by timestamp).

## Testing
- **TDD on `TraceGeometry`:** mapping (time→y, cents→x), ±50c clamping,
  silence gap segmentation, in-tune vs string-color segmentation, fill-region
  generation, eviction.
- Existing `TunerEngineTest` continues to guard the cents pipeline unchanged.
- Visual feel (scroll smoothness, colors, perspective) validated on device.

## Out of scope
- No engine/audio changes.
- No lock celebration / haptics (explicitly not chosen).
- No landscape-specific layout.
- Settings, purchase flow, tuning picker unchanged.
