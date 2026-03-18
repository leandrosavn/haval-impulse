import { getState, setState, subscribe } from '../state.js';
import { div, span, img } from '../utils/createElement.js';

const iconMode = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABgAAAAYCAYAAADgdz34AAAACXBIWXMAAAsTAAALEwEAmpwYAAABQUlEQVR4nOVVS0oDQRB9uvaDeot4gOANdBcIyVI9Qa7gIRy3xqUnECFTNQs9RHYBg+ABJu7Mk+4ZME53j9M4QoIPCga66r3iVVcP8D+Q8gzKHEqWkSPjaXsCwrsV8iKE47bIjyGceQRmSNmJI7viNoQJhPcQDiG8gXDpkH+JLMucYVmTWI4gMl4GybRxXITId6B8bUHgDQ/ccwWUoxorXqDs45m7NjL2oJzWiIxcgQlPoFx4yR956OQ/8aAUrpIvLJcXyi6E75WCfnBmwkGlGVPbDQ+5EFldKFpLQjBef28mryePFZhwP07AZ1HGXjDf3P/GFoWGrJzagbr5RxDOmw/5p2sqHFjPTRSbO4+7pn++aAZmzX8rIDxHEORW5bFLoPyoITRn19Y+U2O+DUcUUnbae65DEI493d9ic36ZWGN8Am7jp8nKJdGxAAAAAElFTkSuQmCC";
const iconDisplay = `data:image/svg+xml;base64,${btoa(`
<svg width="48" height="48" viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
  <rect x="4" y="8" width="40" height="28" rx="4" stroke="#00A0E0" stroke-width="2.5"/>
  <rect x="8" y="12" width="14" height="9" rx="1" fill="#00A0E0" fill-opacity="0.2"/>
  <rect x="26" y="12" width="14" height="9" rx="1" fill="#00A0E0" fill-opacity="0.2"/>
  <rect x="8" y="25" width="32" height="7" rx="1" fill="#00A0E0" fill-opacity="0.5"/>
  <path d="M20 40H28M24 36V40" stroke="#00A0E0" stroke-width="2.5" stroke-linecap="round"/>
</svg>
`)}`;

export function createDisplaySelectionScreen() {
    const main = document.createElement('main');
    main.className = 'main-container';

    const container = div({ className: 'main-menu-container' });
    const carousel = div({ className: 'menu-carousel' });
    container.appendChild(carousel);
    main.appendChild(container);

    const templates = ['Normal'];
    const displays = ['Normal', 'Reduzido', 'Clean'];

    const itemElements = {};

    const menuItemsData = [
        { id: 'sel_template', label: 'Template', type: 'template', iconSrc: iconMode },
        { id: 'spacer', label: '', type: 'spacer', iconSrc: iconMode },
        { id: 'mode_normal', label: 'Normal', type: 'mode', value: 'Normal', iconSrc: iconDisplay },
        { id: 'mode_reduzido', label: 'Reduzido', type: 'mode', value: 'Reduzido', iconSrc: iconDisplay },
        { id: 'mode_clean', label: 'Clean', type: 'mode', value: 'Clean', iconSrc: iconDisplay }
    ];

    const createLabelContent = (itemData) => {
        if (itemData.type === 'template') {
            const stateValue = getState('template') || 'Normal';
            return [
                itemData.label,
                ' ',
                span({
                    className: `menu-label-status eco`, 
                    children: [stateValue]
                })
            ];
        } else if (itemData.type === 'mode') {
            const currentState = getState('display') || 'Normal';
            const isSelected = currentState === itemData.value;
            return [
                itemData.label,
                isSelected ? ' ' : '',
                isSelected ? span({
                    className: `menu-label-status eco`,
                    children: ['(Ativo)']
                }) : ''
            ];
        }
        return [''];
    };

    const renderItems = () => {
        carousel.innerHTML = '';
        const focusedId = getState('displayFocus') || 'sel_template';
        const currentIndex = menuItemsData.findIndex(item => item.id === focusedId);

        menuItemsData.forEach((itemData, index) => {
            const isFocused = itemData.id === focusedId;
            
            const children = [];
            if (itemData.type !== 'spacer') {
                children.push(
                    div({
                        className: 'icon-container',
                        children: [img({ className: 'menu-icon', src: itemData.iconSrc, alt: itemData.label })]
                    })
                );
            }
            children.push(
                span({
                    className: 'menu-label',
                    children: [createLabelContent(itemData)]
                })
            );

            const itemEl = div({
                id: itemData.id,
                className: `menu-item ${isFocused ? 'focused' : ''} ${itemData.type === 'spacer' ? 'spacer' : ''}`,
                'data-index': index,
                children: children
            });
            carousel.appendChild(itemEl);
            itemElements[itemData.id] = itemEl;
        });
        
        carousel.className = 'menu-carousel';
        carousel.classList.add(`focus-${currentIndex >= 0 ? currentIndex : 0}`);
    };

    renderItems();

    main.onMount = () => {
        const sub1 = subscribe('display', renderItems);
        const sub2 = subscribe('template', renderItems);
        const sub3 = subscribe('displayFocus', renderItems);
        
        return () => {
            sub1();
            sub2();
            sub3();
        };
    };

    return main;
}
