import {getState as get, setState, subscribe} from './state.js';
import {createMainMenu} from './components/mainMenu.js';
import {createAcControlScreen, updateProgressRings as updateProgressRingsAC} from "./components/aircon/mainAcControl.js";
import {createRegenScreen, updateProgressRings as updateProgressRingsRegen } from "./components/regen/regenControl.js";
import {createGraphScreen } from "./components/graphs/graphs.js";
import { createMask } from './components/mask.js';
import { createDashboardInfo } from './components/dashboardInfo.js';
import { div } from './utils/createElement.js';

if (process.env.NODE_ENV === 'development') {
    import('./testing-utils.js');
}

const appContainer = document.getElementById('app');
let currentComponent = null;
let maskComponent = null;

// Create container for dynamic content to separate from persistent mask
const contentContainer = div({ className: 'content-container' });

function initializeLayout() {
    if (!appContainer) return;
    appContainer.innerHTML = '';
    
    // Add mask background first (z-index: 50)
    const mask = createMask();
    maskComponent = mask;
    
    appContainer.appendChild(mask.background);
    
    // Add content container (z-index: 100)
    appContainer.appendChild(contentContainer);
    
    // Add Dashboard Info (Gauges, Clock, etc.) (z-index: 140)
    const dashboardInfo = createDashboardInfo();
    appContainer.appendChild(dashboardInfo);
    
    // Add mask foreground on top (z-index: 150)
    appContainer.appendChild(mask.foreground);
}

function render() {
    const screen = get('screen');

    if (currentComponent && currentComponent.cleanup) {
        currentComponent.cleanup();
    }

    contentContainer.innerHTML = '';

    if (screen === 'main_menu') {
        currentComponent = createMainMenu();
    } else if (screen === 'aircon') {
        currentComponent = createAcControlScreen();
    } else if (screen === 'regen') {
        currentComponent = createRegenScreen();
    } else if (screen === 'graph') {
        currentComponent = createGraphScreen();
    }

    if (currentComponent) {
        const element = currentComponent.element || currentComponent;
        const onMount = currentComponent.onMount;
        currentComponent = element;
        
        // Wrap in positioned container to maintain (1630, 430) placement
        const wrapper = div({ className: 'menu-positioned-container' }, element);
        contentContainer.appendChild(wrapper);
        
        if (onMount) {
            onMount();
        }
    }
}

initializeLayout();

// Start rendering and subscribe to listen for screen changes thus triggering new render
subscribe('screen', render);
render();

// Handle Card ID transitions
subscribe('cardId', (cardId) => {
    console.log('Actual Card:', cardId);
    // 0 = hide the right menu display
    contentContainer.style.display = (cardId === 0) ? 'none' : 'block';

    if (cardId === 1) {
        // 1 = go to main regular menu
        setState('screen', 'main_menu');
    } else if (cardId === 3) {
        // 3 = set to AC menu
        setState('screen', 'aircon');
    }
});


// Functions used by Kotlin to trigger interactions
window.showScreen = function(screenName) {
    setState('screen', screenName);
};

window.focus = function(item) {
    const screen = get('screen');
    if (screen === 'main_menu') {
        setState('focusedMenuItem', item);
    } else if (screen === 'aircon') {
        setState('focusArea', item);
    }
};

window.control = function(key, value) {
    setState(key, value);
};

window.cleanup = function() {
    if (currentComponent && currentComponent.cleanup) {
        currentComponent.cleanup();
    }
};
