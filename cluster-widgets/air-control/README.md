# Cluster Air UI

UI for the vehicle's air conditioning cluster widgets.

## Architecture

This project follows a **Webview Frontend** architecture, designed to run within the car's multimedia system (Android backend).

- **Frontend:** A web application built with HTML, CSS, and JavaScript, bundled using [Parcel](https://parceljs.org/).
- **Theming:** Supports both `app-light.html` and `app-night.html` themes.
- **Backend Communication:** The UI runs inside a webview in the main Android application. To ensure compatibility and performance in the car's environment, all CSS and JS assets are inlined into a single HTML file during the build process.
- **Compatibility:** Targets Android 4.4 and Chrome 50 to maintain compatibility with the vehicle's hardware.

## Local Testing

To test the controls and UI behavior locally:

```bash
npm run dev-controls
```

This will start a development server (usually at `http://localhost:1234`) using `index.html`, which provides the necessary controls for simulation (managed under testing-utils.js).

## Build and Compilation

To generate the final version for the car, follow these mandatory steps:

1. **Build the assets:**
   ```bash
   npm run build
   ```
   This command clears the `dist` folder, builds both theme entry points, and runs `inline.js` to bundle all CSS and JS directly into the HTML files.

2. **Deploy to the Android App (Mandatory for the car):**
   ```bash
   # For the night theme (default)
   cp .\dist\app-night.html ..\..\app\src\main\res\raw\app.html
   ```

   > [!IMPORTANT]
   > The step above is mandatory to make the UI work correctly in the car's multimedia system.
