# Coding Guidelines

## UI and Theming

1. **Dual Theme Updates (Light / Night Mode)**
   - The application supports multiple themes (e.g., `light.style.css` and `night.style.css`).
   - **CRITICAL:** Whenever you edit a color, style, or visual property in one theme file, you **MUST** ensure the other theme files are updated accordingly to maintain consistent contrast and design.
   - For example, if you change a label's color to be visible on a dark background in Night Mode, you must explicitly check and configure its color for Light Mode to ensure it remains legible on a white/light background.
