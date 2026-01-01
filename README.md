# Corset Viewer

<img width="1272" height="781" alt="image" src="https://github.com/user-attachments/assets/019e1fe0-6c69-4f86-b246-f42b1c31d5e5" />
<img width="1272" height="818" alt="image" src="https://github.com/user-attachments/assets/e5757527-fbd1-40a7-ad45-561409f04d0c" />
<img width="1274" height="690" alt="image" src="https://github.com/user-attachments/assets/94112cfd-78c7-4925-aa03-0ca2d8f97932" />
<img width="820" height="834" alt="image" src="https://github.com/user-attachments/assets/b343dae6-326c-4c9c-b217-a740bd5acc1c" />




Corset Viewer is a small JavaFX tool for viewing and validating corset panel geometry exported as an SVG file.  
It reads specific `<path>` elements by their `id` attributes, samples them into polylines, and renders the result in 2D (and a pseudo‑3D chained view).

## Downloads

Prebuilt portable packages are published on the **Releases** page:

- https://github.com/arsi-apli/corset-viewer/releases

Each release contains:
- `corset-viewer-<version>-linux.zip`
- `corset-viewer-<version>-win.zip`
- `corset-viewer-<version>-mac.zip`

### Running (portable ZIP)

#### Linux
```bash
unzip corset-viewer-<version>-linux.zip
./CorsetViewer/bin/CorsetViewer
```

#### Windows
Unzip and run:
- `CorsetViewer\CorsetViewer.exe` (or `CorsetViewer\bin\CorsetViewer.exe` depending on the app-image layout)

#### macOS
Unzip and open:
- `CorsetViewer.app`

## Security prompts (Windows / macOS)

This project currently publishes **unsigned** binaries. That is normal for early development, but it may trigger OS warnings.

### Windows SmartScreen
You may see a warning like *“Windows protected your PC”*.

To run anyway:
1. Click **More info**
2. Click **Run anyway**

### macOS Gatekeeper
You may see a warning that the app can’t be opened because it is from an unidentified developer.

Options:
- **Right‑click** the app → **Open** → confirm **Open**
- Or go to **System Settings → Privacy & Security** and allow the app from there

## Building from source

Requirements:
- Java 17+
- Maven

Build:
```bash
mvn clean package
```

Run (via Maven):
```bash
mvn javafx:run -Dexec.args="path/to/corset.svg"
```

## SVG input format (contract)

Corset Viewer expects a single SVG file that contains specific `<path>` elements identified by `id`.
If any required ID is missing, the app fails with an error like: `Missing required SVG element id=...`.

### 1) No `transform` on geometry (important)

SVG paths used by Corset Viewer **must not rely on `transform`** (on `<path>` or parent `<g>`), because the tool samples the raw `d` attribute coordinates.

If your SVG contains transforms, you should “apply” them into the path data and remove the transform.

**Inkscape tip:** you can usually remove/apply transforms via:
`Extensions → Modify Path → Apply Transform`

**A wizard has been added that allows visual assignment of element IDs.**
<img width="820" height="836" alt="image" src="https://github.com/user-attachments/assets/c6e31e81-7110-4ad2-a95b-433f5d7cb882" />


### 2) Panels A–F

The tool reads exactly 6 panels identified as:

- `A`, `B`, `C`, `D`, `E`, `F`

Each panel must provide these curves as `<path id="...">`:

- **Top edge:** `<path id="A_TOP">` ... `<path id="F_TOP">`
- **Bottom edge:** `<path id="A_BOTTOM">` ... `<path id="F_BOTTOM">`
- **Waist line:** `<path id="A_WAIST">` ... `<path id="F_WAIST">`

### 3) Seams (split into UP/DOWN)

Seams are defined between adjacent panels and must be split into two separate curves:
- `_UP` (above the waist)
- `_DOWN` (below the waist)

The seam IDs are:

**Panel A**
- Seam to previous (outer/busk side): `AA_UP`, `AA_DOWN`
- Seam to next: `AB_UP`, `AB_DOWN`

**Panel B**
- To previous: `BA_UP`, `BA_DOWN`
- To next: `BC_UP`, `BC_DOWN`

**Panel C**
- To previous: `CB_UP`, `CB_DOWN`
- To next: `CD_UP`, `CD_DOWN`

**Panel D**
- To previous: `DC_UP`, `DC_DOWN`
- To next: `DE_UP`, `DE_DOWN`

**Panel E**
- To previous: `ED_UP`, `ED_DOWN`
- To next: `EF_UP`, `EF_DOWN`

**Panel F**
- To previous: `FE_UP`, `FE_DOWN`
- To next (outer/lacing side): `FF_UP`, `FF_DOWN`

### 4) Element type requirements

All required elements must be `<path>` elements.  
If an ID exists but is not a `<path>`, the app fails with an error like:

`Element id=<ID> must be <path>, but is <...>`

## Notes / troubleshooting

- **Duplicate IDs**: if the SVG contains duplicate `id` values, the last one wins (a warning is logged).
- If a curve is empty (missing/empty `d` attribute), the app fails with `Empty path 'd' for id=...`.

## License

(Define license here if/when you add one.)
