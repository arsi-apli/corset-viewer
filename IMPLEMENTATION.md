# Pseudo 3D Visualization Implementation

## Overview
This implementation adds a new "Pseudo 3D" tab to the Corset Viewer application that chains corset panels along the TOP or BOTTOM edge, creating a pseudo-3D visualization.

## New Files Created

### Layout Package (`sk.arsi.corset.layout`)

1. **PanelOrderDetector.java**
   - Auto-detects panel order (A→F vs F→A) from SVG geometry
   - Uses panel A seams to determine orientation
   - Compares average X positions of seam AA vs seam AB
   - If AB is to the right of AA, order is A→F; otherwise F→A

2. **ChainLayoutEngine.java**
   - Computes chained panel layout with rotation and translation
   - Supports TOP and BOTTOM edge modes
   - Contains Transform2D class for 2D transformations
   - Implements chain layout algorithm:
     - First panel: translate so waistLeft is at (0,0)
     - Subsequent panels: rotate around waistLeft to align edge vectors, then translate to connect joints

### UI Package (`sk.arsi.corset.app`)

3. **Pseudo3DView.java**
   - JavaFX view for pseudo-3D visualization
   - Features:
     - TOP/BOTTOM toggle buttons
     - Light background (#f5f5f5)
     - Edge highlighting: TOP=red, BOTTOM=blue
     - Zoom/pan/fit functionality (reused from Canvas2DView)
     - Keyboard shortcuts: F (fit), 1 (TOP), 2 (BOTTOM)
   - Integrates PanelOrderDetector and ChainLayoutEngine

### Updated Files

4. **FxApp.java**
   - Added "Pseudo 3D" tab alongside existing "2D" tab
   - Canvas2DView remains unchanged and functional

### Tests

5. **PanelOrderDetectorTest.java**
   - Tests for order detection logic
   - Covers: A→F order, F→A order, empty panels, missing panel A

6. **ChainLayoutEngineTest.java**
   - Tests for chain layout computation
   - Covers: single panel, two panels, empty list, BOTTOM edge mode, transform operations

## Key Features

1. **Auto-Detection**: Panel order is automatically detected from SVG geometry
2. **Separation of Concerns**: Layout logic is separated from UI (layout package)
3. **Reusability**: Layout classes can be used for future SVG export/watch features
4. **Visual Clarity**: Light background with color-coded edge highlighting
5. **User Control**: Simple toggle between TOP and BOTTOM edge modes

## Testing

All unit tests pass (10/10):
- ChainLayoutEngineTest: 6 tests
- PanelOrderDetectorTest: 4 tests

## Compilation

The project compiles successfully with Java 17 and JavaFX 21:
```bash
mvn clean compile
mvn test
```

## Running the Application

```bash
mvn javafx:run -Dexec.args="path/to/corset.svg"
```

## SVG File Format

The application expects SVG files with specific path IDs following the PatternContract:
- Panel curves: A_TOP, A_BOTTOM, A_WAIST, etc. (for panels A-F)
- Seam curves: AA_UP, AA_DOWN, AB_UP, AB_DOWN, etc.

A test SVG file (`test-corset.svg`) is included for reference.
