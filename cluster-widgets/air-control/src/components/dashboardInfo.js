import { getState, setState, subscribe } from '../state.js';
import { div, span, img } from '../utils/createElement.js';

const fuelIconBase64 = "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCIgZmlsbD0id2hpdGUiPjxwYXRoIGQ9Ik0xLDEyTDUsOVYxNVoiLz48cGF0aCBkPSJNMjIsMTBWOGEyLDIsMCwwLDAtMi0yaC0zVjRhMiwyLDAsMCwwLTItMkg5QTIsMiwwLDAsMCw3LDR2MTZhMiwyLDAsMCwwLDIsMmg4YTIsMiwwLDAsMCwyLTJWMTJoMXY0YTIsMiwwLDAsMCw0LDBWMTBaTTksNGg4djZIOVptOCwxNkg5VjEyaDhaIi8+PC9zdmc+";
const batteryIconBase64 = "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCIgZmlsbD0id2hpdGUiPjwhLS0gQm9keSAtLT48cGF0aCBkPSJNMyw2aDE4YzEuMSwwLDIsMC45LDIsMnYxMGMwLDEuMS0wLjksMi0yLDJIM2MtMS4xLDAtMi0wLjktMi0yVjhDMSw2LjksMS45LDYsMyw2eiBNMyw4djEwaDE4VjhIM3oiLz48IS0tIFBvbGVzIC0tPjxyZWN0IHg9IjUiIHk9IjMiIHdpZHRoPSI0IiBoZWlnaHQ9IjMiLz48cmVjdCB4PSIxNSIgeT0iMyIgd2lkdGg9IjQiIGhlaWdodD0iMyIvPjwhLS0gTWludXMgc2lnbiAoLSkgLS0+PHJlY3QgeD0iNiIgeT0iMTIiIHdpZHRoPSI0IiBoZWlnaHQ9IjIiLz48IS0tIFBsdXMgc2lnbiAoKykgLS0+PHBhdGggZD0iTTE2LDEwaC0ydjJoLTJ2MmgydjJoMnYtMmgydi0yaC0yVjEweiIvPjwvc3ZnPg==";

export function createDashboardInfo() {
    const container = div({ className: 'dashboard-info-container' });
    const menuWrapper = div({ className: 'dashboard-menu-container' });

    // 1. Top Bar Elements (Clock, Gear, Mode)
    const topCenter = div({ className: 'dashboard-top-center' });
    const clock = span({ className: 'dashboard-clock', children: [getState('clockTime')] });
    const gearValue = getState('gearState');
    const gear = span({ className: 'dashboard-gear', children: [gearValue] });

    // Initial color setup
    const updateGearColor = (val) => {
        if (val === 'P') gear.style.color = '#ff3b30'; // Red
        else if (val === 'R') gear.style.color = '#ff9500'; // Orange
        else gear.style.color = '#ffffff'; // White
    };
    updateGearColor(gearValue);

    const evMode = span({ className: 'dashboard-ev-mode', children: [getState('evModeLabel')] });

    const updateEvModeColor = (val) => {
        const lowerVal = String(val).toLowerCase();
        if (lowerVal.includes('eco')) {
            evMode.style.color = '#4CAF50';
        } else if (lowerVal.includes('sport')) {
            evMode.style.color = '#FF4D4D';
        } else {
            evMode.style.color = '#ffffff';
        }
    };
    updateEvModeColor(getState('evModeLabel'));

    topCenter.appendChild(clock);
    topCenter.appendChild(gear);
    topCenter.appendChild(evMode);

    // Clock auto-update
    const clockInterval = setInterval(() => {
        const now = new Date();
        const hrs = String(now.getHours()).padStart(2, '0');
        const mins = String(now.getMinutes()).padStart(2, '0');
        clock.textContent = `${hrs}:${mins}`;
    }, 30000);

    // 2. Speed Gauge Elements (Left Circle)
    const speedDial = div({ className: 'dashboard-speed-dial' });
    const speedOrbital = div({ className: 'speed-orbital' });
    const speedInnerCircle = div({ className: 'speed-inner-circle' });
    const speedNeedleContainer = div({ className: 'speed-needle-container' });
    const speedNeedle = div({ className: 'speed-needle' });

    speedNeedleContainer.appendChild(speedNeedle);
    speedDial.appendChild(speedOrbital);

    const trails = [];
    const total_trails = 8;
    for (let i = 0; i < total_trails; i++) {
        const isMainTrail = i === total_trails - 1;
        const trailContainer = div({ className: 'needle-trail-wrapper' });
        const trailLoader = div({ className: 'needle-trail' + (isMainTrail ? ' main-trail' : '') });

        if (isMainTrail) {
            trailLoader.style.borderWidth = '3px';
            trailLoader.style.borderColor = '#FFF transparent transparent transparent';
        } else {
            trailLoader.style.borderWidth = '15px';
            // Apply a blue tint that increases towards the tail
            const blueFactor = (total_trails - 2 - i) / (total_trails - 2);
            const r = Math.round(255 - (255 - 100) * (blueFactor > 0 ? blueFactor : 0));
            const g = Math.round(255 - (255 - 100) * (blueFactor > 0 ? blueFactor : 0));
            const b = 255;
            trailLoader.style.borderColor = `rgb(${r}, ${g}, ${b}) transparent transparent transparent`;
        }

        const sizeReduction = i * 2.5;
        trailContainer.style.width = isMainTrail ? '90%' : `calc(95% - ${sizeReduction}px)`;
        trailContainer.style.height = isMainTrail ? '90%' : `calc(95% - ${sizeReduction}px)`;
        trailContainer.style.opacity = isMainTrail ? '1' : (0.5 - (i * 0.02)).toString();
        trailContainer.style.transition = `transform ${0.1 + (i * 0.02)}s cubic-bezier(0.1, 0, 0.3, 1)`;
        trailLoader.style.animationDelay = `${i * 0.04}s`;

        trailContainer.appendChild(trailLoader);
        speedDial.appendChild(trailContainer);
        trails.push(trailContainer);
    }

    speedDial.appendChild(speedNeedleContainer);
    speedDial.appendChild(speedInnerCircle);

    const speedContent = div({ className: 'dashboard-speed-content' });
    const speedValue = div({ className: 'dashboard-speed-value', children: [getState('carSpeed')] });
    const speedMetric = div({ className: 'dashboard-speed-metric', children: ['km/h'] });

    speedContent.appendChild(speedValue);
    speedContent.appendChild(speedMetric);

    const speedContainer = div({ className: 'dashboard-speed-container' });
    speedContainer.appendChild(speedDial);
    speedContainer.appendChild(speedContent);

    // 3. Bottom Gauges
    const bottomGauges = div({ className: 'dashboard-bottom-gauges' });

    // Fuel Gauge
    const fuelContainer = div({ className: 'dashboard-fuel-container' });
    const fuelTop = div({
        className: 'gauge-top-info', children: [
            img({ className: 'fuel-icon', src: fuelIconBase64 }),
            span({ className: 'fuel-range', children: [getState('fuelRange'), span({ className: 'dashboard-unit', children: [' km'] })] }),
            span({ className: 'fuel-percent', children: [getState('fuelPercent') + '%'] })
        ]
    });

    // 4 Segments
    const fuelSegments = Array.from({ length: 4 }, () => div({
        className: 'segment-track',
        children: [div({ className: 'bar-segment' })]
    }));
    const fuelBar = div({
        className: 'segmented-bar-container fuel', children: [
            ...fuelSegments
        ]
    });
    const fuelLabels = div({
        className: 'gauge-labels-container',
        children: [span({}, 'E'), span({}, 'F')]
    });

    fuelContainer.appendChild(fuelTop);
    fuelContainer.appendChild(fuelBar);
    fuelContainer.appendChild(fuelLabels);

    // Battery Gauge
    const batteryContainer = div({ className: 'dashboard-battery-container' });
    const batteryTop = div({
        className: 'gauge-top-info', children: [
            img({ className: 'battery-icon', src: batteryIconBase64 }),
            span({ className: 'battery-range', children: [getState('batteryRange'), span({ className: 'dashboard-unit', children: [' km'] })] }),
            span({ className: 'battery-percent', children: [getState('batteryPercent') + '%'] })
        ]
    });

    const batterySegments = Array.from({ length: 4 }, () => div({
        className: 'segment-track',
        children: [div({ className: 'bar-segment' })]
    }));
    const batteryBar = div({
        className: 'segmented-bar-container battery', children: [
            ...batterySegments,
        ]
    });
    const batteryLabels = div({
        className: 'gauge-labels-container',
        children: [span({}, 'E'), span({}, 'F')]
    });

    batteryContainer.appendChild(batteryTop);
    batteryContainer.appendChild(batteryBar);
    batteryContainer.appendChild(batteryLabels);

    bottomGauges.appendChild(fuelContainer);

    // Temperature Labels
    const externalTempContainer = div({ className: 'external-temp-container' });
    const externalTempValue = span({ className: 'temp-value', children: [getState('outside_temp') + '°C'] });
    const externalTempLabel = span({ className: 'temp-sub-label', children: ['External'] });
    externalTempContainer.appendChild(externalTempValue);
    externalTempContainer.appendChild(externalTempLabel);

    const internalTempContainer = div({ className: 'internal-temp-container' });
    const internalTempValue = span({ className: 'temp-value', children: [getState('inside_temp') + '°C'] });
    const internalTempLabel = span({ className: 'temp-sub-label', children: ['Internal'] });
    internalTempContainer.appendChild(internalTempValue);
    internalTempContainer.appendChild(internalTempLabel);

    // EV Mode Display (Bottom Right)
    const bottomEvMode = div({ className: 'dashboard-bottom-ev-mode' });
    const bottomEvLabel = span({ className: 'bottom-ev-label' });

    const updateBottomEv = (val) => {
        const cleanVal = String(val).toUpperCase().replace(/'/g, "");
        if (cleanVal === 'EV') {
            bottomEvLabel.textContent = 'EV';
            bottomEvLabel.style.color = '#00beff'; // Blue
        } else if (cleanVal === 'EVP') {
            bottomEvLabel.textContent = 'EV';
            bottomEvLabel.style.color = '#4CAF50'; // Green (EVP)
        } else if (cleanVal === 'HEV') {
            bottomEvLabel.textContent = 'HEV';
            bottomEvLabel.style.color = '#ffffff'; // White (HEV)
        } else {
            bottomEvLabel.textContent = val;
            bottomEvLabel.style.color = '#ffffff';
        }
    };
    updateBottomEv(getState('evMode'));

    bottomEvMode.appendChild(bottomEvLabel);
    bottomGauges.appendChild(batteryContainer);

    container.appendChild(topCenter);
    container.appendChild(speedContainer);
    container.appendChild(bottomGauges);
    container.appendChild(externalTempContainer);
    container.appendChild(internalTempContainer);
    container.appendChild(bottomEvMode);
    container.appendChild(menuWrapper);

    const updateCleanModeVisibility = (maskState) => {
        // mask = 0: Hide dashboard elements (Clean mode effect)
        // mask = 1 or 2: Show dashboard elements
        const shouldHide = maskState === 0;
        const display = shouldHide ? 'none' : 'flex';

        topCenter.style.display = display;
        speedContainer.style.display = display;
        bottomGauges.style.display = display;
        bottomEvMode.style.display = display;

        // Temperatures should stay visible
        externalTempContainer.style.display = 'flex';
        internalTempContainer.style.display = 'flex';
    };

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

    let prevSpeed = getState('carSpeed') || 0;

    const updateSpeedRotation = (speed) => {
        const rotation = (speed * 1);
        speedNeedleContainer.style.transform = `rotate(${rotation}deg)`;

        const speedDelta = speed - prevSpeed;
        const dynamicSpacing = 2 + Math.max(0, speedDelta);

        const headAlignmentOffset = 125 - (speedDelta * 5);
        trails.forEach((trail, i) => {
            const isMainTrail = i === trails.length - 1;
            const effectiveIndex = isMainTrail ? (trails.length - 1) / 2 : i;
            const trailSpacing = effectiveIndex * dynamicSpacing;
            trail.style.transform = `translate(-50%, -50%) rotate(${rotation + headAlignmentOffset + trailSpacing}deg)`;
        });

        prevSpeed = speed;
    };

    const sub0 = subscribe('clockTime', val => clock.textContent = val);
    const sub1 = subscribe('gearState', val => {
        gear.textContent = val;
        updateGearColor(val);
    });
    const sub2 = subscribe('evModeLabel', val => {
        evMode.textContent = val;
        updateEvModeColor(val);
    });
    const sub4 = subscribe('evMode', val => {
        updateBottomEv(val);
    });
    const sub3 = subscribe('carSpeed', val => {
        speedValue.textContent = val;
        updateSpeedRotation(val);
    });
    const sub5 = subscribe('fuelPercent', val => {
        updateBarSegments(fuelSegments, val);
        fuelTop.querySelector('.fuel-percent').textContent = val + '%';
    });
    const sub6 = subscribe('batteryPercent', val => {
        updateBarSegments(batterySegments, val);
        batteryTop.querySelector('.battery-percent').textContent = val + '%';
    });
    const sub7 = subscribe('fuelRange', val => {
        const rangeSpan = fuelTop.querySelector('.fuel-range');
        if (rangeSpan && rangeSpan.childNodes[0]) rangeSpan.childNodes[0].textContent = val;
    });
    const sub8 = subscribe('batteryRange', val => {
        const rangeSpan = batteryTop.querySelector('.battery-range');
        if (rangeSpan && rangeSpan.childNodes[0]) rangeSpan.childNodes[0].textContent = val;
    });
    const sub9 = subscribe('outside_temp', val => externalTempValue.textContent = val + '°C');
    const sub10 = subscribe('inside_temp', val => internalTempValue.textContent = val + '°C');
    const sub11 = subscribe('mask', val => updateCleanModeVisibility(val));

    updateBarSegments(fuelSegments, getState('fuelPercent'));
    updateBarSegments(batterySegments, getState('batteryPercent'));
    updateSpeedRotation(getState('carSpeed'));
    updateCleanModeVisibility(getState('mask'));

    container.cleanup = () => {
        clearInterval(clockInterval);
        [sub0, sub1, sub2, sub3, sub4, sub5, sub6, sub7, sub8, sub9, sub10, sub11].forEach(un => un());
    };

    return { container, menuWrapper };
}
