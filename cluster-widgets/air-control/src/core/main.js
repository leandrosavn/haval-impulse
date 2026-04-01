import { getState as get, setState, subscribe } from './state.js';
import { createDashboardInfo } from './components/dashboardInfo.js';
import { createAcControlScreen } from './components/aircon/mainAcControl.js';
import { createRegenScreen } from './components/regen/regenControl.js';
import { createDisplaySelectionScreen } from './components/display/themeSelection.js';
import { createMainMenu } from './components/mainMenu.js';
import { createMask } from './components/display/mask.js';
import { createGraphScreen } from './components/graphs/graphs.js';
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


function initializeLayout() {
    logger.enter('initializeLayout');
    if (!appContainer) {
        logger.leave('initializeLayout');
        return;
    }
    appContainer.innerHTML = '';

    // Add mask background first (z-index: 50)
    const mask = createMask();
    maskComponent = mask;

    appContainer.appendChild(mask.background);

    // 1. Dashboard Info Layer
    if (dashboardCleanup) dashboardCleanup();
    const dashboardInfo = createDashboardInfo;
    const { element: dashElement, menuWrapper: newMenuWrapper, cleanup: dashCleanup } = dashboardInfo();
    appContainer.appendChild(dashElement);

    menuWrapper = newMenuWrapper;
    dashboardCleanup = dashCleanup;

    // Add no app mask on top (z-index: 200)
    appContainer.appendChild(mask.noAppL);
    appContainer.appendChild(mask.noAppR);
    logger.leave('initializeLayout');
}


function render() {
    logger.enter('render', { screen: get('screen'), display: get('display') });
    const screen = get('screen');
    const displayMode = get('display') || 'Normal';

    // Update app class based on display mode
    if (appContainer) {
        console.log('[Debug] Rendering screen:', screen);
        let classes = appContainer.className.split(' ').filter(c => !c.startsWith('display-') && !c.startsWith('theme-') && c !== 'cluster-disabled' && c !== 'warn-is-active');
        classes.push('display-' + displayMode.toLowerCase());

        if (get('clusterEnabled') === false) {
            classes.push('cluster-disabled');
        }

        if (get('cardId') === 0 || get('warningActive') === true) {
            classes.push('warn-is-active');
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
        componentResult = createMainMenu();
    } else if (screen === 'aircon') {
        componentResult = createAcControlScreen();
    } else if (screen === 'regen') {
        componentResult = createRegenScreen();
    } else if (screen === 'display_selection') {
        componentResult = createDisplaySelectionScreen();
    } else if (screen === 'graph' || screen === 'graphs') {
        componentResult = createGraphScreen();
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

subscribe('warningActive', () => render());
initializeLayout();

// Start rendering and subscribe to listen for screen changes thus triggering new render
subscribe('screen', render);
subscribe('display', render);

subscribe('clusterEnabled', render);
subscribe('cardId', render);
render();



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
    if (key !== 'carSpeed' && key !== 'engineRPM') {
        console.log(`[JS Bridge] control('${key}', ${value})`);
    }
    logger.enter('window.control', { key, value });
    let val = value;
    // Automatically convert numeric strings to numbers for compatibility with components
    if (typeof value === 'string' && value.trim() !== '' && !isNaN(value)) {
        val = Number(value);
    }
    setState(key, val);
    if (key === 'warningActive') {
        render();
    }
    logger.leave('window.control');
};

window.cleanup = function () {
    logger.enter('window.cleanup');
    if (currentComponent && currentComponent.cleanup) {
        currentComponent.cleanup();
    }
    logger.leave('window.cleanup');
};
