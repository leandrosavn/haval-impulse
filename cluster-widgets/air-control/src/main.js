import { getState as get, setState, subscribe } from './state.js';
import { createMainMenu } from './components/mainMenu.js';
import { createAcControlScreen, updateProgressRings as updateProgressRingsAC } from "./components/aircon/mainAcControl.js";
import { createRegenScreen, updateProgressRings as updateProgressRingsRegen } from "./components/regen/regenControl.js";
import { createGraphScreen } from "./components/graphs/graphs.js";
import { createMask } from './components/display/mask.js';
import { createDashboardInfo } from './components/dashboardInfo.js';
import { createDisplaySelectionScreen } from './components/display/themeSelection.js';
import { div } from './utils/createElement.js';
import './dashboard.style.css';

if (process.env.NODE_ENV === 'development') {
    import('./testing-utils.js');
}

const appContainer = document.getElementById('app');
let currentComponent = null;
let maskComponent = null;
let menuWrapper = null;

function initializeLayout() {
    if (!appContainer) return;
    appContainer.innerHTML = '';

    // Add mask background first (z-index: 50)
    const mask = createMask();
    maskComponent = mask;

    appContainer.appendChild(mask.background);

    // Add Dashboard Info (Gauges, Clock, etc.) (z-index: 140)
    const dashboardInfo = createDashboardInfo();
    menuWrapper = dashboardInfo.menuWrapper;
    appContainer.appendChild(dashboardInfo.container);

    // Add mask foreground on top (z-index: 150)
    appContainer.appendChild(mask.foreground);

    // Add no app mask on top (z-index: 200)
    appContainer.appendChild(mask.noAppV);
    appContainer.appendChild(mask.noAppH);
}

function render() {
    const screen = get('screen');
    const displayMode = get('display') || 'Normal';

    // Update app class based on display mode
    if (appContainer) {
        appContainer.className = (appContainer.className.split(' ').filter(c => !c.startsWith('display-')).join(' ') + ' display-' + displayMode.toLowerCase()).trim();
    }

    if (currentComponent && currentComponent.cleanup) {
        currentComponent.cleanup();
    }

    if (menuWrapper) {
        menuWrapper.innerHTML = '';
    }

    if (screen === 'main_menu') {
        currentComponent = createMainMenu();
    } else if (screen === 'aircon') {
        currentComponent = createAcControlScreen();
    } else if (screen === 'regen') {
        currentComponent = createRegenScreen();
    } else if (screen === 'graph') {
        currentComponent = createGraphScreen();
    } else if (screen === 'display_selection') {
        currentComponent = createDisplaySelectionScreen();
    }

    if (currentComponent) {
        const element = currentComponent.element || currentComponent;
        const onMount = currentComponent.onMount;
        currentComponent = element;

        if (menuWrapper) {
            menuWrapper.appendChild(element);
        }

        if (onMount) {
            currentComponent.cleanup = onMount();
        }
    }
}

initializeLayout();

// Start rendering and subscribe to listen for screen changes thus triggering new render
subscribe('screen', render);
subscribe('display', render);
render();

// Handle Card ID transitions
subscribe('cardId', (cardId) => {
    console.log('Actual Card:', cardId);
    // 0 = hide the right menu display
    if (menuWrapper) {
        menuWrapper.style.display = (cardId === 0) ? 'none' : 'block';
    }

    if (cardId === 1) {
        // 1 = go to main regular menu
        setState('screen', 'main_menu');
    } else if (cardId === 3) {
        // 3 = set to AC menu
        setState('screen', 'aircon');
    }
});



// Functions used by Kotlin to trigger interactions
window.showScreen = function (screenName) {
    setState('screen', screenName);
};

window.focus = function (item) {
    const screen = get('screen');
    if (screen === 'main_menu') {
        setState('focusedMenuItem', item);
    } else if (screen === 'aircon') {
        setState('focusArea', item);
    } else if (screen === 'display_selection') {
        setState('displayFocus', item);
    }
};

window.control = function (key, value) {
    setState(key, value);
};

window.cleanup = function () {
    if (currentComponent && currentComponent.cleanup) {
        currentComponent.cleanup();
    }
};
