# Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                         FxApp (Main UI)                         │
│  ┌──────────────┐  ┌──────────────┐                            │
│  │  Tab: "2D"   │  │Tab:"Pseudo3D"│                            │
│  └──────────────┘  └──────────────┘                            │
└─────────────────────────────────────────────────────────────────┘
         │                    │
         │                    │
         ▼                    ▼
┌──────────────────┐  ┌──────────────────┐
│  Canvas2DView    │  │  Pseudo3DView    │
│  (Unchanged)     │  │  (New)           │
│                  │  │                  │
│  - TOP mode      │  │  - TOP button    │
│  - WAIST mode    │  │  - BOTTOM button │
│  - BOTTOM mode   │  │  - Light bg      │
│  - White bg      │  │  - Edge highlight│
│  - Zoom/Pan/Fit  │  │  - Zoom/Pan/Fit  │
└──────────────────┘  └──────────────────┘
                              │
                              │ uses
                              ▼
                      ┌───────────────────────┐
                      │   Layout Package      │
                      │   (New - Non-UI)      │
                      │                       │
                      │  ┌─────────────────┐  │
                      │  │PanelOrderDetect │  │
                      │  │ - detectOrderAtoF│ │
                      │  │ - analyze seams  │  │
                      │  └─────────────────┘  │
                      │                       │
                      │  ┌─────────────────┐  │
                      │  │ChainLayoutEngine│  │
                      │  │ - computeLayout │  │
                      │  │ - Transform2D   │  │
                      │  │ - rotation      │  │
                      │  │ - translation   │  │
                      │  └─────────────────┘  │
                      └───────────────────────┘
                              │
                              │ operates on
                              ▼
                      ┌───────────────────────┐
                      │   Model Package       │
                      │   (Shared)            │
                      │                       │
                      │  - PanelCurves        │
                      │  - Curve2D            │
                      │  - Pt                 │
                      │  - PanelId            │
                      └───────────────────────┘
                              ▲
                              │ loaded by
                              │
                      ┌───────────────────────┐
                      │   SVG Package         │
                      │                       │
                      │  - SvgLoader          │
                      │  - PatternExtractor   │
                      │  - PathSampler        │
                      │  - PatternContract    │
                      └───────────────────────┘
                              ▲
                              │
                         SVG File
                    (e.g., corset.svg)
```

## Data Flow

1. **Loading**:
   ```
   SVG File → SvgLoader → PatternExtractor → List<PanelCurves>
   ```

2. **Order Detection**:
   ```
   List<PanelCurves> → PanelOrderDetector → boolean (A→F or F→A)
   ```

3. **Layout Computation**:
   ```
   List<PanelCurves> + EdgeMode → ChainLayoutEngine → List<LayoutResult>
   ```

4. **Rendering**:
   ```
   List<LayoutResult> → Pseudo3DView → Canvas (with highlighting)
   ```

## Key Design Decisions

### 1. Separation of Concerns
- **Layout logic** separated from UI in dedicated package
- **Reusable** for future SVG export/watch features
- **Testable** without UI dependencies

### 2. Auto-Detection
- No user input needed for panel order
- **Smart detection** based on geometry
- **Fallback** to sensible defaults

### 3. Transform Composition
- **Rotation** around pivot point
- **Translation** to connect panels
- **Immutable** Transform2D objects

### 4. Edge Highlighting
- **Fixed colors**: TOP=red, BOTTOM=blue
- **Light background** for visibility
- **No dark backgrounds** or black under-strokes

### 5. User Experience
- **Simple toggles**: Two buttons for mode
- **Keyboard shortcuts**: F, 1, 2
- **Familiar interactions**: Same zoom/pan/fit as Canvas2DView
