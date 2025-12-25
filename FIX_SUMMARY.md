# Fix Summary: 2D Seam Rendering Issues

## Problem Statement

After adding the Measurements tab and tolerance highlighting feature, two critical bugs were discovered in the 2D seam rendering:

1. **Outer seams AA and FF no longer render** - These are the outer edges of the half-corset and should always be visible in black.

2. **Red seam highlighting starts at an incorrect location along the curve** - The highlighting should color the top or bottom portion exactly as defined by the SVG seam curve, split at that panel's waist line.

## Root Causes

### Issue 1: AA and FF Not Rendering

**Location**: `Canvas2DView.drawSeamWithHighlight()` at line 667-669 (original code)

**Problem**: When `neighborId` is null (which happens for panel A's `seamToPrev` and panel F's `seamToNext`), the function returned early without drawing anything.

```java
// OLD CODE - BUGGY
if (curve == null || neighborId == null) {
    return;  // AA and FF seams were lost here!
}
```

**Root Cause**: The outer seams (AA = panel A's left edge, FF = panel F's right edge) don't have neighbors, so `getPrevPanelId(A)` and `getNextPanelId(F)` return null. The early return prevented these seams from being drawn.

### Issue 2: Incorrect Highlight Position

**Location**: `Canvas2DView.strokeCurveSplit()` at lines 714-757 (original code)

**Problem**: The waist split was being computed in transformed (world) coordinates instead of panel-local coordinates.

```java
// OLD CODE - BUGGY
Pt p0 = rp.transform.apply(pts.get(i));     // Transform to world coords
Pt p1 = rp.transform.apply(pts.get(i + 1)); // Transform to world coords

double y0 = p0.getY();  // World Y coordinate
double y1 = p1.getY();  // World Y coordinate

boolean p0Above = y0 < waistY;  // BUG! Comparing world Y with panel-local waistY
```

**Root Cause**: The `waistY` value computed by `MeasurementUtils.computePanelWaistY0()` is in panel-local coordinates (the original SVG coordinates), but the code was comparing it against Y coordinates that had already been transformed (rotated and translated) to world space. This caused the split point to be at the wrong location.

### Issue 3: Missing UP/DOWN Curve Granularity

**Location**: `Canvas2DView.SeamHighlight` and `computeSeamHighlights()`

**Problem**: The SeamHighlight class only tracked TOP/BOTTOM portions, not which curve type (UP or DOWN) exceeded tolerance.

```java
// OLD CODE - INCOMPLETE
private static class SeamHighlight {
    boolean highlightTop;     // Only tracks portion, not curve type
    boolean highlightBottom;  // Only tracks portion, not curve type
}
```

**Root Cause**: The requirements specify that highlighting should apply ONLY to the curve type (UP or DOWN) that exceeds tolerance. The old implementation would highlight BOTH UP and DOWN curves if either exceeded tolerance in a given portion.

## Solutions

### Fix 1: Always Render AA and FF in Black

```java
// NEW CODE - FIXED
// If curve is null, nothing to draw
if (curve == null) {
    return;
}

// If neighborId is null, this is an outer seam (AA or FF) - always draw in black
if (neighborId == null) {
    strokeCurve(g, rp, curve, seamColor, 1.5);
    return;
}
```

**Changes**:
- Split the null check into two separate conditions
- When `curve` is null, return early (nothing to draw)
- When `neighborId` is null but `curve` is not, draw the curve in default black color
- This ensures AA (panel A's seamToPrev) and FF (panel F's seamToNext) always render

### Fix 2: Split in Panel-Local Coordinates

```java
// NEW CODE - FIXED
// Get original points in panel-local coordinates
Pt localP0 = pts.get(i);
Pt localP1 = pts.get(i + 1);

// Get Y coordinates in panel-local coordinates for comparison with waistY
double localY0 = localP0.getY();
double localY1 = localP1.getY();

// Determine if points are above or below waist in panel-local coordinates
boolean p0Above = localY0 < waistY;  // CORRECT! Both in panel-local coords
boolean p1Above = localY1 < waistY;

// Transform points to world coordinates for rendering
Pt p0 = rp.transform.apply(localP0);
Pt p1 = rp.transform.apply(localP1);

// ... when segment crosses waist ...
// Compute split point in panel-local coordinates
double t = (waistY - localY0) / (localY1 - localY0);
double localXSplit = localP0.getX() + t * (localP1.getX() - localP0.getX());

// Transform the split point to world coordinates
Pt localSplit = new Pt(localXSplit, waistY);
Pt worldSplit = rp.transform.apply(localSplit);
```

**Changes**:
- Keep original points in panel-local coordinates (`localP0`, `localP1`)
- Compare their Y coordinates with `waistY` (both in panel-local space)
- Only transform to world coordinates for rendering
- When computing split point, do so in panel-local coordinates, then transform

### Fix 3: Track UP/DOWN and TOP/BOTTOM Separately

```java
// NEW CODE - COMPLETE
private static class SeamHighlight {
    final boolean highlightUpTop;       // UP curve, TOP portion
    final boolean highlightUpBottom;    // UP curve, BOTTOM portion
    final boolean highlightDownTop;     // DOWN curve, TOP portion
    final boolean highlightDownBottom;  // DOWN curve, BOTTOM portion
}
```

**Changes in `computeSeamHighlights()`**:
```java
// Check which curves and portions exceed tolerance
boolean upTopExceeds = Math.abs(data.getDiffUpTop()) > tolerance;
boolean upBottomExceeds = Math.abs(data.getDiffUpBottom()) > tolerance;
boolean downTopExceeds = Math.abs(data.getDiffDownTop()) > tolerance;
boolean downBottomExceeds = Math.abs(data.getDiffDownBottom()) > tolerance;

SeamHighlight highlight = new SeamHighlight(upTopExceeds, upBottomExceeds, 
                                            downTopExceeds, downBottomExceeds);
```

**Changes in `drawSeamWithHighlight()`**:
```java
// Determine which portions to highlight based on curve type (UP or DOWN)
boolean highlightTop;
boolean highlightBottom;

if (isUp) {
    highlightTop = highlight.highlightUpTop;
    highlightBottom = highlight.highlightUpBottom;
} else {
    highlightTop = highlight.highlightDownTop;
    highlightBottom = highlight.highlightDownBottom;
}
```

**Changes**:
- SeamHighlight now tracks all 4 combinations: {UP,DOWN} × {TOP,BOTTOM}
- computeSeamHighlights checks each curve type and portion individually
- drawSeamWithHighlight selects the correct flags based on which curve is being drawn

## Verification

### Requirements Met

✅ **AA and FF must always render in black** - Fixed by handling null neighborId

✅ **Interior seam pairs AB..EF are highlighted on BOTH matching seams** - Already working, unchanged

✅ **Highlighting applies ONLY to the curve type (UP or DOWN) that exceeds tolerance** - Fixed by tracking UP/DOWN separately

✅ **Highlighting is portion-specific (TOP or BOTTOM)** - Fixed by tracking TOP/BOTTOM for each curve type

✅ **Split computed in panel-local coordinates** - Fixed by comparing localY with waistY before transforming

✅ **Drawing behavior preserves baseline seam drawing** - AA/FF now always drawn in black

### Testing

All existing unit tests pass:
```
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
```

### Code Quality

- Code review completed with only minor nitpicks (not affecting correctness)
- Clean compilation with no errors or warnings (except pre-existing deprecation warnings in MeasurementsView)
- Changes are minimal and focused (96 insertions, 38 deletions in 1 file)

## Technical Details

### Coordinate Systems

The fix correctly handles three coordinate systems:

1. **Panel-Local Coordinates**: Original SVG coordinates where waistY is computed
2. **World Coordinates**: After applying panel transform (rotation + translation)
3. **Screen Coordinates**: Final pixel coordinates for rendering

The key insight is that geometric comparisons (like "is this point above the waist?") must be done in panel-local coordinates, while rendering must be done in screen coordinates.

### Transform Pipeline

```
Panel-Local → [Transform2D] → World → [worldToScreen] → Screen
    ↑                                                        ↓
waistY comparison                                     strokeLine()
```

### Seam Naming Convention

- **AA**: Panel A's left edge (seamToPrev, neighborId = null)
- **AB**: Panel A's right edge (seamToNext) = Panel B's left edge (seamToPrev)
- **BA**: Panel B's left edge (seamToPrev) = Panel A's right edge (seamToNext)
- **FF**: Panel F's right edge (seamToNext, neighborId = null)

Each interior seam (AB, BC, CD, DE, EF) has two representations (one per panel) that should be highlighted identically.

## Impact

- **No breaking changes**: Only fixes bugs in existing rendering logic
- **No API changes**: All changes are internal to Canvas2DView
- **No test changes**: Existing tests continue to pass
- **No UI changes**: Only fixes incorrect behavior to match requirements

## Files Changed

- `src/main/java/sk/arsi/corset/app/Canvas2DView.java`: 96 insertions(+), 38 deletions(-)
  - Updated SeamHighlight class
  - Updated computeSeamHighlights method
  - Updated drawSeamWithHighlight method
  - Updated strokeCurveSplit method
