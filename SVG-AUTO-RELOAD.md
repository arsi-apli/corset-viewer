# SVG Auto-Reload Feature

## Overview

The corset-viewer application now automatically reloads SVG files when they change on disk. This is particularly useful when editing SVG files in external tools like Inkscape - changes are immediately reflected in the viewer without manual reloading.

## Implementation Details

### Components

1. **SvgFileWatcher** (`sk.arsi.corset.app.SvgFileWatcher`)
   - Uses Java NIO `WatchService` to monitor file changes
   - Runs on a background daemon thread
   - Watches the parent directory and filters events for the target file
   - Implements debouncing to handle rapid successive writes (common with editors)

2. **FxApp Modifications** (`sk.arsi.corset.app.FxApp`)
   - Refactored to support reloading SVG files
   - Starts file watcher on application startup
   - Stops watcher on application shutdown
   - Preserves UI state during reload:
     - Current tab selection (2D vs Pseudo 3D)
     - Pseudo3D mode (TOP/BOTTOM)

3. **Pseudo3DView Enhancements** (`sk.arsi.corset.app.Pseudo3DView`)
   - Added `getEdgeMode()` to retrieve current mode
   - Added `setEdgeMode()` to restore mode after reload

### Key Features

#### Debouncing
- **Delay**: 800ms after the last file modification event
- **Purpose**: Prevents multiple reloads when editors write files in multiple passes
- **Benefit**: Reduces CPU usage and provides smoother user experience

#### Retry Logic
- **Attempts**: Up to 3 attempts to reload the file
- **Retry Delay**: 300ms between attempts
- **Purpose**: Handles cases where file is temporarily locked or incomplete

#### Threading
- **Watch Loop**: Runs on dedicated daemon thread `SVG-File-Watcher`
- **Reload Scheduler**: Executes reload callbacks on `SVG-Reload-Scheduler` thread
- **SVG Parsing**: Runs asynchronously in background thread pool
- **UI Updates**: Always dispatched to JavaFX Application Thread via `Platform.runLater()`

#### State Preservation
During reload, the following state is preserved:
- Active tab (2D or Pseudo 3D)
- Pseudo3D edge mode (TOP or BOTTOM)
- User's zoom and pan settings (maintained by view components)

### Configuration

Current settings (in `SvgFileWatcher`):
```java
DEBOUNCE_DELAY_MS = 800     // Wait time after last file event
MAX_RETRY_ATTEMPTS = 3       // Number of reload retry attempts
RETRY_DELAY_MS = 300        // Delay between retry attempts
```

## Usage

Simply run the application as before:
```bash
mvn javafx:run -Dexec.args="/path/to/your/file.svg"
```

The file watcher starts automatically when the application loads. Any changes to the SVG file will be detected and the view will refresh automatically.

## Testing

### Unit Tests
- `SvgFileWatcherTest`: Basic functionality and lifecycle tests
  - File modification detection
  - Debouncing behavior
  - Start/stop lifecycle
  - Idempotent operations
  - Null parameter validation

### Integration Tests
- `SvgFileWatcherIntegrationTest`: Real-world scenarios
  - Inkscape-style multiple rapid writes
  - File recreation (delete + create pattern)
  - Selective watching (only target file triggers reload)
  - Multiple save cycles in editing session

Run tests:
```bash
mvn test
```

## Limitations

- Does not detect changes if the file is moved/renamed (watch is path-specific)
- Watches only the initially loaded file (opening a new file would require app restart)
- No visual indication when reload is happening (logged to console)

## Future Enhancements

Potential improvements for future versions:
- Visual feedback when reload is in progress (e.g., status bar message)
- Configurable debounce delay via preferences
- Option to disable auto-reload
- Support for watching multiple files or switching watched file at runtime
