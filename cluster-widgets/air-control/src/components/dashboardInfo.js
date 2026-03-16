import { getState, subscribe } from '../state.js';
import { div, span, img } from '../utils/createElement.js';

export function createDashboardInfo() {
    const container = div({ className: 'dashboard-info-container' });

    // 1. Top Bar Elements (Clock, Gear, Mode)
    const topCenter = div({ className: 'dashboard-top-center' });
    const clock = span({ className: 'dashboard-clock', children: [getState('clockTime')] });
    const gear = span({ className: 'dashboard-gear', children: [getState('gearState')] });
    const evMode = span({ className: 'dashboard-ev-mode', children: [getState('evModeLabel')] });
    
    topCenter.appendChild(clock);
    topCenter.appendChild(gear);
    topCenter.appendChild(evMode);

    // 2. Speed Gauge Elements (Left Circle)
    const speedDial = div({ className: 'dashboard-speed-dial' });
    const speedInnerCircle = div({ className: 'speed-inner-circle' });
    const speedNeedleContainer = div({ className: 'speed-needle-container' });
    const speedNeedle = div({ className: 'speed-needle' });
    
    speedNeedleContainer.appendChild(speedNeedle);
    speedDial.appendChild(speedNeedleContainer);
    speedDial.appendChild(speedInnerCircle);

    const speedContent = div({ className: 'dashboard-speed-content' });
    const ready = div({ className: 'dashboard-ready', children: ['READY'] });
    const speedValue = div({ className: 'dashboard-speed-value', children: [getState('carSpeed')] });
    const speedMetric = div({ className: 'dashboard-speed-metric', children: ['km/h'] });
    const limitSign = div({ className: 'dashboard-limit-sign', children: [
        div({ className: 'limit-circle', children: [getState('limitSpeed')] })
    ]});

    speedContent.appendChild(ready);
    speedContent.appendChild(speedValue);
    speedContent.appendChild(speedMetric);
    speedContent.appendChild(limitSign);
    
    const speedContainer = div({ className: 'dashboard-speed-container' });
    speedContainer.appendChild(speedDial);
    speedContainer.appendChild(speedContent);

    // 3. Bottom Gauges
    const bottomGauges = div({ className: 'dashboard-bottom-gauges' });
    
    // Fuel Gauge
    const fuelContainer = div({ className: 'dashboard-fuel-container' });
    const fuelTop = div({ className: 'gauge-top-info', children: [
        div({ className: 'fuel-icon' }),
        span({ className: 'fuel-range', children: [getState('fuelRange') + ' km'] })
    ]});
    
    // 4 Segments
    const fuelSegments = Array.from({length: 4}, () => div({ 
        className: 'segment-track', 
        children: [div({ className: 'bar-segment' })] 
    }));
    const fuelBar = div({ className: 'segmented-bar-container fuel', children: [
        ...fuelSegments,
        div({ className: 'fuel-labels', children: [span({}, 'E'), span({}, 'F')] })
    ]});
    
    fuelContainer.appendChild(fuelTop);
    fuelContainer.appendChild(fuelBar);

    // Battery Gauge
    const batteryContainer = div({ className: 'dashboard-battery-container' });
    const batteryTop = div({ className: 'gauge-top-info', children: [
        div({ className: 'battery-icon' }),
        span({ className: 'battery-range', children: [getState('batteryRange') + ' km'] })
    ]});
    
    const batterySegments = Array.from({length: 4}, () => div({ 
        className: 'segment-track', 
        children: [div({ className: 'bar-segment' })] 
    }));
    const batteryBar = div({ className: 'segmented-bar-container battery', children: [
        ...batterySegments,
        div({ className: 'battery-labels', children: [span({}, 'E'), span({}, 'F')] }),
        span({ className: 'ev-label', children: ['EV'] })
    ]});

    batteryContainer.appendChild(batteryTop);
    batteryContainer.appendChild(batteryBar);

    bottomGauges.appendChild(fuelContainer);
    bottomGauges.appendChild(batteryContainer);

    container.appendChild(topCenter);
    container.appendChild(speedContainer);
    container.appendChild(bottomGauges);

    // Subscriptions
    const updateBarSegments = (tracks, percent) => {
        tracks.forEach((track, i) => {
            const seg = track.firstChild;
            const segMax = (i + 1) * 25;
            const segMin = i * 25;
            if (percent >= segMax) {
                seg.style.width = '100%';
            } else if (percent > segMin) {
                seg.style.width = ((percent - segMin) / 25 * 100) + '%';
            } else {
                seg.style.width = '0%';
            }
        });
    };

    const updateSpeedRotation = (speed) => {
        // 0 km/h = bottom (180deg). Max speed (e.g. 240) = ~-60deg? 
        // Let's assume 0 at bottom and rotating counter-clockwise or clockwise.
        // Car dials usually go clockwise. 0 at bottom = 180deg. 
        // Rotation = 180 + (speed * factor)
        // Adjust factor to fit the sweep.
        const rotation = 180 + (speed * 1.2); 
        speedNeedleContainer.style.transform = `rotate(${rotation}deg)`;
    };

    const sub0 = subscribe('clockTime', val => clock.textContent = val);
    const sub1 = subscribe('gearState', val => gear.textContent = val);
    const sub2 = subscribe('evModeLabel', val => evMode.textContent = val);
    const sub3 = subscribe('carSpeed', val => {
        speedValue.textContent = val;
        updateSpeedRotation(val);
    });
    const sub4 = subscribe('limitSpeed', val => limitSign.querySelector('.limit-circle').textContent = val);
    const sub5 = subscribe('fuelPercent', val => updateBarSegments(fuelSegments, val));
    const sub6 = subscribe('batteryPercent', val => updateBarSegments(batterySegments, val));
    const sub7 = subscribe('fuelRange', val => fuelTop.querySelector('.fuel-range').textContent = val + ' km');
    const sub8 = subscribe('batteryRange', val => batteryTop.querySelector('.battery-range').textContent = val + ' km');
    const sub9 = subscribe('readyState', val => ready.style.visibility = val ? 'visible' : 'hidden');

    updateBarSegments(fuelSegments, getState('fuelPercent'));
    updateBarSegments(batterySegments, getState('batteryPercent'));
    updateSpeedRotation(getState('carSpeed'));

    container.cleanup = () => {
        [sub0, sub1, sub2, sub3, sub4, sub5, sub6, sub7, sub8, sub9].forEach(un => un());
    };

    return container;
}
