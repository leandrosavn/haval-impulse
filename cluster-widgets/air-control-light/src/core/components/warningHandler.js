import { stateManager } from './../state.js';
import { logger } from './../../utils/logger.js';

const VISUAL_ONLY_KEYS = [
    "car.ipk_info.bsd_lca_warning_reqleft",
    "car.ipk_info.bsd_lca_warning_reqright"
];

export function initWarningHandler() {
    window.updateWarning = function(key, value) {
        const state = stateManager.getState();
        const warnings = state.warnings || {};
        const newWarnings = Object.assign({}, warnings, { [key]: value });
        stateManager.set('warnings', newWarnings);
        
        let hasCriticalWarning = false;
        
        for (const [k, v] of Object.entries(newWarnings)) {
            const isThisActive = v !== "0" && v !== "{0,0,0,0}" && v !== "{0,0,0,0,0}" && v !== "" && v !== "false";
            
            if (isThisActive && !VISUAL_ONLY_KEYS.includes(k)) {
                hasCriticalWarning = true;
            }
            
            if (k === "car.ipk_info.bsd_lca_warning_reqleft") {
                stateManager.set('bsdLeft', isThisActive);
            }
            if (k === "car.ipk_info.bsd_lca_warning_reqright") {
                stateManager.set('bsdRight', isThisActive);
            }
        }
        
        const currentActive = stateManager.get('warningActive');
        const cardId = stateManager.get('cardId');
        const shouldBeWarnActive = hasCriticalWarning || cardId == 0;

        if (currentActive !== hasCriticalWarning) {
            stateManager.set('warningActive', hasCriticalWarning);
        }

        // Android size handling
        if (window.Android && window.Android.setWarningActive) {
            window.Android.setWarningActive(shouldBeWarnActive);
        }
    };

    window.clearWarnings = function() {
        logger.log('Clearing all warnings via DISMISS_WARNING');
        stateManager.set('warnings', {});
        stateManager.set('warningActive', false);
        
        const cardId = stateManager.get('cardId');
        const shouldBeWarnActive = cardId == 0; // Still active if card is 0

        if (window.Android && window.Android.setWarningActive) {
            window.Android.setWarningActive(shouldBeWarnActive);
        }
    };
}
