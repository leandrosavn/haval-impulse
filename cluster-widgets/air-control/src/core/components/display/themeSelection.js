import { getState, setState, subscribe } from '../../state.js';
import { div, span, img } from '../../../utils/createElement.js';

const iconNormal = `data:image/svg+xml;base64,${btoa('<svg width="48" height="48" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><rect x="2" y="5" width="20" height="14" rx="2" stroke="#00A0E0" stroke-width="2"/><circle cx="8" cy="12" r="3" stroke="#00A0E0" stroke-width="1.5"/><circle cx="16" cy="12" r="3" stroke="#00A0E0" stroke-width="1.5"/></svg>')}`;
const iconReduced = `data:image/svg+xml;base64,${btoa('<svg width="48" height="48" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><rect x="2" y="5" width="20" height="14" rx="2" stroke="#00A0E0" stroke-width="2"/><circle cx="8" cy="12" r="1.5" stroke="#00A0E0" stroke-width="1.5"/><circle cx="16" cy="12" r="1.5" stroke="#00A0E0" stroke-width="1.5"/></svg>')}`;
const iconClean = `data:image/svg+xml;base64,${btoa('<svg width="48" height="48" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><rect x="2" y="5" width="20" height="14" rx="2" stroke="#00A0E0" stroke-width="2"/></svg>')}`;

export function createDisplaySelectionScreen() {
    const main = document.createElement('main');
    main.className = 'main-container';

    let currentContainer = null;
    let currentSubscriptions = [];

    const render = () => {
        // Cleanup previous
        if (currentContainer) {
            main.removeChild(currentContainer);
            currentSubscriptions.forEach(unsub => unsub());
            currentSubscriptions = [];
        }

        const clusterEnabled = getState('clusterEnabled') !== false;

        if (!clusterEnabled) {
            const warningContainer = div({
                className: 'warning-container',
                style: {
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    justifyContent: 'center',
                    height: '456px',
                    width: '456px',
                    padding: '40px',
                    textAlign: 'center',
                    color: '#FFFFFF',
                    boxSizing: 'border-box',
                    border: '3px solid #00A0E0',
                    borderRadius: '50%',
                    background: 'rgba(0, 0, 0, 0.4)',
                    backdropFilter: 'blur(10px)'
                }
            }, [
                div({
                    style: {
                        fontSize: '1.8em',
                        marginBottom: '40px',
                        lineHeight: '1.3',
                        fontWeight: '300',
                        padding: '0 40px'
                    }
                }, 'O cockpit virtual precisa ser ativado no app Haval Impulse.'),
                div({
                    id: 'ok_button',
                    className: 'menu-item focused',
                    style: {
                        position: 'relative',
                        transform: 'none',
                        opacity: '1',
                        left: 'auto',
                        top: 'auto',
                        width: 'auto',
                        height: 'auto',
                        padding: '12px 60px',
                        borderRadius: '40px',
                        background: '#00A0E0',
                        color: '#FFFFFF',
                        cursor: 'pointer',
                        fontSize: '1.4em',
                        fontWeight: 'bold', // Keep button text bold for visibility but slightly smaller
                        transition: 'all 0.3s ease',
                        boxShadow: '0 0 20px rgba(0, 160, 224, 0.5)'
                    },
                    onclick: () => {
                        window.showScreen('main_menu');
                    }
                }, 'OK')
            ]);
            main.appendChild(warningContainer);
            main.style.borderRadius = '50%';
            main.style.overflow = 'hidden';
            main.style.border = 'none'; // Clear any default main border to use warningContainer's
            currentContainer = warningContainer;
            setState('displayFocus', 'ok_button');
            return;
        }

        const container = div({ className: 'main-menu-container' });
        const carousel = div({ className: 'menu-carousel' });
        container.appendChild(carousel);
        main.appendChild(container);
        currentContainer = container;

        const menuItemsData = [
            { id: 'title_mask', label: 'Modos do Tema', type: 'title' },
            { id: 'mode_normal', label: 'Normal', type: 'mode', value: 'Normal', iconSrc: iconNormal },
            { id: 'mode_reduzido', label: 'Reduzido', type: 'mode', value: 'Reduzido', iconSrc: iconReduced },
            { id: 'mode_clean', label: 'Clean', type: 'mode', value: 'Clean', iconSrc: iconClean }
        ];

        const itemElements = {};

        menuItemsData.forEach((itemData, index) => {
            const children = [];
            if (itemData.type === 'mode') {
                children.push(
                    div({
                        className: 'icon-container',
                    }, img({ className: 'menu-icon', src: itemData.iconSrc, alt: itemData.label }))
                );
            }

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
                if (!cache) return;
                const isFocused = itemData.id === focusedId;
                const isSelected = itemData.type === 'mode' && currentMode === itemData.value;

                cache.element.className = `menu-item ${isFocused ? 'focused' : ''} ${itemData.type === 'title' ? 'title-item' : ''}`;
                
                if (itemData.type === 'mode') {
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
        currentSubscriptions.push(subscribe('display', updateItems));
        currentSubscriptions.push(subscribe('displayFocus', updateItems));

        const currentMode = getState('display') || 'Normal';
        const item = menuItemsData.find(i => i.type === 'mode' && i.value === currentMode);
        if (item) {
            setState('displayFocus', item.id);
        }
    };

    render();
    const subCluster = subscribe('clusterEnabled', render);

    const cleanup = () => {
        subCluster();
        currentSubscriptions.forEach(unsub => unsub());
    };

    return { element: main, cleanup };
}


