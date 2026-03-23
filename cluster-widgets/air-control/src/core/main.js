import { getState as get, setState, subscribe } from './state.js';
import { themeEngine } from './themeEngine.js';
import { style, theme as padraoTheme } from '../themes/padrao/index.js';
import { div } from '../utils/createElement.js';
import { logger } from '../utils/logger.js';
import { initializeConstants } from '../utils/constants.js';

initializeConstants();

if (process.env.NODE_ENV === 'development') {
    document.body.style.backgroundColor = 'red';
    import('../utils/testing-utils.js');
}

const appContainer = document.getElementById('app');
let currentComponent = null;
let maskComponent = null;
let menuWrapper = null;
let dashboardCleanup = null;

// Initial state from URL parameters
const urlParams = new URLSearchParams(window.location.search);
const modeParam = urlParams.get('mode');
if (modeParam === 'light') {
    setState('nightMode', false);
} else if (modeParam === 'night') {
    setState('nightMode', true);
}


function initializeLayout() {
    logger.enter('initializeLayout');
    if (!appContainer) {
        logger.leave('initializeLayout');
        return;
    }
    appContainer.innerHTML = '';

    // Load default theme with initial nightMode
    updateThemeStyles();

    // Add mask background first (z-index: 50)
    const createMask = themeEngine.getComponent('mask');
    const mask = createMask();
    maskComponent = mask;

    appContainer.appendChild(mask.background);

    // 1. Dashboard Info Layer
    if (dashboardCleanup) dashboardCleanup();
    const dashboardInfo = themeEngine.getComponent('dashboardInfo');
    const { element: dashElement, menuWrapper: newMenuWrapper, cleanup: dashCleanup } = dashboardInfo();
    appContainer.appendChild(dashElement);

    menuWrapper = newMenuWrapper;
    dashboardCleanup = dashCleanup;

    // Add no app mask on top (z-index: 200)
    appContainer.appendChild(mask.noAppV);
    appContainer.appendChild(mask.noAppH);
    logger.leave('initializeLayout');
}

function render() {
    logger.enter('render', { screen: get('screen'), display: get('display') });
    const screen = get('screen');
    const displayMode = get('display') || 'Normal';

    // Update app class based on display mode
    if (appContainer) {
        const isNight = get('nightMode');
        console.log('[Debug] Rendering with nightMode:', isNight, 'screen:', screen);
        let classes = appContainer.className.split(' ').filter(c => !c.startsWith('display-') && !c.startsWith('theme-') && c !== 'cluster-disabled' && c !== 'card-is-0');
        classes.push('display-' + displayMode.toLowerCase());
        classes.push(isNight ? 'theme-night' : 'theme-light');
        
        if (get('clusterEnabled') === false) {
            classes.push('cluster-disabled');
        }

        if (get('cardId') === 0) {
            classes.push('card-is-0');
        }

        appContainer.className = classes.join(' ').trim();
        console.log('[Debug] App classes:', appContainer.className);
    }

    if (currentComponent && currentComponent.cleanup) {
        currentComponent.cleanup();
    }

    if (menuWrapper) {
        menuWrapper.innerHTML = '';
    }

    let componentResult = null;
    if (screen === 'main_menu') {
        componentResult = themeEngine.getComponent('mainMenu')();
    } else if (screen === 'aircon') {
        componentResult = themeEngine.getComponent('aircon')();
    } else if (screen === 'regen') {
        componentResult = themeEngine.getComponent('regen')();
    } else if (screen === 'display_selection') {
        componentResult = themeEngine.getComponent('displaySelection')();
    } else if (screen === 'graph' || screen === 'graphs') {
        componentResult = themeEngine.getComponent('graph')();
    }

    if (componentResult) {
        const element = componentResult.element || componentResult;
        const onMount = componentResult.onMount;

        if (menuWrapper) {
            menuWrapper.appendChild(element);
        }

        if (onMount) {
            onMount();
        }
        
        currentComponent = componentResult;
    } else {
        currentComponent = null;
    }
    logger.leave('render');
}

initializeLayout();

// Start rendering and subscribe to listen for screen changes thus triggering new render
subscribe('screen', render);
subscribe('display', render);
subscribe('nightMode', (isNight) => {
    updateThemeStyles();
    render();
});
subscribe('clusterEnabled', render);
subscribe('cardId', render);
render();

function updateThemeStyles() {
    const isNight = get('nightMode') !== false;
    logger.log('[Theme] Updating theme styles for nightMode:', isNight);
    themeEngine.loadTheme(padraoTheme, isNight);
}

// Handle Card ID transitions
subscribe('cardId', (cardId) => {
    logger.log('cardId change:', cardId);
    console.log('Actual Card:', cardId);
    // 0 = hide the right menu display
    if (menuWrapper) {
        menuWrapper.style.display = (cardId == 0) ? 'none' : 'block';
    }

    if (cardId == 1) {
        // 1 = go to main regular menu
        setState('screen', 'main_menu');
    } else if (cardId == 3) {
        // 3 = set to AC menu
        setState('screen', 'aircon');
    }
});

// Functions used by Kotlin to trigger interactions
window.showScreen = function (screenName) {
    logger.enter('window.showScreen', screenName);
    setState('screen', screenName);
    logger.leave('window.showScreen');
};

window.focus = function (item) {
    logger.enter('window.focus', item);
    const screen = get('screen');
    if (screen === 'main_menu') {
        setState('focusedMenuItem', item);
    } else if (screen === 'aircon') {
        setState('focusArea', item);
    } else if (screen === 'display_selection') {
        setState('displayFocus', item);
    }
    logger.leave('window.focus');
};

window.control = function (key, value) {
    logger.enter('window.control', { key, value });
    let val = value;
    // Automatically convert numeric strings to numbers for compatibility with components
    if (typeof value === 'string' && value.trim() !== '' && !isNaN(value)) {
        val = Number(value);
    }
    setState(key, val);
    logger.leave('window.control');
};

window.cleanup = function () {
    logger.enter('window.cleanup');
    if (currentComponent && currentComponent.cleanup) {
        currentComponent.cleanup();
    }
    logger.leave('window.cleanup');
};
