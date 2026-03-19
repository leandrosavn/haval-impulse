# Air Control Architecture

This document describes the architecture of the `air-control` widget within the `cluster-widgets` system.

## Component Overview

The `air-control` widget provides the user interface for vehicle air conditioning controls. It is a dedicated web application running in a high-performance webview container on the vehicle's multimedia platform.

### Key Entry Points
- `app-night.html`: The dark/night mode entry point.
- `app-light.html`: The light mode entry point.
- `index.html`: A development-only entry point with testing controls.

## Design Patterns & Technology Stack

- **UI Framework:** Vanilla JavaScript & CSS for performance on embedded systems.
- **Build System:** [Parcel](https://parceljs.org/) for fast bundling and optimization.
- **Asset Inlining:** To overcome potential loading bottlenecks in the embedded webview, the custom `inline.js` script embeds all external CSS and JS directly into the single HTML output files.
- **Visualization:** Utilizes [Chart.js](https://www.chartjs.org/) for real-time data visualization if needed.
- **Data Handling:** `date-fns` for robust date and time manipulation.

## Embedded Integration (Backend vs Webview)

The component follows a **Host-Client** model:
- **Host (Android Backend):** Manages the webview lifecycle, provides data streams via a JavaScript bridge, and maps UI interactions to hardware commands.
- **Client (Webview UI):** Responsible for rendering the state provided by the bridge and emitting events for user actions.

## Security Considerations

- **Execution Isolation:** The webview should be configured to only allow communication with the host app.
- **Content Security:** All assets are bundled and inlined to prevent external resource loading vulnerabilities.

## Development Guidelines

- **Local Simulation:** Use `npm run dev-controls` to test with simulated hardware events.
- **Build Integrity:** Ensure `npm run build` is run before deployment to generate the necessary inlined versions.
- **Compatibility:** Always maintain support for older browser versions (Android 4.4 / Chrome 50) as specified in `package.json`.
