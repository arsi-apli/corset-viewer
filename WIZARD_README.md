# SVG ID Assignment Wizard

## Overview

The SVG ID Assignment Wizard is an interactive JavaFX dialog that allows users to assign required path IDs to SVG files when they are missing. This ensures that corset pattern SVG files can be loaded even if they don't initially have all the required ID labels.

## Features

- **Automatic Detection**: The wizard automatically launches when the app detects missing required path IDs
- **Interactive UI**: Click on curves in a preview canvas to assign IDs step-by-step
- **Visual Feedback**: 
  - Green: curves that already have required IDs or have been assigned
  - Black: unassigned curves
  - Blue: hovered curve (if not green)
  - Red: selected curve for assignment
- **Preserves Formatting**: Uses text-based SVG editing to preserve Inkscape formatting
- **Auto-Save**: Automatically saves a modified copy with `_corset_viewer.svg` suffix

## Required Path IDs

The wizard expects 42 required path IDs for a complete corset pattern:

- **Panel curves** (6 panels × 3 curves = 18):
  - `{A,B,C,D,E,F}_TOP`
  - `{A,B,C,D,E,F}_WAIST`
  - `{A,B,C,D,E,F}_BOTTOM`

- **Seam curves** (6 panels × 4 seams = 24):
  - `AA_UP`, `AA_DOWN` (panel A left seam)
  - `AB_UP`, `AB_DOWN` (panel A right seam)
  - `BA_UP`, `BA_DOWN` (panel B left seam)
  - ... continuing for all panels through ...
  - `FF_UP`, `FF_DOWN` (panel F right seam)

## Usage

1. **Launch App**: Start the corset viewer with an SVG file
2. **Wizard Opens**: If required IDs are missing, the wizard opens automatically
3. **Assign IDs**: 
   - The wizard shows which ID to assign (e.g., "Priraď: A_TOP (1/40)")
   - Click on a curve in the preview to select it (turns red)
   - Click "Next" to assign the ID and move to the next step
4. **Complete**: After all IDs are assigned, the wizard:
   - Saves a new SVG file with `_corset_viewer.svg` suffix
   - Automatically loads the new file
   - Sets up file watching on the new file

## Technical Details

### Components

- **`RequiredPath`**: Enum representing all 42 required path IDs
- **`SvgPathCandidate`**: Represents a candidate `<path>` element with polyline for hit-testing
- **`IdWizardSession`**: Manages wizard state and two-way assignments between required paths and candidates
- **`IdAssignmentWizard`**: JavaFX dialog with canvas rendering and mouse interaction
- **`SvgTextEditor`**: Text-based SVG editor that preserves formatting

### Integration Points

The wizard is integrated into `FxApp.start()`:
1. Try to load SVG panels
2. If `IllegalStateException` with "Missing required SVG element id=" is caught:
   - Launch wizard
   - If wizard completes successfully, save modified SVG and reload
   - If wizard is cancelled, show error and exit

### File Saving

The wizard uses text-based editing to preserve Inkscape formatting:
- Reads original SVG as UTF-8 string
- Uses regex to find and update/insert `id` attributes
- Matches paths by their `d` attribute and original `id` (if present)
- Writes to new file with `_corset_viewer.svg` suffix

## Testing

Unit tests verify:
- `RequiredPath.steps()` returns all 42 required IDs in deterministic order
- `IdWizardSession` correctly computes missing steps
- Session pre-assigns candidates that already have required IDs
- `SvgTextEditor` can update existing IDs and insert new IDs
- Assignment maintains two-way references between paths and candidates
