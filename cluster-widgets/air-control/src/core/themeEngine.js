export class ThemeEngine {
    constructor() {
        this.currentTheme = null;
        this.loadedStyles = [];
    }

    async loadTheme(theme, isNight = false) {
        if (this.currentTheme && this.currentTheme.name === theme.name) {
            this.updateStyles(isNight);
            return;
        }

        this.currentTheme = theme;
        this.updateStyles(isNight);
    }

    updateStyles(isNight) {
        if (!this.currentTheme) return;
        
        console.log('[ThemeEngine] Updating styles for nightMode:', isNight);
        this.unloadCurrentThemeStyles();

        const styles = this.currentTheme.styles;
        let toLoad = [];
        
        if (Array.isArray(styles)) {
            toLoad = styles;
        } else if (styles) {
            toLoad = [
                ...(styles.common || []),
                ...(isNight ? (styles.night || []) : (styles.light || []))
            ];
        }

        this.loadStyles(toLoad);
    }

    loadStyles(styles) {
        styles.forEach(href => {
            let processedHref = href;
            
            // Fix for Android WebView prepending asset path to Data URIs
            // e.g. file:///android_asset/data:text/css;base64,...
            if (href.includes('data:text/css;base64,')) {
                const dataIndex = href.indexOf('data:text/css;base64,');
                if (dataIndex > 0) {
                    processedHref = href.substring(dataIndex);
                    console.log('[ThemeEngine] Stripped prefix from Data URI:', href.substring(0, dataIndex));
                }
            }

            console.log('[ThemeEngine] Loading style:', processedHref.substring(0, 50) + (processedHref.length > 50 ? '...' : ''));
            
            const link = document.createElement('link');
            link.rel = 'stylesheet';
            link.href = processedHref;
            link.className = 'theme-stylesheet';
            document.head.appendChild(link);
            this.loadedStyles.push(link);
        });
    }

    unloadCurrentThemeStyles() {
        // Clear explicitly tracked styles
        this.loadedStyles.forEach(link => {
            if (link.parentNode) {
                link.parentNode.removeChild(link);
            }
        });
        
        // Final safety: remove ANY residual theme stylesheet class from the head
        // this is useful if HMR or manual manipulation causes drift
        document.querySelectorAll('.theme-stylesheet').forEach(el => el.remove());

        this.loadedStyles = [];
    }

    getComponent(name) {
        if (!this.currentTheme || !this.currentTheme.components[name]) {
            console.error(`Component ${name} not found in theme ${this.currentTheme?.name}`);
            return null;
        }
        return this.currentTheme.components[name];
    }
}

export const themeEngine = new ThemeEngine();
