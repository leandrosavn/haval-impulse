import { stateManager, subscribe } from '../../../../state.js';
import { createFocusElementWithChildren } from './focusElement.js';
import { div, span, img } from '../../../../utils/createElement.js';
import { updateProgressRings } from './mainAcControl.js'

export function createTempInfoElement() {

    const container = div({ className: 'temp-info-container' });

    var externalTempLabel = div({
       className: 'temp-info-label',
       children: [ 'Outside',],
    });
    var externalTemp = div({
       className: 'temp-info-value-left',
       children: [
           stateManager.get('outside_temp') + '°C',
       ],
    });
    var internalTempLabel = div({
       className: 'temp-info-label',
       children: [ 'Inside',],
    });
    var internalTemp = div({
       className: 'temp-info-value-right',
       children: [
           stateManager.get('inside_temp') + '°C',
       ],
    });

    externalTempLabel.append(externalTemp);
    container.append(externalTempLabel);
    internalTempLabel.append(internalTemp);
    container.append(internalTempLabel);

    container.onMount = () => {
       const internalSub = subscribe('inside_temp', (newValue) => {
           internalTemp.textContent = `${newValue}°C`;
       });
       const externalSub = subscribe('outside_temp', (newValue) => {
           externalTemp.textContent = `${newValue}°C`;
       });
       const unsubscribePower = subscribe('power', function(newPower) {
           document.getElementById('ac-power-icon').source = (newPower == 1 ? acON : acOFF);
       });

       return () => {
           internalSub();
           externalSub();
           unsubscribePower()
       };
    };

    return container;

}

