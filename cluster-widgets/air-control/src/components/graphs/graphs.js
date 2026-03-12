import { getState, setState, subscribe } from '../../state.js';
import { div, img, span } from '../../utils/createElement.js';

import { Chart, registerables } from 'chart.js';
import streamingPlugin from 'chartjs-plugin-streaming';
import 'chartjs-adapter-date-fns';
import { WarpTunnelAnimation } from './warpTunnel.js';
Chart.register(...registerables, streamingPlugin);

const HISTORY_DURATION = 30000; //ms
const TIMER_HIDE_DELAY = 30000; //ms
const UI_UPDATE_INTERVAL = 100; //ms
const ACCELERATION_THRESHOLD = 10; //s (ie 100km/h in 5s = 5, 100km/h in 10s = 10)

const hexToRgba = (hex, alpha) => {
    let r = 255, g = 255, b = 255;
    if (hex.startsWith('#')) {
        const h = hex.replace('#', '');
        if (h.length === 3) {
            r = parseInt(h[0] + h[0], 16);
            g = parseInt(h[1] + h[1], 16);
            b = parseInt(h[2] + h[2], 16);
        } else if (h.length >= 6) {
            r = parseInt(h.substring(0, 2), 16);
            g = parseInt(h.substring(2, 4), 16);
            b = parseInt(h.substring(4, 6), 16);
        }
    }
    return `rgba(${r}, ${g}, ${b}, ${alpha})`;
};

export const graphList = [
    {
        id: 'evConsumption',
        displayLabel: 'Potência EV',
        decimalPlaces: 1,
        secondaryDecimalPlaces: 1,
        yAxis: {
            min: -100,
            max: 140,
            stepSize: 50
        },
        y1Axis: {
            min: -100,
            max: 140,
            stepSize: 50
        },
        datasets: [
            {
                label: 'Potência EV',
                dataKey: 'evPowerKw',
                smooth: true,
                smoothFactor: 100, // Join data close by 100ms
                unity: 'kWatts',
                yAxisID: 'y',
                lineColor: '#ffffff',
                positiveColor: '#ffffff',
                negativeColor: '#00ff88'
            },
            {
                label: 'Média',
                dataKey: 'evPowerKw',
                type: 'average',
                avgConfig: { seconds: 30 },
                smooth: true,
                unity: 'kWavg',
                yAxisID: 'y1',
                lineColor: '#00c3ff'
            }
        ]
    },
    {
        id: 'gasConsumption',
        displayLabel: 'Consumo Misto',
        decimalPlaces: 1,
        secondaryDecimalPlaces: 1,
        yAxis: {
            min: -43,
            max: 120,
            stepSize: 15
        },
        y1Axis: {
            min: -10,
            max: 30,
            stepSize: 10
        },
        datasets: [
            {
                label: 'Consumo Elétrico',
                dataKey: 'instantEVConsumption',
                smooth: true,
                smoothFactor: 100, // Join data close by 100ms
                unity: 'KWh/100km',
                yAxisID: 'y',
                lineColor: '#ffffff',
                valueFilter: 'positive'
            },
            {
                label: 'Consumo Instantâneo',
                dataKey: 'gasConsumption',
                smooth: true,
                unity: 'Km/L',
                yAxisID: 'y1',
                idleKey: 'gasConsumptionIdle',
                idleUnity: 'l/100km',
                lineColor: '#ff5500',
                valueFilter: 'positive'
            },
            {
                label: 'Média EV',
                dataKey: 'instantEVConsumption',
                type: 'average',
                avgConfig: { seconds: 30 },
                smooth: true,
                unity: 'KWh/100km',
                yAxisID: 'y2',
                followAxis: 'y',
                lineColor: '#00c3ff',
                valueFilter: 'positive'
            }
        ]
    },
    {
        id: 'carSpeed',
        displayLabel: 'Velocidade',
        decimalPlaces: 0,
        secondaryDecimalPlaces: 1,
        yAxis: {
            min: -72,
            max: 200,
            stepSize: 40
        },
        y1Axis: {
            min: -10,
            max: 30,
            stepSize: 10
        },
        datasets: [
            {
                label: 'Velocidade',
                dataKey: 'carSpeed',
                smooth: true,
                unity: 'km/h',
                yAxisID: 'y',
                lineColor: '#ffffff'
            },
            {
                label: 'Consumo',
                dataKey: 'gasConsumption',
                smooth: true,
                unity: 'km/L',
                yAxisID: 'y1',
                lineColor: '#ff5500'
            }
        ]
    },
];

const historicalData = {};

const addDataPoint = (dataKey, value, datasetConfig = {}) => {
    if (value === undefined || value === null) return;
    const now = Date.now();
    if (!historicalData[dataKey]) historicalData[dataKey] = [];

    const data = historicalData[dataKey];
    const smoothFactor = datasetConfig.smoothFactor || 0;

    if (data.length > 0) {
        const lastPoint = data[data.length - 1];
        const timeGap = now - lastPoint.x;


        // Data Thinning
        if (value !== 0 && lastPoint.y !== 0 && timeGap < smoothFactor) {
            lastPoint.y = (lastPoint.y + value) / 2;
            return;
        }

        // Bridge Logic for 0 value
        if (lastPoint.y === 0 && Math.abs(value) > 0 && timeGap > 500) {
            data.push({ x: now - 50, y: 0 });
        }
        else if (Math.abs(lastPoint.y) > 0 && value === 0 && timeGap > 500) {
            data.push({ x: now - 50, y: lastPoint.y });
        }
    }

    data.push({ x: now, y: value });

    const DURATION = HISTORY_DURATION;
    while (data.length > 0 && now - data[0].x > DURATION) {
        data.shift();
    }
};

function initializeGlobalDataStore() {
    graphList.forEach(graph => {
        graph.datasets.forEach(dataset => {
            if (dataset.dataKey) {
                historicalData[dataset.dataKey] = [];

                let currentKey = dataset.dataKey;
                if (dataset.valueFilter) {
                    const filterKey = `${dataset.dataKey}_filtered_${dataset.valueFilter}`;
                    historicalData[filterKey] = [];
                    dataset.filterInternalKey = filterKey;
                    currentKey = filterKey;
                }

                if (dataset.type === 'average') {
                    const avgKey = `${currentKey}_avg_${dataset.avgConfig.samples || dataset.avgConfig.seconds || '10'}`;
                    historicalData[avgKey] = [];
                    dataset.avgInternalKey = avgKey;
                }
            }
            if (dataset.idleKey) {
                dataKeys.add(dataset.idleKey); // This will handle gasConsumptionIdle
                historicalData[dataset.idleKey] = [];
            }
        });
    });
}

const dataKeys = new Set();
const averagesToTrack = []; // List of { sourceKey, targetKey, config }
const filtersToTrack = []; // List of { sourceKey, targetKey, filterType }

function startGlobalDataCollector() {
    initializeGlobalDataStore();

    const allDatasets = [];
    graphList.forEach(g => allDatasets.push(...g.datasets));

    graphList.forEach(graph => {
        graph.datasets.forEach(dataset => {
            if (dataset.dataKey) {
                dataKeys.add(dataset.dataKey);

                if (dataset.filterInternalKey) {
                    filtersToTrack.push({
                        sourceKey: dataset.dataKey,
                        targetKey: dataset.filterInternalKey,
                        filterType: dataset.valueFilter
                    });
                }

                if (dataset.avgInternalKey) {
                    averagesToTrack.push({
                        sourceKey: dataset.filterInternalKey || dataset.dataKey,
                        targetKey: dataset.avgInternalKey,
                        config: dataset.avgConfig
                    });
                }
            }
        });
    });

    dataKeys.forEach(dataKey => {
        subscribe(dataKey, (value) => {
            const datasetCfg = allDatasets.find(d =>
                d.dataKey === dataKey ||
                d.filterInternalKey === dataKey ||
                d.avgInternalKey === dataKey
            ) || {};

            addDataPoint(dataKey, value, datasetCfg);

            // Handle Filters
            filtersToTrack.forEach(filter => {
                if (filter.sourceKey === dataKey) {
                    let passes = true;
                    if (filter.filterType === 'positive') passes = value >= 0;
                    else if (filter.filterType === 'negative') passes = value <= 0;
                    const currentFilterCfg = allDatasets.find(d => d.filterInternalKey === filter.targetKey) || {};

                    if (passes) {
                        addDataPoint(filter.targetKey, value, currentFilterCfg);
                        setState(filter.targetKey, value);
                    } else {
                        addDataPoint(filter.targetKey, 0, currentFilterCfg);
                        setState(filter.targetKey, 0);
                    }
                }
            });

            // Generic average handling
            averagesToTrack.forEach(avg => {
                if (avg.sourceKey === dataKey || filtersToTrack.some(f => f.targetKey === avg.sourceKey && f.sourceKey === dataKey)) {
                    // Check if this avg relies on the filtered key or raw key
                    const activeKey = (filtersToTrack.find(f => f.sourceKey === dataKey && f.targetKey === avg.sourceKey)) ? avg.sourceKey : dataKey;

                    // If it matches either raw or the specific filtered key we just updated
                    if (avg.sourceKey === activeKey) {
                        const sourceData = historicalData[activeKey];

                        if (sourceData && sourceData.length > 0) {
                            let filteredData = sourceData;

                            if (avg.config.samples) {
                                filteredData = sourceData.slice(-avg.config.samples);
                            } else if (avg.config.seconds) {
                                const now = Date.now();
                                const threshold = now - (avg.config.seconds * 1000);
                                filteredData = sourceData.filter(p => p.x >= threshold);
                            }

                            if (filteredData.length > 0) {
                                const sum = filteredData.reduce((acc, point) => acc + point.y, 0);
                                const denominator = avg.config.samples || filteredData.length;
                                const avgVal = sum / denominator;
                                const avgCfg = allDatasets.find(d => d.avgInternalKey === avg.targetKey) || {};
                                addDataPoint(avg.targetKey, avgVal, avgCfg);
                                setState(avg.targetKey, avgVal);
                            }
                        }
                    }
                }
            });
        });
    });


    // Pulse timer for smoothing logic that depends on time/samples
    const smoothingStates = new Map();

    graphList.forEach(graph => {
        graph.datasets.forEach(dataset => {
            if (dataset.smooth && dataset.dataKey) {
                const rawKey = dataset.dataKey;
                const smoothedKey = `${rawKey}_smoothed`;

                dataset.dataKey = smoothedKey;

                if (!smoothingStates.has(rawKey)) {
                    smoothingStates.set(rawKey, {
                        samples: 0,
                        targetKey: smoothedKey
                    });
                    dataKeys.add(rawKey);
                }
            }
        });
    });

    setInterval(() => {
        smoothingStates.forEach((state, rawKey) => {
            const rawValue = getState(rawKey) || 0;

            if (state.emaValue === undefined) {
                state.emaValue = rawValue;
            }

            // Exponential Moving Average (EMA) for noise reduction
            // Alpha 0.3 provides a good balance between responsiveness and smoothness
            const alpha = 0.3;
            state.emaValue = (state.emaValue * (1 - alpha)) + (rawValue * alpha);

            setState(state.targetKey, state.emaValue);
            addDataPoint(state.targetKey, state.emaValue);
        });
    }, 200);
}

startGlobalDataCollector();

const graphController = {

    chartInstance: null,
    isInitialized: false,
    currentGraphId: null,
    lastCarSpeed: getState('carSpeed') || 0.0,
    warpTunnel: null,
    warpTunnelCanvas: null,
    isSpeedTimerRunning: false,
    speedTimerStartTime: 0,
    timerHideTimeoutId: null,
    last0To100Time: null,
    flashTriggered: false,

    triggerFlash(color = 'white') {
        const flashOverlay = document.querySelector('.graph-flash-overlay');
        if (!flashOverlay || this.flashTriggered) return;

        this.flashTriggered = true;
        flashOverlay.style.background = `radial-gradient(circle, white 0%, ${color} 100%)`;
        flashOverlay.classList.add('screen-flash-animation');

        const onAnimationEnd = () => {
            flashOverlay.classList.remove('screen-flash-animation');
            flashOverlay.style.background = '';
            flashOverlay.removeEventListener('animationend', onAnimationEnd);
        };
        flashOverlay.addEventListener('animationend', onAnimationEnd);
    },

    setWarpAnimation(visible) {
        if (!this.warpTunnel || !this.warpTunnelCanvas) return;

        const graphContainer = document.querySelector('.graph-screen');
        if (visible) {
            if (!this.warpTunnelCanvas.classList.contains('visible')) {
                if (graphContainer) graphContainer.classList.add('warp-active');
                this.warpTunnelCanvas.classList.add('visible');
                this.warpTunnel.start();
            }
        } else {
            if (this.warpTunnelCanvas.classList.contains('visible')) {
                this.warpTunnelCanvas.classList.remove('visible');
                setTimeout(() => {
                    if (!this.warpTunnelCanvas.classList.contains('visible')) {
                        if (graphContainer) graphContainer.classList.remove('warp-active');
                        this.warpTunnel.stop();
                    }
                }, 2000);
            }
        }
    },

    setChronometer(action) {
        const tooltip = document.getElementById('timer-tooltip');
        const valueEl = document.getElementById('timer-tooltip-value');

        switch (action) {
            case 'start':
                this.isSpeedTimerRunning = true;
                this.speedTimerStartTime = Date.now();
                this.last0To100Time = null;
                this.flashTriggered = false;
                if (this.timerHideTimeoutId) {
                    clearTimeout(this.timerHideTimeoutId);
                    this.timerHideTimeoutId = null;
                }
                if (tooltip) tooltip.classList.add('visible');
                if (valueEl) valueEl.textContent = '0.0s';
                break;

            case 'stop': // Immediate hide/reset
                this.isSpeedTimerRunning = false;
                this.flashTriggered = false;
                if (tooltip) tooltip.classList.remove('visible');
                break;

            case 'success_hold': // Start 10s timeout to hide
                this.isSpeedTimerRunning = false;
                if (!this.timerHideTimeoutId) {
                    this.timerHideTimeoutId = setTimeout(() => {
                        this.setChronometer('stop');
                        this.timerHideTimeoutId = null;
                    }, TIMER_HIDE_DELAY);
                }
                break;
        }
    },

    init(canvasContext) {
        if (this.isInitialized) return;

        this.warpTunnelCanvas = document.getElementById('warp-tunnel-canvas');
        if (this.warpTunnelCanvas) {
            this.warpTunnel = new WarpTunnelAnimation(this.warpTunnelCanvas);
        }

        this.lastCarSpeed = getState('carSpeed') || 0.0;

        this.chartInstance = new Chart(canvasContext, {
            type: 'line',
            data: {
                datasets: [
                    {
                        label: '',
                        backgroundColor: 'rgba(0, 120, 255, 0.15)',
                        borderColor: '#00c3ff',
                        borderWidth: 2,
                        pointRadius: 0,
                        fill: true,
                        cubicInterpolationMode: 'monotone',
                        tension: 0.4,
                        shadowColor: 'rgba(0, 195, 255, 0.5)',
                        shadowBlur: 10,
                        segment: {
                            borderColor: ctx => ctx.p0.parsed.y < 0 ? '#00ff88' : '#00c3ff',
                            backgroundColor: ctx => ctx.p0.parsed.y < 0 ? 'rgba(0, 255, 136, 0.15)' : 'rgba(0, 120, 255, 0.15)'
                        }
                    },
                    {
                        label: '',
                        backgroundColor: 'rgba(0, 120, 255, 0.15)',
                        borderColor: '#00c3ff',
                        borderWidth: 2,
                        pointRadius: 0,
                        fill: true,
                        cubicInterpolationMode: 'monotone',
                        tension: 0.4,
                        shadowColor: 'rgba(0, 195, 255, 0.5)',
                        shadowBlur: 10,
                    }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { display: false },
                    tooltip: { enabled: false },
                },
                layout: {
                    padding: {
                        left: 18,
                        right: 18,
                        top: 0,
                        bottom: 0
                    }
                },
                scales: {
                    x: {
                        type: 'realtime',
                        display: false,
                        realtime: {
                            duration: HISTORY_DURATION,
                            refresh: 250
                        }
                    },
                    y: {
                        min: -20,
                        max: 120,
                        ticks: {
                            display: true,
                            mirror: true,
                            padding: 0,
                            stepSize: 20,
                            color: 'rgba(100,172,255,0.7)',
                        },
                        grid: {
                            display: true,
                            drawOnChartArea: true,
                            drawTicks: false,
                            color: (ctx) => (ctx.tick.value === 0 ? 'rgba(0, 195, 255, 1)' : 'rgba(0,160,255,0.3)'),
                            lineWidth: (ctx) => (ctx.tick.value === 0 ? 4 : 1),
                        },
                    },
                    y1: {
                        id: 'y1',
                        position: 'right',
                        min: 0,
                        max: 200,
                        ticks: {
                            display: true,
                            mirror: true,
                            padding: 0,
                            align: 'start',
                            stepSize: 2,
                            color: 'rgba(0, 255, 187, 0.5)',
                        },
                        grid: {
                            drawOnChartArea: false,
                        }
                    },
                    y2: {
                        id: 'y2',
                        display: false,
                        grid: {
                            drawOnChartArea: false,
                        }
                    }
                }
            }
        });

        this.uiUpdateInterval = setInterval(() => {
            try {
                if (!this.isInitialized || !this.currentGraphId) return;

                const graphInfo = graphList.find(g => g.id === this.currentGraphId);
                if (!graphInfo) return;

                const primaryTooltipEl = document.querySelector('.dynamic-tooltip.primary');
                const secondaryTooltipEl = document.querySelector('.dynamic-tooltip.secondary');
                const primaryLineEl = document.querySelector('.dynamic-tooltip-line.primary');
                const secondaryLineEl = document.querySelector('.dynamic-tooltip-line.secondary');
                const speedTimerTooltip = document.getElementById('timer-tooltip');
                const speedTimerValue = document.getElementById('timer-tooltip-value');
                const LINE_OFFSET = 13;

                if (primaryTooltipEl) {
                    primaryTooltipEl.style.display = 'none';
                    primaryTooltipEl.style.opacity = '0';
                }
                if (primaryLineEl) primaryLineEl.style.opacity = '0';
                if (secondaryTooltipEl) {
                    secondaryTooltipEl.style.display = 'none';
                    secondaryTooltipEl.style.opacity = '0';
                }
                if (secondaryLineEl) secondaryLineEl.style.opacity = '0';

                // Find datasets for tooltips based on yAxisID
                const primaryDS = graphInfo.datasets.find(ds => ds.yAxisID === 'y');
                const secondaryDS = graphInfo.datasets.find(ds => ds.yAxisID === 'y1');

                let activeValue, activeUnity;
                let secValue, secUnity;

                if (primaryDS) {
                    const dataKey = primaryDS.avgInternalKey || primaryDS.filterInternalKey || primaryDS.dataKey;
                    const val = getState(dataKey);

                    if (graphInfo.id === 'gasConsumption' && primaryDS.idleKey && val <= 0) {
                        activeValue = getState(primaryDS.idleKey);
                        activeUnity = primaryDS.idleUnity;
                    } else {
                        activeValue = val;
                        activeUnity = primaryDS.unity;
                    }
                }

                if (secondaryDS) {
                    const dataKey = secondaryDS.avgInternalKey || secondaryDS.filterInternalKey || secondaryDS.dataKey;
                    secValue = getState(dataKey);
                    secUnity = secondaryDS.unity;
                }

                const yAxis = this.chartInstance.scales.y;
                const y1Axis = this.chartInstance.scales.y1;

                if (primaryDS && primaryTooltipEl && primaryLineEl) {
                    const activeVal = activeValue !== undefined ? activeValue : 0;
                    const decimalPlaces = graphInfo.decimalPlaces !== undefined ? graphInfo.decimalPlaces : 1;
                    const valueEl = primaryTooltipEl.querySelector('.tooltip-value');
                    const unityEl = primaryTooltipEl.querySelector('.tooltip-unity');

                    let dsColor = (primaryDS && primaryDS.lineColor) || '#ffffff';
                    let txtColor = (primaryDS && (primaryDS.tooltipColor || primaryDS.lineColor)) || '#ffffff';

                    if (primaryDS && primaryDS.positiveColor && activeVal >= 0) {
                        dsColor = primaryDS.positiveColor;
                        txtColor = primaryDS.tooltipColor || primaryDS.positiveColor;
                    } else if (primaryDS && primaryDS.negativeColor && activeVal < 0) {
                        dsColor = primaryDS.negativeColor;
                        txtColor = primaryDS.tooltipColor || primaryDS.negativeColor;
                    }

                    primaryTooltipEl.style.display = 'flex';
                    primaryTooltipEl.style.opacity = '1';

                    valueEl.textContent = activeVal.toFixed(decimalPlaces);
                    valueEl.style.color = txtColor;
                    unityEl.style.color = txtColor;

                    if (activeVal < 0 && graphInfo.id === 'gasConsumption') {
                        valueEl.classList.add('negative');
                    } else {
                        valueEl.classList.remove('negative');
                    }

                    unityEl.textContent = activeUnity;
                    primaryLineEl.style.top = `${yAxis.getPixelForValue(activeVal) + LINE_OFFSET}px`;
                    primaryLineEl.style.backgroundColor = dsColor;
                    primaryLineEl.style.opacity = 0.5;
                }

                if (secondaryDS && secondaryTooltipEl && secondaryLineEl) {
                    const val = secValue !== undefined ? secValue : 0;
                    const decimalPlaces = graphInfo.secondaryDecimalPlaces !== undefined ? graphInfo.secondaryDecimalPlaces : 1;
                    const valueEl = secondaryTooltipEl.querySelector('.tooltip-value');
                    const unityEl = secondaryTooltipEl.querySelector('.tooltip-unity');

                    let dsColor = (secondaryDS && secondaryDS.lineColor) || '#00c3ff';
                    let txtColor = (secondaryDS && (secondaryDS.tooltipColor || secondaryDS.lineColor)) || '#00c3ff';

                    if (secondaryDS.positiveColor && val >= 0) {
                        dsColor = secondaryDS.positiveColor;
                        txtColor = secondaryDS.tooltipColor || secondaryDS.positiveColor;
                    } else if (secondaryDS.negativeColor && val < 0) {
                        dsColor = secondaryDS.negativeColor;
                        txtColor = secondaryDS.tooltipColor || secondaryDS.negativeColor;
                    }

                    secondaryTooltipEl.style.display = 'flex';
                    secondaryTooltipEl.style.opacity = '1';
                    secondaryLineEl.style.display = 'block';

                    valueEl.textContent = val.toFixed(decimalPlaces);
                    unityEl.textContent = secUnity;

                    valueEl.style.color = txtColor;
                    unityEl.style.color = txtColor;

                    const axis = (secondaryDS && secondaryDS.yAxisID === 'y1') ? y1Axis : yAxis;
                    secondaryLineEl.style.top = `${axis.getPixelForValue(val) + LINE_OFFSET}px`;
                    secondaryLineEl.style.backgroundColor = dsColor;
                    secondaryLineEl.style.opacity = 0.5;
                }

                // Speed Timer / Chronometer Special Logic (only for carSpeed)
                if (graphInfo.id === 'carSpeed') {
                    const currentSpeed = getState('carSpeed') || 0.0;
                    const drivingMode = getState('drivingMode');
                    const acceleration = parseFloat(currentSpeed - this.lastCarSpeed) * (1000 / UI_UPDATE_INTERVAL);
                    const isFastAcceleration = acceleration >= (100 / ACCELERATION_THRESHOLD);

                    if (this.isSpeedTimerRunning) {
                        const elapsedTime = (Date.now() - this.speedTimerStartTime) / 1000;
                        if (elapsedTime > 15) {
                            this.triggerFlash('red');
                            this.setWarpAnimation(false);
                            this.setChronometer('stop');
                        } else if (currentSpeed >= 100) {
                            if (!this.last0To100Time) {
                                this.last0To100Time = elapsedTime.toFixed(1);
                            }
                            if (speedTimerValue) speedTimerValue.textContent = `${this.last0To100Time}s`;

                            // Flash only in Sport mode
                            if (drivingMode === 'Sport') {
                                this.triggerFlash('white');
                            }

                            this.setChronometer('success_hold');
                        } else if (currentSpeed === 0) {
                            this.setWarpAnimation(false);
                            this.setChronometer('stop');
                        } else {
                            if (speedTimerValue) speedTimerValue.textContent = `${elapsedTime.toFixed(1)}s`;
                            if (speedTimerTooltip && !speedTimerTooltip.classList.contains('visible')) {
                                speedTimerTooltip.classList.add('visible');
                            }
                            if (acceleration > 0 && drivingMode === 'Sport') {
                                this.setWarpAnimation(true);
                                if (this.warpTunnel) this.warpTunnel.setSpeed(currentSpeed);
                            }
                        }
                    } else {
                        if (currentSpeed === 0) {
                            this.setChronometer('ready');
                        } else if (isFastAcceleration && currentSpeed < 10 && currentSpeed > 1 && drivingMode === 'Sport') {
                            this.setChronometer('start');
                        }
                    }

                    const shouldWarp = this.lastCarSpeed === 0 && currentSpeed > 0 && isFastAcceleration && drivingMode === 'Sport';
                    if (shouldWarp) {
                        this.triggerFlash('orange');
                        this.setChronometer('start');
                        this.setWarpAnimation(true);
                        if (this.warpTunnel) this.warpTunnel.setSpeed(currentSpeed);
                    } else if ((currentSpeed >= 100) && (currentSpeed < this.lastCarSpeed)) {
                        this.setWarpAnimation(false);
                    }

                    this.lastCarSpeed = currentSpeed;
                } else {
                    this.setWarpAnimation(false);
                    this.setChronometer('stop');
                }
                this.chartInstance.update('quiet');

                // Update Power/RPM Ring
                const powerV = getState('evPowerFactor');
                const rpmV = getState('engineRPM');

                const powerBar = document.getElementById('graph-power-bar-svg');
                const rpmBar = document.getElementById('graph-rpm-bar-svg');
                const rpmLabelHtml = document.getElementById('graph-rpm-label-html');
                const powerLabelHtml = document.getElementById('graph-power-label-html');
                const labelWrapper = document.querySelector('.graph-vertical-labels-wrapper');

                if (powerBar && rpmBar) {
                    const isRegen = powerV < 0;

                    if (isRegen) {
                        powerBar.classList.add('regen-active');
                    } else {
                        powerBar.classList.remove('regen-active');
                    }

                    const wrapAngle = (a) => ((a % 360) + 360) % 360;

                    // Scaling: Power 100 = 180 deg if positive and 90 deg if negative, RPM 7000 = 90 deg
                    const powerAngleWidth = powerV >= 0
                        ? (powerV / 100) * 180
                        : (powerV / 100) * 90;
                    const rpmAngleWidth = (rpmV / 7000) * 90;

                    const pStart = 270; // LEFT side
                    const tipAngle = wrapAngle(pStart + powerAngleWidth);

                    // Behavior: If power is negative, RPM starts at 270 (Left)
                    const GAP = 2; // Degrees gap
                    const rStart = (powerV >= 0 ? tipAngle : 270) + GAP;
                    const rEnd = wrapAngle(rStart + rpmAngleWidth);

                    const radius = 217; // Updated radius
                    const cx = 250, cy = 250;
                    const getCoords = (deg) => {
                        const rad = (deg - 90) * Math.PI / 180;
                        return { x: cx + radius * Math.cos(rad), y: cy + radius * Math.sin(rad) };
                    };



                    // Draw Power Bar
                    const pS = getCoords(pStart);
                    const pE = getCoords(tipAngle);
                    const pLarge = Math.abs(powerAngleWidth) > 180 ? 1 : 0;
                    const pSweep = powerV >= 0 ? 1 : 0;

                    if (Math.abs(powerAngleWidth) > 0.1) {
                        powerBar.setAttribute("d", `M ${pS.x} ${pS.y} A ${radius} ${radius} 0 ${pLarge} ${pSweep} ${pE.x} ${pE.y}`);
                        powerBar.style.opacity = 1;
                        powerBar.setAttribute("stroke-width", "8");

                        // Sectioned bars logic: 10 segments for 100%. 
                        // Power sweep is 180 deg (68.4px per 10%), Regen sweep is 90 deg (34.2px per 10%).
                        const dash = isRegen ? 30.2 : 64.4;
                        powerBar.setAttribute("stroke-dasharray", `${dash} 4.0`);
                    } else {
                        powerBar.style.opacity = 0;
                    }

                    // Draw RPM Bar
                    const rS = getCoords(rStart);
                    const rE = getCoords(rEnd);
                    const rLarge = Math.abs(rpmAngleWidth) > 180 ? 1 : 0;

                    if (rpmV > 1) {
                        rpmBar.setAttribute("d", `M ${rS.x} ${rS.y} A ${radius} ${radius} 0 ${rLarge} 1 ${rE.x} ${rE.y}`);
                        rpmBar.style.opacity = 1;
                        rpmBar.setAttribute("stroke-width", "8");

                        // Sectioned bars logic (7 segments for 7k RPM)
                        rpmBar.setAttribute("stroke-dasharray", "44.9 4.01");
                    } else {
                        rpmBar.style.opacity = 0;
                    }

                    // Update HTML Labels
                    if (rpmLabelHtml) {
                        const rpm = (rpmV / 1000).toFixed(1);
                        rpmLabelHtml.innerHTML = `<span class="val">${rpm}</span><span class="unit">RPM</span>`;
                        rpmLabelHtml.style.opacity = rpm > 0 ? 1 : 0;
                    }
                    if (powerLabelHtml) {
                        const pwr = Math.abs(Math.round(powerV));
                        powerLabelHtml.innerHTML = `<span class="val">${pwr}</span><span class="symbol">%</span>`;
                        powerLabelHtml.style.opacity = pwr > 0 ? 1 : 0;
                        if (isRegen) powerLabelHtml.classList.add('regen');
                        else powerLabelHtml.classList.remove('regen');
                    }
                    if (labelWrapper) {
                        if (rpmV > 1) labelWrapper.classList.add('has-rpm');
                        else labelWrapper.classList.remove('has-rpm');
                    }
                }
            } catch (error) {
                console.error('Error: ', error);
            }
        }, UI_UPDATE_INTERVAL);


        this.isInitialized = true;
    },

    switchTo(graphId) {
        if (!this.isInitialized) {
            return;
        }

        const graphInfo = graphList.find(g => g.id === graphId);
        if (!graphInfo) return;

        const scales = this.chartInstance.options.scales;
        const hasSecondaryAxis = graphInfo.datasets.some(ds => ds.yAxisID === 'y1');
        scales.y1.display = hasSecondaryAxis;

        const bulletContainer = document.querySelector('.graph-bullet-container');
        const tooltipLines = document.querySelectorAll('.dynamic-tooltip-line');
        if (bulletContainer && tooltipLines.length > 0) {
            if (hasSecondaryAxis) {
                bulletContainer.classList.add('position-left');
                bulletContainer.classList.remove('position-right');
                tooltipLines.forEach(line => line.style.width = '80%');

            } else {
                bulletContainer.classList.add('position-right');
                bulletContainer.classList.remove('position-left');
                tooltipLines.forEach(line => line.style.width = '95%');
            }
        }

        // Apply axis configurations from graphList
        if (graphInfo.yAxis) {
            scales.y.min = graphInfo.yAxis.min;
            scales.y.max = graphInfo.yAxis.max;
            scales.y.ticks.stepSize = graphInfo.yAxis.stepSize;
        }
        if (graphInfo.y1Axis) {
            scales.y1.min = graphInfo.y1Axis.min;
            scales.y1.max = graphInfo.y1Axis.max;
            scales.y1.ticks.stepSize = graphInfo.y1Axis.stepSize;
        }

        // Auto-set axis tick colors based on the first dataset assigned to each axis
        const yDS = graphInfo.datasets.find(ds => ds.yAxisID === 'y');
        if (yDS && yDS.lineColor) scales.y.ticks.color = yDS.lineColor + 'B3';

        const y1DS = graphInfo.datasets.find(ds => ds.yAxisID === 'y1');
        if (y1DS && y1DS.lineColor) scales.y1.ticks.color = y1DS.lineColor + 'B3';

        const newDatasets = [];

        graphInfo.datasets.forEach((datasetInfo) => {
            const dataKey = datasetInfo.avgInternalKey || datasetInfo.filterInternalKey || datasetInfo.dataKey;
            if (dataKey) {
                let color = datasetInfo.lineColor || '#ffffff';

                if (datasetInfo.yAxisID === 'y2' && datasetInfo.followAxis) {
                    const followDS = graphInfo.datasets.find(ds => ds.yAxisID === datasetInfo.followAxis);
                    if (followDS && followDS.lineColor && !datasetInfo.lineColor) {
                        color = followDS.lineColor;
                    }
                    // Sync Y2 scale with following axis
                    const followScale = datasetInfo.followAxis === 'y1' ? scales.y1 : scales.y;
                    scales.y2.min = followScale.min;
                    scales.y2.max = followScale.max;
                }

                const ds = {
                    label: datasetInfo.label,
                    data: historicalData[dataKey] || [],
                    yAxisID: datasetInfo.yAxisID,
                    borderColor: color,
                    backgroundColor: hexToRgba(color, datasetInfo.yAxisID === 'y' ? 0.15 : 0.1),
                    borderWidth: 2,
                    pointRadius: 0,
                    fill: datasetInfo.fill !== undefined ? datasetInfo.fill : (datasetInfo.yAxisID === 'y' || datasetInfo.yAxisID === 'y1'),
                    cubicInterpolationMode: 'monotone',
                    tension: 0.4,
                };

                // Add any other properties from config directly
                Object.keys(datasetInfo).forEach(key => {
                    if (!['label', 'dataKey', 'type', 'avgConfig', 'unity', 'yAxisID', 'lineColor', 'fill', 'avgInternalKey', 'followAxis', 'idleKey', 'idleUnity', 'tooltipColor'].includes(key)) {
                        ds[key] = datasetInfo[key];
                    }
                });

                // Conditional coloring based on positive/negative properties
                if (datasetInfo.positiveColor || datasetInfo.negativeColor) {
                    const posColor = datasetInfo.positiveColor || datasetInfo.lineColor || color;
                    const negColor = datasetInfo.negativeColor || datasetInfo.lineColor || color;

                    ds.segment = {
                        borderColor: ctx => ctx.p0.parsed.y < 0 ? negColor : posColor,
                        backgroundColor: ctx => ctx.p0.parsed.y < 0 ? hexToRgba(negColor, 0.15) : hexToRgba(posColor, 0.15)
                    };
                }
                newDatasets.push(ds);
            }
        });

        this.chartInstance.data.datasets = newDatasets;
        this.chartInstance.update();

        const tooltipEl = this.chartInstance.canvas.parentNode.querySelector('.dynamic-tooltip');
        if (tooltipEl) tooltipEl.style.opacity = 0;

        this.currentGraphId = graphId;
        this.chartInstance.update({ duration: 400 });
    },

    cleanup() {
        if (this.uiUpdateInterval) {
            clearInterval(this.uiUpdateInterval);
            this.uiUpdateInterval = null;
        }
        if (this.warpTunnel) {
            this.warpTunnel.stop();
        }
        if (this.timerHideTimeoutId) {
            clearTimeout(this.timerHideTimeoutId);
            this.timerHideTimeoutId = null;
        }
        if (this.chartInstance) {
            this.chartInstance.destroy();
            this.chartInstance = null;
        }
        this.isInitialized = false;
        this.currentGraphId = null;
    }

};

export function createGraphScreen() {

    var main = div({ className: 'main-container' });

    const container = div({ className: 'graph-screen' });

    const graphProgressRing = div({ className: 'graph-progress-ring' });
    graphProgressRing.id = 'graph-progress-ring';
    container.appendChild(graphProgressRing);

    const graphPowerRpmRing = div({ className: 'graph-power-rpm-ring' });
    graphPowerRpmRing.id = 'graph-power-rpm-ring';
    container.appendChild(graphPowerRpmRing);

    // SVG for curved labels
    const svgNS = "http://www.w3.org/2000/svg";
    const svg = document.createElementNS(svgNS, "svg");
    svg.setAttribute("viewBox", "0 0 500 500");
    svg.classList.add("graph-curved-labels");
    svg.style.position = "absolute";
    svg.style.width = "500px";
    svg.style.height = "500px";
    svg.style.top = "50%";
    svg.style.left = "50%";
    svg.style.transform = "translate(-50%, -50%)";
    svg.style.pointerEvents = "none";
    svg.style.zIndex = "10";

    const powerBar = document.createElementNS(svgNS, "path");
    powerBar.setAttribute("id", "graph-power-bar-svg");
    powerBar.setAttribute("fill", "none");
    svg.appendChild(powerBar);

    const rpmBar = document.createElementNS(svgNS, "path");
    rpmBar.setAttribute("id", "graph-rpm-bar-svg");
    rpmBar.setAttribute("fill", "none");
    svg.appendChild(rpmBar);

    container.appendChild(svg);

    // New HTML labels for vertical stacking and rotation
    const labelContainer = div({ className: 'graph-vertical-labels-wrapper' });
    const rpmLabel = div({ className: 'graph-vertical-label rpm', id: 'graph-rpm-label-html' });
    rpmLabel.innerHTML = '<span class="unit">kRPM</span><span class="val">0.0</span>';
    const powerLabel = div({ className: 'graph-vertical-label power', id: 'graph-power-label-html' });
    powerLabel.innerHTML = '<span class="val">0</span><span class="symbol">%</span>';

    labelContainer.appendChild(rpmLabel);
    labelContainer.appendChild(powerLabel);
    container.appendChild(labelContainer);

    const divider = div({ className: 'graph-selector-line' });
    const outerRing = div({ className: 'graph-outer-ring' });
    const innerBorder = div({ className: 'graph-inner-border' });
    const innerRingShadow = div({ className: 'graph-inner-ring-shadow' });
    const innerRing = div({ className: 'graph-inner-ring' });

    const dynamicTooltip = div({ className: 'dynamic-tooltip primary' });
    const tooltipValue = span({ className: 'tooltip-value' });
    const tooltipUnity = span({ className: 'tooltip-unity' });
    dynamicTooltip.appendChild(tooltipValue);
    dynamicTooltip.appendChild(tooltipUnity);

    const secondaryTooltip = div({ className: 'dynamic-tooltip secondary' });
    const secondaryTooltipValue = span({ className: 'tooltip-value' });
    const secondaryTooltipUnity = span({ className: 'tooltip-unity' });
    secondaryTooltip.appendChild(secondaryTooltipValue);
    secondaryTooltip.appendChild(secondaryTooltipUnity);

    const dynamicTooltipLine = div({ className: 'dynamic-tooltip-line primary' });
    const secondaryTooltipLine = div({ className: 'dynamic-tooltip-line secondary' });

    const tooltipMask = div({ className: 'graph-tooltip-mask' });
    innerRing.appendChild(tooltipMask);
    innerRing.appendChild(dynamicTooltip);
    innerRing.appendChild(secondaryTooltip);
    innerRing.appendChild(dynamicTooltipLine);
    innerRing.appendChild(secondaryTooltipLine);

    const timerTooltip = div({ className: 'timer-tooltip', id: 'timer-tooltip' });
    const timerTooltipValue = span({ className: 'timer-tooltip-value', id: 'timer-tooltip-value' });
    const timerTooltipUnity = span({ className: 'timer-tooltip-unity' });
    timerTooltip.appendChild(timerTooltipValue);
    timerTooltip.appendChild(timerTooltipUnity);
    timerTooltipValue.textContent = '--.-s';
    timerTooltipUnity.textContent = '0 - 100 km/h';

    innerRing.appendChild(timerTooltip);



    const warpCanvas = document.createElement('canvas');
    warpCanvas.id = 'warp-tunnel-canvas';
    warpCanvas.className = 'warp-tunnel-canvas';
    container.appendChild(warpCanvas);

    const canvas = document.createElement('canvas');
    canvas.className = 'graph-chart';
    canvas.id = 'graph-chart';
    innerRing.appendChild(canvas);

    container.appendChild(outerRing);
    container.appendChild(innerBorder);
    container.appendChild(innerRingShadow);
    container.appendChild(innerRing);

    const flashOverlay = div({ className: 'graph-flash-overlay' });
    container.appendChild(flashOverlay);

    main.appendChild(container);

    const bulletContainer = div({ className: 'graph-bullet-container' });
    const bulletElements = {};

    graphList.forEach((itemData) => {
        const bulletEl = div({
            id: `bullet-${itemData.id}`,
            className: 'graph-bullet',
            'data-id': itemData.id
        });
        bulletContainer.appendChild(bulletEl);
        bulletElements[itemData.id] = bulletEl;
    });

    container.appendChild(bulletContainer);

    const graphTitleLabel = div({ className: 'graph-title-label' });
    container.appendChild(graphTitleLabel);

    setTimeout(() => {
        const ctx = document.getElementById('graph-chart');
        if (ctx) {
            graphController.init(ctx);
            graphController.switchTo(getState('currentGraph'));
        }

        subscribe('currentGraph', (newGraphId) => {
            graphController.switchTo(newGraphId);
            updateFocus(newGraphId);
        });

        updateFocus(getState('currentGraph'));

    }, 0);

    main.cleanup = () => {
        graphController.cleanup();
    };

    const updateFocus = (currentGraphId) => {
        Object.values(bulletElements).forEach(bullet => {
            if (bullet.dataset.id === currentGraphId) {
                bullet.classList.add('active');
            } else {
                bullet.classList.remove('active');
            }
        });

        const currentItem = graphList.find(item => item.id === currentGraphId);
        if (currentItem) {
            var currentLabel = currentItem.displayLabel;
        }
        graphTitleLabel.textContent = currentLabel;
    };

    return main;
}
