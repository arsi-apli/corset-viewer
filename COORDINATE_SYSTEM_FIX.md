# Coordinate System Fix Explained

## The Problem

The seam highlighting split was being computed in the wrong coordinate system, causing the red highlighting to appear at the wrong location along the curve.

## Coordinate Systems

The application uses three coordinate systems:

### 1. Panel-Local Coordinates (SVG Space)
```
  Panel A in its original SVG position
  
  (0,0) ────────────────► X
    │
    │    ┌─────────────┐ ← TOP
    │    │             │
    │    │    PANEL    │
    ▼    │─────────────│ ← WAIST (waistY = 150)
    Y    │             │
         │    A        │
         └─────────────┘ ← BOTTOM
```

### 2. World Coordinates (After Transform)
```
  Panel A after rotation and translation
  
  (0,0) ────────────────► X
    │           
    │              ╱
    │           ╱ Panel A rotated 30°
    │        ╱    and translated
    ▼     ╱
    Y  ╱
    ╱─────────────╱ ← WAIST (at different Y!)
  ╱             ╱
╱   A         ╱
─────────────╱
```

### 3. Screen Coordinates (Final Pixels)
```
Screen with zoom and pan applied
```

## The Bug

### Before Fix (WRONG)
```java
// 1. Transform points to world coordinates
Pt p0 = rp.transform.apply(pts.get(i));     // Panel → World
Pt p1 = rp.transform.apply(pts.get(i + 1)); // Panel → World

// 2. Get Y in world coordinates
double y0 = p0.getY();  // World Y
double y1 = p1.getY();  // World Y

// 3. Compare with panel-local waistY - WRONG!
boolean p0Above = y0 < waistY;  // ❌ Comparing apples to oranges!
//                      ^^^^^^
//                      waistY is in panel-local space (e.g., 150)
//                      but y0, y1 are in world space (e.g., 327 after rotation)
```

**Why it fails:**
- `waistY` from `computePanelWaistY0()` is in panel-local coordinates (e.g., 150)
- After rotation by 30° and translation by (200, 100), a point at panel-Y=150 might be at world-Y=327
- Comparing world-Y (327) with panel-local waistY (150) gives wrong result!

### After Fix (CORRECT)
```java
// 1. Keep points in panel-local coordinates
Pt localP0 = pts.get(i);     // Panel-local
Pt localP1 = pts.get(i + 1); // Panel-local

// 2. Get Y in panel-local coordinates
double localY0 = localP0.getY();  // Panel-local Y
double localY1 = localP1.getY();  // Panel-local Y

// 3. Compare in same coordinate system - CORRECT!
boolean p0Above = localY0 < waistY;  // ✅ Both in panel-local space!
//                ^^^^^^^^     ^^^^^^
//                Both are in panel-local coordinates (e.g., 145 < 150)

// 4. THEN transform to world for rendering
Pt p0 = rp.transform.apply(localP0);  // Panel → World
Pt p1 = rp.transform.apply(localP1);  // Panel → World
```

## Visual Example

### Panel-Local Space (Original SVG)
```
    100      150      200   (Y coordinates)
     │        │        │
─────┼────────┼────────┼───── TOP curve
     │  ╱╲    │   ╱╲   │
     │ ╱  ╲   │  ╱  ╲  │
═════╪═══════╪═══════╪═════ WAIST (waistY = 150)
     │╲    ╱ │ ╲    ╱ │
     │ ╲  ╱  │  ╲  ╱  │
─────┼────────┼────────┼───── BOTTOM curve
     │        │        │
```

Point at (x, y) = (50, 145) is ABOVE waist (145 < 150) ✓

### After Transform (World Space)
```
  Rotated 30° and translated (200, 100)
  
    200      350      500   (Y coordinates)
     │       ╱│       ╱│
─────┼──────╱─┼──────╱─┼───── TOP curve
     │  ╱╲╱   │  ╱╲╱   │
     │ ╱  ╲   │ ╱  ╲   │
═════╪═══╱══╪╱═══╱══╪═════ WAIST (now at different Y!)
     │╲╱   ╱│╲╱   ╱ │
     │╱╲  ╱ │╱╲  ╱  │
─────┼────────┼────────┼───── BOTTOM curve
     │        │        │
```

Same point after transform: (x, y) ≈ (293, 327)

**Wrong comparison:** Is 327 < 150? NO! ❌ (but point is actually above waist)
**Correct comparison:** Is 145 < 150? YES! ✓ (compare before transform)

## The Fix for Split Points

When a seam segment crosses the waist, we need to compute the split point:

### Before Fix (WRONG)
```java
// In world coordinates
double t = (waistY - y0) / (y1 - y0);  // ❌ Wrong! waistY in panel-local, y0/y1 in world
double xSplit = p0.getX() + t * (p1.getX() - p0.getX());
```

### After Fix (CORRECT)
```java
// In panel-local coordinates
double t = (waistY - localY0) / (localY1 - localY0);  // ✓ All in panel-local
double localXSplit = localP0.getX() + t * (localP1.getX() - localP0.getX());

// Create split point in panel-local coordinates
Pt localSplit = new Pt(localXSplit, waistY);

// THEN transform to world coordinates
Pt worldSplit = rp.transform.apply(localSplit);
```

## Key Principle

**Geometric comparisons must be done in a consistent coordinate system.**

Since `waistY` is computed in panel-local coordinates (from the original SVG), all comparisons with it must also be in panel-local coordinates. Only after determining the geometry do we transform to world/screen coordinates for rendering.

## Transform Pipeline

```
Panel-Local Coordinates         World Coordinates           Screen Coordinates
(Original SVG)                  (After rotation/translation) (After zoom/pan)
─────────────────────────────────────────────────────────────────────────────

localP0 (50, 145)               p0 (293, 327)               screen (421, 456)
         │                           │                            │
         │ Transform2D.apply()       │ worldToScreen()            │
         └──────────────────────────►└────────────────────────────►
         
waistY = 150 ◄─── Comparison happens here (panel-local)!
         │
         │
localY0 = 145 < waistY? YES ✓
```

## Summary

1. **Root cause**: Comparing Y coordinates from different coordinate systems
2. **Solution**: Keep comparisons in panel-local coordinates, transform only for rendering
3. **Impact**: Seam highlighting now splits at the correct location along the curve
4. **Principle**: Always compare geometric values in the same coordinate system

This fix ensures that the red highlighting appears exactly at the waist line as defined in the original SVG, regardless of how the panel has been rotated or translated for display.
