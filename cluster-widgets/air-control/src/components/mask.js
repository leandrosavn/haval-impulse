import { getState as get, subscribe } from '../state.js';
import { div } from '../utils/createElement.js';

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

    const updateVisibility = () => {
        const visible = get('maskVisible');
        maskFg.style.opacity = visible ? '1' : '0';
        maskBg.style.opacity = visible ? '1' : '0';
    };

    const unsubscribe = subscribe('maskVisible', updateVisibility);
    updateVisibility();

    // Use a combined object for cleanup
    const result = {
        background: maskBg,
        foreground: maskFg,
        cleanup: () => {
            unsubscribe();
        }
    };

    return result;
}
