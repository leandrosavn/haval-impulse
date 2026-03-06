import { setState, stateManager } from './state.js';
import { menuItems } from './components/mainMenu.js';

const focusableAreas = {
    main_menu: menuItems.map(item => item.id),
    ac_control: ['fan', 'temp'],
    regen: ['Baixo', 'Normal', 'Alto'],
    graph: ['evConsumption', 'gasConsumption', 'carSpeed']
};

document.addEventListener('keydown', (e) => {
    if (e.ctrlKey || e.altKey || e.metaKey) return;

    const currentState = stateManager.getState();
    const currentScreen = currentState.screen;

    if (e.key === 'Backspace') {
        if (currentScreen !== 'main_menu') {
            window.showScreen('main_menu');
        }
        return;
    }

    if (currentScreen === 'main_menu') {
        const menuItems = focusableAreas.main_menu;
        const currentIndex = menuItems.indexOf(currentState.focusedMenuItem);

        if (e.key === 'ArrowUp') {
            const prevIndex = (currentIndex - 1 + menuItems.length) % menuItems.length;
            window.focus(menuItems[prevIndex]);
        } else if (e.key === 'ArrowDown') {
            const nextIndex = (currentIndex + 1) % menuItems.length;
            window.focus(menuItems[nextIndex]);

        } else if (e.key === 'Enter') {
            if (currentState.focusedMenuItem === 'option_1') {
                const currentStatus = stateManager.getState().espStatus;
                const newStatus = (currentStatus === 'ON') ? 'OFF' : 'ON';
                setState('espStatus', newStatus);
            } else if (currentState.focusedMenuItem === 'option_2') {
                const modes = ['HEV', 'PHEV', 'EV'];
                const currentMode = stateManager.getState().evMode;
                const currentIndex = modes.indexOf(currentMode);
                const nextIndex = (currentIndex + 1) % modes.length;
                const newMode = modes[nextIndex];
                setState('evMode', newMode);
            } else if (currentState.focusedMenuItem === 'option_3') {
                const modes = ['Normal', 'Eco', 'Sport'];
                const currentMode = stateManager.getState().drivingMode;
                const currentIndex = modes.indexOf(currentMode);
                const nextIndex = (currentIndex + 1) % modes.length;
                const newMode = modes[nextIndex];
                setState('drivingMode', newMode);
            } else if (currentState.focusedMenuItem === 'option_4') {
                setState('fan', 1);
                setState('temp', 21.0);
                setState('outside_temp', 28.5);
                setState('inside_temp', 23.0);
                setState('targetTemp', 21.0);
                window.showScreen('aircon');
            } else if (currentState.focusedMenuItem === 'option_5') {
                const modes = ['Normal', 'Conforto', 'Esportiva'];
                const currentMode = stateManager.getState().steerMode;
                const currentIndex = modes.indexOf(currentMode);
                const nextIndex = (currentIndex + 1) % modes.length;
                const newMode = modes[nextIndex];
                setState('steerMode', newMode);
            } else if (currentState.focusedMenuItem === 'option_6') {
                window.showScreen('regen');
            } else if (currentState.focusedMenuItem === 'option_7') {
                window.showScreen('graph');
            }


        }
    }

    else if (currentScreen === 'aircon') {
        const focusedArea = currentState.focusArea;

        if (e.key === 'Enter') {
            if (currentState.impulseauto == 1) {
                window.focus('temp');
            } else {
                const controls = focusableAreas.ac_control;
                const currentIndex = controls.indexOf(focusedArea);
                const nextIndex = (currentIndex + 1) % controls.length;
                window.focus(controls[nextIndex]);
            }
        } else if (e.key === ' ') {
            e.preventDefault();
            const newAutoModeState = (currentState.auto == 0 ? 1 : 0);
            setState('auto', newAutoModeState);
        } else if (e.key === 'a') {
            /* TODO: for future use with impulseauto. For now it triggers maxauto mode
                        e.preventDefault();
                        const newModeState = (currentState.impulseauto == 0 ? 1 : 0);
                        setState('impulseauto', newModeState);
                        window.focus('temp');
            */
            e.preventDefault();
            const newModeState = (currentState.maxauto == 0 ? 1 : 0);
            setState('maxauto', newModeState);
        }

        switch (focusedArea) {
            case 'fan':
                const currentFan = parseInt(currentState.fan, 10) || 0;
                if (e.key === 'ArrowUp' && currentFan < 7) {
                    window.control('fan', String(currentFan + 1));
                } else if (e.key === 'ArrowDown' && currentFan > 0) {
                    window.control('fan', String(currentFan - 1));
                }
                break;

            case 'temp':
                if (currentState.impulseauto == 1) {
                    const currentTargetTemp = parseFloat(currentState.targetTemp) || 21.0;
                    if (e.key === 'ArrowUp' && currentTargetTemp < 32.0) {
                        window.control('targetTemp', (currentTargetTemp + 0.5).toFixed(1));
                    } else if (e.key === 'ArrowDown' && currentTargetTemp > 16.0) {
                        window.control('targetTemp', (currentTargetTemp - 0.5).toFixed(1));
                    }
                } else {
                    const currentTemp = parseFloat(currentState.temp) || 21.0;
                    if (e.key === 'ArrowUp' && currentTemp < 32.0) {
                        window.control('temp', (currentTemp + 0.5).toFixed(1));
                    } else if (e.key === 'ArrowDown' && currentTemp > 16.0) {
                        window.control('temp', (currentTemp - 0.5).toFixed(1));
                    }
                }
                break;

            default:
                break;
        }
    }

    else if (currentScreen === 'regen') {
        const regenMode = currentState.regenMode;

        if (e.key === 'Enter') {
            window.control('onepedal', !currentState.onepedal);
        } else if (e.key === 'ArrowUp') {
            const controls = focusableAreas.regen;
            const currentIndex = controls.indexOf(regenMode);
            if (currentIndex < 2) {
                const nextIndex = (currentIndex + 1) % controls.length;
                window.control('regenMode', controls[nextIndex]);
            }
        } else if (e.key === 'ArrowDown') {
            const controls = focusableAreas.regen;
            const currentIndex = controls.indexOf(regenMode);
            if (currentIndex > 0) {
                const nextIndex = (currentIndex - 1) % controls.length;
                window.control('regenMode', controls[nextIndex]);
            }
        }
    }
    else if (currentScreen === 'graph') {
        const currentGraph = currentState.currentGraph;

        if ((e.key === 'Enter') || (e.key === 'ArrowDown')) {
            const controls = focusableAreas.graph;
            const currentIndex = controls.indexOf(currentGraph);
            var nextIndex = currentIndex + 1;
            if (nextIndex >= controls.length) nextIndex = 0;
            window.control('currentGraph', controls[nextIndex]);
        } else if (e.key === 'ArrowUp') {
            const controls = focusableAreas.graph;
            const currentIndex = controls.indexOf(currentGraph);
            var nextIndex = currentIndex - 1;
            if (nextIndex < 0) nextIndex = controls.length - 1;
            window.control('currentGraph', controls[nextIndex]);
        }
    }
});

let lastValue = 0;
const smoothingFactor = 0.05; // Less dramatic changes
let timeToModeChange = 10;
let simulationPhase = 'idle';
let currentSpeed = 0.0;
let steadyTimeCounter = 0;
const SIMULATION_INTERVAL = 100;


if (window.simulationInterval) clearInterval(window.simulationInterval);

window.simulationInterval = setInterval(() => {
    switch (simulationPhase) {
        case 'accelerating':
            if (currentSpeed < 150) {
                currentSpeed += 2.0;
            } else {
                currentSpeed = 150;
                simulationPhase = 'decelerating';
            }
            break;

        case 'decelerating':
            if (currentSpeed > 20) {
                currentSpeed -= 5;
            } else {
                currentSpeed = 20;
                simulationPhase = 'steady';
                steadyTimeCounter = 0;
            }
            break;

        case 'steady':
            const STEADY_DURATION_MS = 1000;
            if (steadyTimeCounter * SIMULATION_INTERVAL < STEADY_DURATION_MS) {
                steadyTimeCounter++;
            } else {
                simulationPhase = 'stopping';
            }
            break;

        case 'stopping':
            if (currentSpeed > 0) {
                currentSpeed -= 1;
            } else {
                currentSpeed = 0;
                simulationPhase = 'idle';
                setTimeout(() => {
                    simulationPhase = 'accelerating';
                }, 5000);
            }
            break;

        case 'idle':
        default:
            break;
    }

    setState('carSpeed', Math.max(0, currentSpeed.toFixed(1)));

    const randomTarget = Math.floor(Math.random() * 101);
    lastValue = (lastValue * (1 - smoothingFactor)) + (randomTarget * smoothingFactor);

    timeToModeChange--;
    if (timeToModeChange <= 0) {
        const currentMode = stateManager.getState().gasConsumptionMode;
        const newMode = (currentMode === 'Running') ? 'Idle' : 'Running';
        setState('gasConsumptionMode', newMode);

        timeToModeChange = Math.floor(Math.random() * 100) + 50;
    }

    const currentMode = stateManager.getState().gasConsumptionMode;

    if (currentMode === 'Running') {
        const gasV = Math.round(lastValue) / 3;
        setState('gasConsumption', gasV);
        setState('gasConsumptionIdle', 0);
        // Simulate RPM: if running AND speed > 0, it should be between 800 and 7000
        // Force 0 if speed is 0
        const playsRPM = currentSpeed > 0;
        const simulatedRPM = playsRPM ? 1000 + (currentSpeed * 40) + (Math.random() * 500) : 0;
        setState('engineRPM', Math.min(Math.max(simulatedRPM, 0), 7000));
    } else {
        setState('gasConsumption', 0);
        setState('gasConsumptionIdle', Math.round(lastValue) / 20);
        // If idle, RPM is 800 but ONLY if speed > 0
        const idleRPM = currentSpeed > 0 ? 800 : 0;
        setState('engineRPM', idleRPM);
    }

    // Simulate EV power factor: -100 to +100 % (for power ring)
    const powerFactor = Math.round(lastValue * 2) - 100;
    setState('evPowerFactor', powerFactor * 2);
    // Simulate EV power in kW: ±120 kW range (for graph)
    if (powerFactor > 0) setState('evPowerKw', Math.round(powerFactor * 4 * Math.abs(currentSpeed) / 100));
    else setState('evPowerKw', Math.round(powerFactor));
    setState('lastRegenValue', Math.round(lastValue));

}, SIMULATION_INTERVAL);

setTimeout(() => {
    simulationPhase = 'accelerating';
}, 5000);


