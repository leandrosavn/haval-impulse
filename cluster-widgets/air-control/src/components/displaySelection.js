import { getState, setState, subscribe } from '../state.js';
import { div, span } from '../utils/createElement.js';

export function createDisplaySelectionScreen() {
    const main = document.createElement('main');
    main.className = 'main-container';

    const container = div({ className: 'display-selection-container' });

    const templates = ['Normal'];
    const displays = ['Normal', 'Reduzido', 'Clean'];

    let focusedSection = 'display'; // 'template' or 'display'
    let focusedIndex = displays.indexOf(getState('display') || 'Normal');

    const renderOptions = () => {
        container.innerHTML = '';

        const title = div({ className: 'selection-title', children: ['Configurações de Exibição'] });
        container.appendChild(title);

        // Template Section
        const templateGroup = div({ className: `selection-group ${focusedSection === 'template' ? 'focused' : ''}` });
        templateGroup.appendChild(div({ className: 'group-label', children: ['Template'] }));
        
        const templateValue = div({ 
            className: 'group-value', 
            children: [getState('template') || 'Normal'] 
        });
        templateGroup.appendChild(templateValue);
        container.appendChild(templateGroup);

        // Display Section
        const displayGroup = div({ className: `selection-group ${focusedSection === 'display' ? 'focused' : ''}` });
        displayGroup.appendChild(div({ className: 'group-label', children: ['Modo de Exibição'] }));
        
        const displayOptionsContainer = div({ className: 'display-options-horizontal' });
        displays.forEach((mode, idx) => {
            const isSelected = getState('display') === mode;
            const isFocused = focusedSection === 'display' && focusedIndex === idx;
            
            const option = div({
                className: `display-option ${isSelected ? 'selected' : ''} ${isFocused ? 'focused' : ''}`,
                children: [mode]
            });
            displayOptionsContainer.appendChild(option);
        });
        
        displayGroup.appendChild(displayOptionsContainer);
        container.appendChild(displayGroup);

        const hint = div({ className: 'selection-hint', children: ['Use as setas para navegar e OK para selecionar'] });
        container.appendChild(hint);
    };

    const handleKey = (e) => {
        if (e.key === 'ArrowUp' || e.key === 'ArrowDown') {
            focusedSection = focusedSection === 'template' ? 'display' : 'template';
            if (focusedSection === 'display') {
                focusedIndex = displays.indexOf(getState('display'));
            } else {
                focusedIndex = 0;
            }
            renderOptions();
        } else if (e.key === 'ArrowLeft' && focusedSection === 'display') {
            focusedIndex = (focusedIndex - 1 + displays.length) % displays.length;
            renderOptions();
        } else if (e.key === 'ArrowRight' && focusedSection === 'display') {
            focusedIndex = (focusedIndex + 1) % displays.length;
            renderOptions();
        } else if (e.key === 'Enter') {
            if (focusedSection === 'display') {
                setState('display', displays[focusedIndex]);
            } else {
                setState('template', templates[focusedIndex]);
            }
            renderOptions();
            
            // Apply class to body/app for CSS transitions if needed
            const app = document.getElementById('app');
            if (app) {
                app.className = (app.className.split(' ').filter(c => !c.startsWith('display-')).join(' ') + ' display-' + getState('display').toLowerCase()).trim();
            }
        } else if (e.key === 'Backspace') {
            window.showScreen('main_menu');
        }
    };

    main.appendChild(container);
    renderOptions();

    // In a real environment, keys are handled by the global listener in testing-utils.js 
    // or by the system. For the component themselves, we can expose a key handler if needed.
    
    main.onMount = () => {
        document.addEventListener('keydown', handleKey);
        return () => document.removeEventListener('keydown', handleKey);
    };

    return main;
}
