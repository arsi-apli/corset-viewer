# SVG Export with Seam Allowances

This feature allows you to export corset pattern SVG files with seam allowance offset curves added as a separate layer.

## Usage

1. Load a corset pattern SVG file in the application
2. Navigate to the **Measurements** tab
3. Set the desired seam allowance using the **Seam Allowance (mm)** spinner (default: 10.0 mm, range: 0-100 mm)
4. Click the **Export SVG (with allowances)** button
5. The exported file will be created in the same directory as the input file

## Output File Naming

The output filename follows these rules:
- If the input filename contains `corset_viewer` anywhere, the output appends `_allowances`
- Otherwise, it appends `_corset_viewer_allowances`
- The `.svg` extension is preserved

**Examples:**
- `pattern.svg` → `pattern_corset_viewer_allowances.svg`
- `pattern_corset_viewer.svg` → `pattern_corset_viewer_allowances.svg`
- `pattern_corset_viewer_fixed.svg` → `pattern_corset_viewer_fixed_allowances.svg`

## Output SVG Structure

The exported SVG includes:
- All original content from the input SVG (unchanged)
- A new layer/group: `<g id="allowances" inkscape:label="Allowances" inkscape:groupmode="layer">`
- Offset paths for each vertical seam curve with IDs like `AA_UP_ALLOW`, `AB_DOWN_ALLOW`, etc.
- Paths are styled with `fill:none` and green stroke (`#00ff00`)

## Seam Coverage

The feature generates allowance offset curves for **vertical seams only**:
- Panel seam pairs: `AA_UP/DOWN`, `AB_UP/DOWN`, `BA_UP/DOWN`, `BC_UP/DOWN`, `CB_UP/DOWN`, `CD_UP/DOWN`, `DC_UP/DOWN`, `DE_UP/DOWN`, `ED_UP/DOWN`, `EF_UP/DOWN`, `FE_UP/DOWN`, `FF_UP/DOWN`
- **Does NOT** offset waist lines or top/bottom edges
- **Does NOT** modify the original seam geometry

## Offset Direction

- Left seams (`seamToPrev*`): Offset to the left (outside of the panel)
- Right seams (`seamToNext*`): Offset to the right (outside of the panel)

The offset is computed using segment normals with a bevel join strategy to avoid miter explosions.

## Requirements

- Input SVG must be a valid corset pattern with properly assigned seam IDs
- The pattern must follow the `PatternContract` naming convention (A-F panels with appropriate seam IDs)
