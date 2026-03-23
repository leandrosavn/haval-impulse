import nightStyle from 'url:./styles/night.style.css';
import lightStyle from 'url:./styles/light.style.css';
import style from 'url:./styles/dashboard.style.css';
import mainMenuStyle from 'url:./styles/main.menu.css';
import { createDashboardInfo } from '../../core/components/dashboardInfo.js';
import { createAcControlScreen } from '../../core/components/aircon/mainAcControl.js';
import { createRegenScreen } from '../../core/components/regen/regenControl.js';
import { createDisplaySelectionScreen } from '../../core/components/display/themeSelection.js';
import { createMainMenu } from '../../core/components/mainMenu.js';
import { createMask } from '../../core/components/display/mask.js';
import { createGraphScreen } from '../../core/components/graphs/graphs.js';

export { style };

export const theme = {
    name: 'padrao',
    styles: {
        common: [style, mainMenuStyle],
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
