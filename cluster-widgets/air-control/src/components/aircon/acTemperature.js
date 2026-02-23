import { stateManager, subscribe } from '../../state.js';
import { createFocusElementWithChildren } from './focusElement.js';
import { div } from '../../utils/createElement.js';
import { updateProgressRings } from './mainAcControl.js'

export function createTemperatureElement() {

    function getTempDisplayValue(temp) {
        if (temp == 16) return 'Low';
        if (temp == 32) return 'High';
        return temp + '°C';
    }

    var tempDisplay = div({
        className: 'temp-display-label font-bold',
        children: [
            getTempDisplayValue(stateManager.get('temp')),
        ],
    });
    var focusArea = createFocusElementWithChildren({
        className: 'ac-temp-control-area',
        focused: stateManager.get('focusArea') === 'temp',
        children: [
            div({
                className: 'text-white text-center',
                children: [div({
                    className: 'text-20 text-gray',
                    children: [
                        'Temp',
                    ],
                }), tempDisplay],
            }),
        ]
    });

    var unsubscribeFocus = subscribe('focusArea', function (newFocusArea) {
        var isFocused = newFocusArea === 'temp';

        // Apply base styles with browser prefixes
        //focusArea.className = 'focus-area focus-area-small';

        if (isFocused) {
            focusArea.classList.add('transition-all', 'focus-active');
            focusArea.classList.remove('focus-inactive');
        } else {
            focusArea.classList.add('focus-inactive');
            focusArea.classList.remove('transition-all', 'focus-active');
        }
    });

    var unsubscribeTemp = subscribe('temp', function (newTemp) {
        tempDisplay.textContent = getTempDisplayValue(newTemp);
        updateProgressRings();
    });

    // Add cleanup method to the element
    focusArea.cleanup = function () {
        unsubscribeFocus();
        unsubscribeTemp();
    };

    return focusArea;
}
