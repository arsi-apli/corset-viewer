# Sewing Notches Export Feature

## Overview

The Corset Viewer now includes functionality to export SVG files with sewing notches added to panel seams. Notches are small tick marks that help align fabric pieces during sewing.

## Features

- **Automatic Notch Generation**: Notches are automatically placed at evenly-spaced intervals along each seam
- **Configurable Count**: Choose how many notches per seam (1-10, default: 3)
- **Configurable Length**: Set the notch tick length in mm (3-5mm, default: 4mm)
- **Inward Direction**: Notches automatically point inward toward the panel interior
- **SVG Export**: Export panels with notches as a new SVG file that can be opened in Inkscape or other vector graphics software

## How to Use

### 1. Load a Corset SVG File

Open the Corset Viewer and load an SVG file containing your corset pattern. The file must have the required panel IDs (see main README).

### 2. Configure Notch Settings

In the 2D view toolbar, you'll find the export controls:

- **Notches**: Spinner to set the number of notches per seam (default: 3)
- **Length (mm)**: Spinner to set the notch tick length (default: 4.0mm)

### 3. Export

Click the **"Export SVG with Notches"** button. Choose a filename and location to save the exported SVG.

### 4. View/Edit the Result

Open the exported SVG in Inkscape or your preferred vector graphics software. You'll see:

- All original panel geometry preserved
- Panels organized in groups (e.g., `A_PANEL`, `B_PANEL`, etc.)
- Each panel has a `_NOTCHES` subgroup containing the notch tick marks
- Notch IDs follow the pattern: `{PANEL}_NOTCH_{NEIGHBOR}_{PERCENT}` (e.g., `C_NOTCH_D_25`)

## Technical Details

### Notch Placement Algorithm

1. **Seam Definition**: Each seam consists of two separate curves: UP (above waist) and DOWN (below waist)
2. **Independent Generation**: Notches are generated separately for UP and DOWN curves
   - Each curve gets N notches positioned at i/(N+1) along its own length
   - For count=3: each curve gets notches at 25%, 50%, 75% = 6 notches total per seam
   - For count=n: each curve gets n notches = 2n notches total per seam
3. **Direction Calculation**:
   - Compute the local tangent vector at each notch position on the curve
   - Compute the normal (perpendicular) to the tangent
   - Choose the inward normal using the panel's interior reference point (waist curve centroid)
4. **Tick Generation**: Create a short line segment from the seam point into the panel interior

### SVG Structure

The exported SVG maintains this structure:

```xml
<svg>
  <g id="A_PANEL">
    <path id="A_TOP" ... />
    <path id="A_BOTTOM" ... />
    <path id="A_WAIST" ... />
    <path id="AA_UP" ... />
    <path id="AA_DOWN" ... />
    <path id="AB_UP" ... />
    <path id="AB_DOWN" ... />
    <g id="A_NOTCHES">
      <path id="A_NOTCH_A_UP_25" d="M ... L ..." />
      <path id="A_NOTCH_A_UP_50" d="M ... L ..." />
      <path id="A_NOTCH_A_UP_75" d="M ... L ..." />
      <path id="A_NOTCH_A_DOWN_25" d="M ... L ..." />
      <path id="A_NOTCH_A_DOWN_50" d="M ... L ..." />
      <path id="A_NOTCH_A_DOWN_75" d="M ... L ..." />
      <path id="A_NOTCH_B_UP_25" d="M ... L ..." />
      <path id="A_NOTCH_B_UP_50" d="M ... L ..." />
      <path id="A_NOTCH_B_UP_75" d="M ... L ..." />
      <path id="A_NOTCH_B_DOWN_25" d="M ... L ..." />
      <path id="A_NOTCH_B_DOWN_50" d="M ... L ..." />
      <path id="A_NOTCH_B_DOWN_75" d="M ... L ..." />
    </g>
  </g>
  <!-- More panels... -->
</svg>
```

## Typical Use Cases

1. **Sewing Guides**: Use notches to align fabric pieces accurately during construction
2. **Quality Control**: Notches help verify that pattern pieces are cut and assembled correctly
3. **Pattern Grading**: When scaling patterns, notches maintain consistent alignment points

## Notes

- Notches are generated separately for UP and DOWN curves on each seam
- Each curve gets N notches when you set notch count to N (total: 2N per seam)
- Notches are generated for BOTH vertical seams of each panel (seam to previous + seam to next)
- Outer seams (AA and FF edges) also receive notches
- The original panel geometry is never modified; notches are added as separate elements
- Notch tick marks are drawn as black lines with 0.5px stroke width
- Notch IDs include the segment identifier (UP or DOWN) to avoid collisions

## Preview in 2D View

Before exporting, you can preview notches in the 2D view:
1. Check the **"Show notches"** checkbox in the export toolbar
2. Adjust the notch count and length to see the preview update in real-time
3. Notches appear as black tick marks on the panel seams
4. The preview uses the same geometry computation as the exporter

## Example

For a panel with UP curve (100mm) and DOWN curve (100mm) with 3 notches configured:

**UP Curve Notches:**
- Notch 1 at 25mm from waist (25% along UP curve)
- Notch 2 at 50mm from waist (50% along UP curve)
- Notch 3 at 75mm from waist (75% along UP curve)

**DOWN Curve Notches:**
- Notch 1 at 25mm from waist (25% along DOWN curve)
- Notch 2 at 50mm from waist (50% along DOWN curve)
- Notch 3 at 75mm from waist (75% along DOWN curve)

Total: 6 notches per seam (3 on UP + 3 on DOWN)

Each notch is a 4mm line pointing inward from the seam toward the panel interior.
