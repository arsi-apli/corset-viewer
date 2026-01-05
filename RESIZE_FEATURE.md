# Panel Resizing Feature

This document describes the panel resizing feature implemented in the corset-viewer application.

## Overview

The panel resizing feature allows users to adjust the circumference of corset patterns by shifting panel nodes horizontally (X-axis only). This is useful for grading patterns to different sizes while maintaining the original vertical proportions.

## User Interface

The resizing controls are located in the bottom toolbar of the Canvas2D view:

### Controls

1. **Resize (mm)** - Spinner control
   - Range: -100 to +100 mm
   - Step: 1.0 mm
   - Represents the desired **full corset circumference** change
   - Negative values shrink the pattern
   - Positive values expand the pattern

2. **Mode** - ComboBox with three options:
   - **GLOBAL**: Resizes all curves (TOP, WAIST, BOTTOM) and all seams
   - **TOP**: Resizes only the TOP curve and UP seams (seamToPrevUp, seamToNextUp). Waist remains unchanged.
   - **BOTTOM**: Resizes only the BOTTOM curve and DOWN seams (seamToPrevDown, seamToNextDown). Waist remains unchanged.

3. **Preview resize** - Checkbox (default: off)
   - When enabled, the canvas displays the resized geometry
   - Allowances and notches are updated to match the resized geometry
   - When disabled, the original geometry is displayed

4. **Reset resize** - Button
   - Sets the resize value to 0
   - Disables the preview
   - Triggers a redraw

5. **Export resized SVG** - Button
   - Exports the resized pattern to a new SVG file
   - Preserves all original styles and attributes
   - Only updates the path coordinates ('d' attributes)
   - Output filename: adds "_resized" suffix before ".svg"
   - Saves to the same directory as the input SVG

## Resize Mathematics

The resize calculation is based on the following logic:

### Per-Side Shift Calculation

```
sideShiftMm = deltaMm / (4 * panelCount)
```

Where:
- `deltaMm` = desired full corset circumference change (user input)
- `panelCount` = number of panels in the half corset pattern

This formula accounts for:
1. Half corset representation in SVG: `deltaMm / 2`
2. Distribution across panels: `/ panelCount`
3. Two vertical seams per panel (left and right): `/ 2`

Final: `deltaMm / (2 * panelCount * 2) = deltaMm / (4 * panelCount)`

### Side Detection

Each panel has:
- Left side (seamToPrev): receives negative shift on grow, positive on shrink
- Right side (seamToNext): receives positive shift on grow, negative on shrink

Seam side determination uses explicit naming convention:
- `seamToPrev*` curves are always on the left side
- `seamToNext*` curves are always on the right side

### Resize Behavior

#### Panel-Edge Curves (TOP, BOTTOM, WAIST)
Panel-edge curves span the entire panel width, so they use **point-level interpolation**:
1. Compute minimum and maximum X coordinates in the curve
2. For each point, calculate interpolation factor: `t = (x - minX) / (maxX - minX)`
3. Apply interpolated shift: `shift = leftShift + t * (rightShift - leftShift)`
4. This ensures leftmost points shift by `leftShift` and rightmost points shift by `rightShift`
5. Points in-between are smoothly interpolated

#### Seam Curves
Seam curves lie on one side of the panel, so they use **uniform shifting**:
- All points in the curve shift by the same amount (leftShift or rightShift)

#### GLOBAL Mode
- TOP, WAIST, BOTTOM curves: point-level interpolation with leftShift and rightShift
- seamToPrevUp, seamToPrevDown: uniform shift by leftShift
- seamToNextUp, seamToNextDown: uniform shift by rightShift
- Maintains consistent panel width change from top to bottom

#### TOP Mode
- TOP curve: point-level interpolation with leftShift and rightShift
- seamToPrevUp: uniform shift by leftShift
- seamToNextUp: uniform shift by rightShift
- Waist, bottom, and DOWN seams remain at original positions
- Useful for adjusting bust circumference independently

#### BOTTOM Mode
- BOTTOM curve: point-level interpolation with leftShift and rightShift
- seamToPrevDown: uniform shift by leftShift
- seamToNextDown: uniform shift by rightShift
- Waist, top, and UP seams remain at original positions
- Useful for adjusting hip circumference independently

## Implementation Details

### Core Classes

1. **ResizeMode** (enum)
   - Defines the three resize modes: GLOBAL, TOP, BOTTOM

2. **PanelResizer** (utility class)
   - `computeSideShift(deltaMm, panelCount)`: Calculates per-side shift amount
   - `resizePanelEdgeCurve(curve, leftShift, rightShift)`: Applies point-level interpolation to panel-edge curves (TOP/BOTTOM/WAIST)
   - `resizeSeamCurve(curve, shift)`: Applies uniform shift to seam curves
   - `shiftPointsX(points, shiftX)`: Applies X-only shift to point list
   - `resizeCurve(curve, shiftX)`: Creates resized curve with shifted points
   - `resizePanel(panel, mode, deltaMm, panelCount)`: Creates ResizedPanel wrapper

3. **ResizedPanel** (wrapper class)
   - Wraps original PanelCurves
   - Pre-computes resized curves based on mode and shift amounts
   - Provides getter methods for resized curves
   - Does not mutate original PanelCurves

4. **Canvas2DView** (updated)
   - Added resize UI controls
   - `getEffectivePanelCurves(index)`: Returns resized or original curves based on preview state
   - `updateResizedPanels()`: Regenerates resized panels when settings change
   - Updated `buildWaistLayout()` and `buildEndpointLayout()` to use effective panels
   - Cache invalidation when preview state changes

5. **SvgExporter** (updated)
   - `exportResizedSvg()`: Exports resized SVG preserving original styles
   - Clones original SVG document
   - Updates only path 'd' attributes for affected curves
   - Uses PatternContract for correct seam ID generation

### Preview Implementation

The preview feature is implemented using a lightweight transformation approach:

1. When preview is enabled, `getEffectivePanelCurves()` returns a temporary PanelCurves with resized geometry
2. The layout engine (`rebuildLayout()`) uses effective panels
3. Allowances and notches are computed from effective panels
4. Cache invalidation ensures consistent rendering

### Export Implementation

The export preserves the original SVG structure:

1. Clones the original SVG DOM
2. Finds path elements by ID
3. Updates only the 'd' attribute with new coordinates
4. Preserves all other attributes (stroke, fill, style, etc.)
5. Writes to new file with "_resized" suffix

## Testing

### Unit Tests (PanelResizerTest)
- Side shift calculation verification
- Point shifting in X-only
- Curve resizing with uniform shift
- Point-level interpolation for panel-edge curves
- Both endpoints moving in opposite directions
- Interpolation correctness for intermediate points
- Uniform shifting for seam curves
- Mode-specific behavior:
  - GLOBAL mode: all curves resized with correct methods
  - TOP mode: waist unchanged, bottom unchanged, DOWN seams unchanged
  - BOTTOM mode: waist unchanged, top unchanged, UP seams unchanged
- Panel resizing with different modes

### Integration Tests (ResizeIntegrationTest)
- Full resize and export workflow
- Validates output SVG structure
- Tests all three resize modes
- Verifies exported files are valid SVG

## Usage Example

1. Load a corset pattern SVG in the application
2. Adjust the "Resize (mm)" spinner to desired circumference change (e.g., +20mm to increase by 20mm)
3. Select the appropriate mode (GLOBAL, TOP, or BOTTOM)
4. Enable "Preview resize" to see the changes in the canvas
5. Verify the resize looks correct
6. Click "Export resized SVG" to save the modified pattern
7. Use the "Reset resize" button to return to original geometry

## Notes

- Resize only affects X coordinates; Y coordinates remain unchanged
- The waist curve is used as the reference for side detection
- Panel orientation is handled automatically based on curve positions
- Original PanelCurves objects are never mutated
- Export preserves all SVG styling and attributes
