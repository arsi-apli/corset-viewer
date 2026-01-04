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

1. **Seam Definition**: Each seam is defined by combining its UP and DOWN curves into one continuous polyline
2. **Position Calculation**: Notches are placed at equally-spaced arc-length percentages along the seam
   - For count=3: positions are at 25%, 50%, 75%
   - For count=n: positions are at i/(n+1) for i=1..n
3. **Direction Calculation**:
   - Compute the local tangent vector at each notch position
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
      <path id="A_NOTCH_A_25" d="M ... L ..." />
      <path id="A_NOTCH_A_50" d="M ... L ..." />
      <path id="A_NOTCH_A_75" d="M ... L ..." />
      <path id="A_NOTCH_B_25" d="M ... L ..." />
      <path id="A_NOTCH_B_50" d="M ... L ..." />
      <path id="A_NOTCH_B_75" d="M ... L ..." />
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

- Notches are generated for BOTH vertical seams of each panel (seam to previous + seam to next)
- Outer seams (AA and FF edges) also receive notches
- The original panel geometry is never modified; notches are added as separate elements
- Notch tick marks are drawn as blue lines with 0.5px stroke width

## Example

For a panel with a 200mm tall seam and 3 notches configured:
- Notch 1 at 50mm from top (25%)
- Notch 2 at 100mm from top (50%)
- Notch 3 at 150mm from top (75%)

Each notch is a 4mm line pointing inward from the seam toward the panel interior.
