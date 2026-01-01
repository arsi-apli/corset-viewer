# SVG ID Assignment Wizard - User Interface Guide

## Visual Layout

```
┌─────────────────────────────────────────────────────────────────┐
│ Priradenie ID pre SVG krivky                              [X]   │
├─────────────────────────────────────────────────────────────────┤
│ Kliknutím na krivku priraďte požadované ID.                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│ Priraď: A_WAIST (1/41)                                           │
│                                                                   │
│ ┌───────────────────────────────────────────────────────────┐   │
│ │                                                             │   │
│ │     Canvas Preview (800x600)                               │   │
│ │                                                             │   │
│ │     ════════════  (Green - A_TOP already has ID)           │   │
│ │                                                             │   │
│ │     ────────────  (Black - unassigned curve)               │   │
│ │                                                             │   │
│ │     ────────────  (Black - unassigned curve)               │   │
│ │                                                             │   │
│ │     ════════════  (Blue - hover over unassigned)           │   │
│ │                                                             │   │
│ │     ────────────  (Red - selected for assignment)          │   │
│ │                                                             │   │
│ └───────────────────────────────────────────────────────────┘   │
│                                                                   │
│                                           [Next]     [Exit]      │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
```

## Color Coding

The wizard uses color to provide visual feedback:

- **Green** (thick line, 1.5px):
  - Paths that already have a required ID in the original SVG
  - Paths that have been assigned a required ID during the wizard session
  - These curves are **ignored** - clicking them does nothing

- **Black** (normal line, 1.0px):
  - Unassigned paths that do not have a required ID
  - These are the candidates for assignment

- **Blue** (thick line, 2.0px):
  - Hover state for unassigned curves
  - Shows which curve will be selected when clicked
  - Only shown when hovering over a non-green curve

- **Red** (very thick line, 2.5px):
  - Currently selected curve
  - This is the curve that will receive the required ID when "Next" is clicked
  - Only one curve can be selected at a time

## User Interaction Flow

### 1. Wizard Opens
- User tries to open an SVG file with missing required IDs
- App catches the `IllegalStateException` and launches the wizard
- All `<path>` elements from the SVG are loaded as candidates
- Paths with required IDs are pre-assigned and shown in green

### 2. Step-by-Step Assignment
```
Step 1: Priraď: A_WAIST (1/41)
  → User hovers over curves (turn blue)
  → User clicks a curve (turns red)
  → User clicks "Next"
  → Assignment saved, curve turns green
  
Step 2: Priraď: A_BOTTOM (2/41)
  → Previous curve (A_WAIST) is now green
  → User selects next curve...
  → Continue until all steps complete
```

### 3. Completion
- After last assignment, wizard automatically:
  1. Saves modified SVG to `<original>_corset_viewer.svg`
  2. Closes the wizard dialog
  3. Updates the app to load the new file
  4. Starts watching the new file for changes

### 4. Cancellation
- User clicks "Exit" at any time
- Wizard closes without saving
- App shows error dialog:
  ```
  ┌─────────────────────────────────────┐
  │ Wizard cancelled                [X] │
  ├─────────────────────────────────────┤
  │ You must assign required IDs to     │
  │ load the SVG.                       │
  │                                     │
  │                            [OK]     │
  └─────────────────────────────────────┘
  ```
- App exits gracefully

## Button States

### Next Button
- **Disabled** (grayed out):
  - When no curve is selected (red)
  - User must click a non-green curve first

- **Enabled** (clickable):
  - When a curve is selected (shown in red)
  - Click to assign the ID and move to next step

### Exit Button
- Always enabled
- Cancels the wizard at any point
- No changes are saved

## Canvas View Transform

The wizard automatically fits all paths to the canvas:
- Calculates bounding box of all path polylines
- Applies uniform scaling (preserves aspect ratio)
- Centers the view with padding
- No manual pan/zoom controls (not required for v1)

## Mouse Interaction

### Hover
- Move mouse over canvas
- Wizard finds nearest path within 10 pixels
- If path is not green, highlight in blue
- If path is green, no visual change

### Click
- Click on a non-green path
- Path turns red (selected)
- "Next" button becomes enabled
- Clicking another path deselects the first

### Ignored Clicks
- Clicking on green paths: no effect
- Clicking on empty space: no effect
- Clicking on same selected path: no effect

## Hit-Testing Algorithm

```
For each mouse position:
  For each candidate path:
    For each line segment in path's polyline:
      Calculate distance from mouse to segment
    Keep track of minimum distance
  
  If minimum distance < 10 pixels:
    Return nearest path as hovered
  Else:
    No path is hovered
```

## File Saving Strategy

When wizard completes:

1. **Read Original File** (text mode, UTF-8)
   ```xml
   <path id="path1" d="M 0 0 L 10 10" />
   ```

2. **Update/Insert IDs** (regex-based)
   ```xml
   <path id="A_WAIST" d="M 0 0 L 10 10" />
   ```

3. **Preserve Formatting**
   - No XML re-serialization
   - Maintains Inkscape metadata
   - Keeps original indentation and spacing

4. **Save to New File**
   - Original: `pattern.svg`
   - New: `pattern_corset_viewer.svg`
   - Same directory as original

5. **Update App**
   - `svgPath` → new file
   - Start watching new file
   - Load panels from new file

## Example Session

```
User opens: corset_pattern.svg
  ↓
Missing: A_WAIST, A_BOTTOM, AA_UP, ... (41 IDs)
  ↓
Wizard opens with "Priraď: A_WAIST (1/41)"
  ↓
User clicks curve #2 → turns red
User clicks "Next"
  ↓
Wizard advances: "Priraď: A_BOTTOM (2/41)"
Curve #2 is now green (A_WAIST)
  ↓
User clicks curve #3 → turns red
User clicks "Next"
  ↓
... repeat for all 41 steps ...
  ↓
Wizard auto-saves: corset_pattern_corset_viewer.svg
Wizard closes
  ↓
App loads: corset_pattern_corset_viewer.svg
File watcher monitors: corset_pattern_corset_viewer.svg
  ↓
Normal app operation continues
```
