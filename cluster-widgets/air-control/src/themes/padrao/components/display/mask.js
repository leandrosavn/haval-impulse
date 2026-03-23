import { getState as get, subscribe } from '../../../../state.js';
import { div } from '../../../../utils/createElement.js';

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
    //const maskFg = div({ className: 'cluster-mask' });
    const leftCircleBorder = div({ className: 'mask-circle-border left' });
    const rightCircleBorder = div({ className: 'mask-circle-border right' });

    maskBg.appendChild(leftCircleBorder);
    maskBg.appendChild(rightCircleBorder);

    // No App Mask layer 
    const noAppMaskV = div({ className: 'no-app-mask-v' });
    const noAppMaskH = div({ className: 'no-app-mask-h' });
    const partialAppMask = div({ className: 'partial-app-mask' });

    maskBg.appendChild(partialAppMask);

    const updateVisibility = () => {
        const appInDash = get('appInDash'); 
        const cardId = get('cardId');
        const rightVisible = (cardId !== 0);

        if (!appInDash) {
            noAppMaskV.style.opacity = '1';
            noAppMaskH.style.opacity = '1';
            noAppMaskV.style.pointerEvents = 'auto';
            noAppMaskH.style.pointerEvents = 'auto';
            noAppMaskV.style.visibility = 'visible';
            noAppMaskH.style.visibility = 'visible';
            
            const targetWidth = (cardId === 0) ? '1344px' : '1920px';
            noAppMaskV.style.width = targetWidth;
            noAppMaskH.style.width = targetWidth;
        } else {
            noAppMaskV.style.opacity = '0';
            noAppMaskH.style.opacity = '0';
            noAppMaskV.style.pointerEvents = 'none';
            noAppMaskH.style.pointerEvents = 'none';
            noAppMaskV.style.visibility = 'hidden';
            noAppMaskH.style.visibility = 'hidden';
            
            noAppMaskV.style.width = '1920px';
            noAppMaskH.style.width = '1920px';
        }

        partialAppMask.style.opacity = (!rightVisible) ? '1' : '0';
    };

    const unsub1 = subscribe('appInDash', updateVisibility);
    const unsub2 = subscribe('cardId', updateVisibility);
    updateVisibility();

    // Use a combined object for cleanup
    const result = {
        background: maskBg,
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

