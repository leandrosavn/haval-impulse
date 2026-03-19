import { getState, setState, subscribe } from '../../state.js';
import { div, span, img } from '../../utils/createElement.js';

const iconNormal = `data:image/svg+xml;base64,${btoa('<svg width="48" height="48" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><rect x="2" y="5" width="20" height="14" rx="2" stroke="#00A0E0" stroke-width="2"/><circle cx="8" cy="12" r="3" stroke="#00A0E0" stroke-width="1.5"/><circle cx="16" cy="12" r="3" stroke="#00A0E0" stroke-width="1.5"/></svg>')}`;
const iconReduced = `data:image/svg+xml;base64,${btoa('<svg width="48" height="48" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><rect x="2" y="5" width="20" height="14" rx="2" stroke="#00A0E0" stroke-width="2"/><circle cx="8" cy="12" r="1.5" stroke="#00A0E0" stroke-width="1.5"/><circle cx="16" cy="12" r="1.5" stroke="#00A0E0" stroke-width="1.5"/></svg>')}`;
const iconClean = `data:image/svg+xml;base64,${btoa('<svg width="48" height="48" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><rect x="2" y="5" width="20" height="14" rx="2" stroke="#00A0E0" stroke-width="2"/></svg>')}`;

export function createDisplaySelectionScreen() {
    const main = document.createElement('main');
    main.className = 'main-container';

    const container = div({ className: 'main-menu-container' });
    const carousel = div({ className: 'menu-carousel' });
    container.appendChild(carousel);
    main.appendChild(container);

    const menuItemsData = [
        { id: 'title_mask', label: 'Modos do Tema', type: 'title' },
        { id: 'mode_normal', label: 'Normal', type: 'mode', value: 'Normal', iconSrc: iconNormal },
        { id: 'mode_reduzido', label: 'Reduzido', type: 'mode', value: 'Reduzido', iconSrc: iconReduced },
        { id: 'mode_clean', label: 'Clean', type: 'mode', value: 'Clean', iconSrc: iconClean }
    ];

    const itemElements = {};

    // Helper to create items once
    menuItemsData.forEach((itemData, index) => {
        const children = [];

        // Icon
        if (itemData.type === 'mode') {
            children.push(
                div({
                    className: 'icon-container',
                }, img({ className: 'menu-icon', src: itemData.iconSrc, alt: itemData.label }))
            );
        }

        // Label Container with check icon pre-built
        const labelText = span({
            className: 'item-label-text',
            style: { display: 'inline-block' }
        }, itemData.label);

        const checkIcon = div({
            className: 'check-icon',
            style: {
                marginLeft: 'auto',
                color: '#FFFFFF',
                display: 'flex',
                alignItems: 'center',
                opacity: '0',
                transform: 'scale(0.5)',
                transition: 'all 0.3s cubic-bezier(0.4, 0, 0.2, 1)'
            }
        });
        checkIcon.innerHTML = '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"></polyline></svg>';

        const labelContainer = div({
            className: 'menu-label',
            style: {
                display: 'flex',
                alignItems: 'center',
                flexGrow: '1',
                transition: 'all 0.3s ease'
            }
        }, labelText, checkIcon);

        children.push(labelContainer);

        const itemEl = div({
            id: itemData.id,
            className: `menu-item ${itemData.type === 'title' ? 'title-item' : ''}`,
            'data-index': index,
            onclick: () => {
                if (itemData.type === 'mode') {
                    setState('display', itemData.value);
                    if (window.Android && window.Android.saveSetting) {
                        window.Android.saveSetting('currentClusterDisplay', itemData.value);
                    }
                }
            }
        }, ...children);

        if (itemData.type === 'title') {
            itemEl.style.pointerEvents = 'none';
            itemEl.style.opacity = '0.6';
            itemEl.style.fontSize = '0.7em';
            itemEl.style.textTransform = 'uppercase';
            itemEl.style.letterSpacing = '1.5px';
            itemEl.style.marginBottom = '10px';
            itemEl.style.marginTop = '10px';
            itemEl.style.border = 'none';
            itemEl.style.background = 'none';
            itemEl.style.paddingLeft = '15px';
            itemEl.style.display = 'flex';
            itemEl.style.alignItems = 'center';
        }

        carousel.appendChild(itemEl);
        itemElements[itemData.id] = {
            element: itemEl,
            labelContainer,
            checkIcon
        };
    });

    const updateItems = () => {
        const focusedId = getState('displayFocus') || 'mode_normal';
        const currentIndex = menuItemsData.findIndex(item => item.id === focusedId);
        const currentMode = getState('display') || 'Normal';

        menuItemsData.forEach((itemData) => {
            const cache = itemElements[itemData.id];
            const isFocused = itemData.id === focusedId;
            const isSelected = itemData.type === 'mode' && currentMode === itemData.value;

            // Updated classes for movement transition
            cache.element.className = `menu-item ${isFocused ? 'focused' : ''} ${itemData.type === 'title' ? 'title-item' : ''}`;
            
            if (itemData.type === 'mode') {
                // Update selection styles
                cache.labelContainer.style.color = isSelected ? '#FFFFFF' : '#B0B8C4';
                cache.labelContainer.style.fontWeight = isSelected ? 'bold' : 'normal';
                
                if (cache.checkIcon) {
                    cache.checkIcon.style.opacity = isSelected ? '1' : '0';
                    cache.checkIcon.style.transform = isSelected ? 'scale(1)' : 'scale(0.5)';
                }
            }
        });

        carousel.className = `menu-carousel focus-${currentIndex >= 0 ? currentIndex : 0}`;
    };

    updateItems();

    main.onMount = () => {
        const sub1 = subscribe('display', updateItems);
        const sub2 = subscribe('displayFocus', updateItems);

        return () => {
            sub1();
            sub2();
        };
    };

    return main;
}
