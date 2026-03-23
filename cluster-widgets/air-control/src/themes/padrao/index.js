import style from 'url:./styles/dashboard.style.css';
import nightStyle from 'url:./styles/night.style.css';
import lightStyle from 'url:./styles/light.style.css';
import componentsStyle from 'url:./styles/components.style.css';
import mainMenuStyle from 'url:./styles/main.menu.css';
import { createDashboardInfo } from './components/dashboardInfo.js';
import { createAcControlScreen } from './components/aircon/mainAcControl.js';
import { createRegenScreen } from './components/regen/regenControl.js';
import { createDisplaySelectionScreen } from './components/display/themeSelection.js';
import { createMainMenu } from './components/mainMenu.js';
import { createMask } from './components/display/mask.js';
import { createGraphScreen } from './components/graphs/graphs.js';

export { style };

export const theme = {
    name: 'padrao',
    styles: {
        common: [style, componentsStyle, mainMenuStyle],
        night: [nightStyle],
        light: [lightStyle]
    },
    components: {
        mask: createMask,
        dashboardInfo: createDashboardInfo,
        aircon: createAcControlScreen,
        regen: createRegenScreen,
        displaySelection: createDisplaySelectionScreen,
        mainMenu: createMainMenu,
        graph: createGraphScreen
    }
};
