# SVG Resize Feature

## Overview

The SVG Resize Feature allows users to adjust the width of corset panels by modifying the original SVG path data and re-sampling for preview. This is a productivity feature designed to reduce manual node moving in Inkscape - users can apply a rough resize in the viewer, export, and then fine-tune curves in Inkscape.

## UI Components

The resize controls are located in the **bottom toolbar** of the Canvas2D view:

- **Resize Mode ComboBox**: Select between three modes:
  - `DISABLED` (default): No resizing applied
  - `TOP`: Resize only the top edge and upper seams
  - `GLOBAL`: Resize all edges and seams

- **Delta (mm) Spinner**: Set the total width change in millimeters
  - Range: -50mm to +50mm
  - Default: 0mm
  - Positive values widen the corset, negative values narrow it

## How It Works

### Core Mechanism

1. **Original Data Preservation**: The viewer maintains two sets of panel data:
   - `panelsOriginal`: The unmodified panels loaded from SVG
   - `panelsEffective`: Panels after applying resize transformation

2. **SVG Path Editing**: Instead of just moving sampled points, the resizer:
   - Edits the original SVG path data (`d` attribute strings)
   - Identifies specific endpoint nodes to modify
   - Adjusts their coordinates while preserving Bezier control points
   - Re-samples the modified path to generate new preview points

3. **No Cumulative Drift**: All resize operations are computed from the original panels, not from previously resized ones. This ensures consistency and allows users to experiment with different values without accumulating errors.

### Resize Modes

#### DISABLED Mode
- No transformation applied
- Returns original panels unchanged
- Use this when you don't need resizing or want to see the original pattern

#### TOP Mode
The TOP mode is designed for adjusting only the bust/top area of the corset while leaving the waist and bottom unchanged.

For each panel, it calculates `sideShiftMm = deltaMm / (4 * panelCount)` and applies:

- **Top Edge**: Finds the two endpoints with minimum Y coordinate (top of pattern)
  - Shifts left endpoint by `-sideShiftMm`
  - Shifts right endpoint by `+sideShiftMm`

- **UP Seams (seamToPrevUp, seamToNextUp)**: 
  - Finds the endpoint with minimum Y coordinate
  - Shifts left seam by `-sideShiftMm` (for seamToPrevUp)
  - Shifts right seam by `+sideShiftMm` (for seamToNextUp)

- **Unchanged**: Waist edge, bottom edge, and DOWN seams remain exactly as in the original

#### GLOBAL Mode
The GLOBAL mode provides a simple overall widening/narrowing of the entire panel.

For each panel, it calculates `sideShiftMm = deltaMm / (4 * panelCount)` and applies:

- **Horizontal Edges (top, bottom, waist)**:
  - Finds leftmost and rightmost endpoints
  - Shifts left endpoint by `-sideShiftMm`
  - Shifts right endpoint by `+sideShiftMm`

- **Vertical Seams (all UP and DOWN seams)**:
  - Finds endpoints at top (minY) and bottom (maxY)
  - Shifts both endpoints by `±sideShiftMm` (depending on left/right seam)

## Technical Details

### New Classes

1. **`sk.arsi.corset.resize.ResizeMode`** (enum)
   - `DISABLED`, `TOP`, `GLOBAL`

2. **`sk.arsi.corset.resize.SvgPathEditor`**
   - Parses SVG path data (supports M/m, L/l, C/c, Z/z commands)
   - Extracts endpoint nodes from paths
   - Modifies specific node coordinates
   - Outputs modified path data (converts to absolute commands for simplicity)

3. **`sk.arsi.corset.resize.PanelResizer`**
   - Orchestrates the resize operation
   - Applies mode-specific transformations to panels
   - Uses `PathSampler` to re-sample modified paths

### Model Changes

**`sk.arsi.corset.model.Curve2D`**:
- Added `String d` field to store original SVG path data
- Added `getD()` getter method
- Updated constructor: `Curve2D(String id, String d, List<Pt> points)`
- Backward-compatible constructor: `Curve2D(String id, List<Pt> points)` for synthetic curves

**`sk.arsi.corset.svg.PathSampler`**:
- Updated to pass `d` parameter when creating `Curve2D` instances

### UI Integration

**`sk.arsi.corset.app.Canvas2DView`**:
- Added resize controls to bottom toolbar
- Maintains `panelsOriginal` and `panelsEffective` separately
- Recomputes effective panels whenever resize mode or delta changes
- Automatically updates layout, measurements, and notches based on effective panels

## Usage Example

1. Load a corset pattern SVG file
2. Select resize mode from the dropdown (e.g., "TOP")
3. Adjust the delta spinner (e.g., +10mm to widen by 10mm total)
4. Preview updates in real-time showing the resized pattern
5. Export the result with allowances and notches
6. Open in Inkscape for final curve adjustments

## Testing

All functionality is covered by unit tests:
- `Curve2DTest`: Verifies `d` field storage and retrieval
- `SvgPathEditorTest`: Tests SVG path parsing and modification
- `PanelResizerTest`: Validates resize modes and behavior

Total: 106 tests pass (17 new tests added for resize feature)

## Limitations and Future Enhancements

### Current Limitations
- Only supports M/m, L/l, C/c, Z/z SVG path commands
- Does not adjust Bezier control points (only endpoint nodes)
- GLOBAL mode uses a simplified approach for vertical seams

### Potential Enhancements
- Add more sophisticated control point adjustment
- Support additional SVG path commands (H/h, V/v, S/s, Q/q, etc.)
- Add preview highlighting to show which parts are being modified
- Allow per-panel custom resize amounts
- Add "undo" functionality for resize operations
