import { getState as get, subscribe } from '../../state.js';
import { div } from '../../utils/createElement.js';

export function createMask() {
    // Background layer (Bars and Solid Circles) - z-index: 50
    const maskBg = div({ className: 'cluster-mask-bg' });
    const topBar = div({ className: 'mask-top-bar' });
    const bottomBar = div({ className: 'mask-bottom-bar' });
    const leftCircle = div({ className: 'mask-circle left' });
    const rightCircle = div({ className: 'mask-circle right' });

    maskBg.appendChild(topBar);
    maskBg.appendChild(bottomBar);
    maskBg.appendChild(leftCircle);
    maskBg.appendChild(rightCircle);

    // Foreground layer (Borders) - z-index: 150
    const maskFg = div({ className: 'cluster-mask' });
    const leftCircleBorder = div({ className: 'mask-circle-border left' });
    const rightCircleBorder = div({ className: 'mask-circle-border right' });

    maskFg.appendChild(leftCircleBorder);
    maskFg.appendChild(rightCircleBorder);

    // No App Mask layer 
    const noAppMaskV = div({ className: 'no-app-mask-v' });
    const noAppMaskH = div({ className: 'no-app-mask-h' });
    const partialAppMask = div({ className: 'partial-app-mask' });

    maskBg.appendChild(partialAppMask);

    const updateVisibility = () => {
        const maskMode = get('mask'); // 0=none, 1=partial, 2=full
        const cardId = get('cardId');
        const rightVisible = (cardId !== 0);

        // maskMode 0: All off
        // maskMode 1: Standard mask on. noAppMask off.
        // maskMode 2: Standard mask on + noAppMask on.

        if (rightVisible) {
            //maskFg.style.opacity = (maskMode > 0) ? '1' : '0';
            //maskBg.style.opacity = (maskMode > 0) ? '1' : '0';
            noAppMaskV.style.opacity = (maskMode === 2) ? '1' : '0';
            noAppMaskH.style.opacity = (maskMode === 2) ? '1' : '0';

            //maskFg.style.pointerEvents = (maskMode > 0) ? 'auto' : 'none';
            //maskBg.style.pointerEvents = (maskMode > 0) ? 'auto' : 'none';
            noAppMaskV.style.pointerEvents = (maskMode === 2) ? 'auto' : 'none';
            noAppMaskH.style.pointerEvents = (maskMode === 2) ? 'auto' : 'none';

            // Additional safeguard: ensure noAppMask is logically hidden if maskMode < 2
            noAppMaskV.style.visibility = (maskMode === 2) ? 'visible' : 'hidden';
            noAppMaskH.style.visibility = (maskMode === 2) ? 'visible' : 'hidden';

        }


        if (maskMode === 1) {
            // Hide right circle if cardId is 0
            //rightCircle.style.opacity = rightVisible ? '1' : '0';
            //rightCircleBorder.style.opacity = rightVisible ? '1' : '0';

            // Show partial app mask if cardId is 0 (Transparency Mode)
            partialAppMask.style.opacity = (!rightVisible) ? '1' : '0';
        } else {
            //rightCircle.style.opacity = rightVisible ? '1' : '0';
            //rightCircleBorder.style.opacity = rightVisible ? '1' : '0';
            partialAppMask.style.opacity = '0';
        }
    };

    const unsub1 = subscribe('mask', updateVisibility);
    const unsub2 = subscribe('cardId', updateVisibility);
    updateVisibility();

    // Use a combined object for cleanup
    const result = {
        background: maskBg,
        foreground: maskFg,
        noAppV: noAppMaskV,
        noAppH: noAppMaskH,
        partial: partialAppMask,
        cleanup: () => {
            unsub1();
            unsub2();
        }
    };

    return result;
}
