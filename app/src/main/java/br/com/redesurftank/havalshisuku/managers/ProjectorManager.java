package br.com.redesurftank.havalshisuku.managers;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import br.com.redesurftank.App;
import br.com.redesurftank.havalshisuku.models.CarConstants;
import br.com.redesurftank.havalshisuku.projectors.InstrumentProjector;
import br.com.redesurftank.havalshisuku.projectors.InstrumentProjector2;

public class ProjectorManager {
    private static final String TAG = "ProjectorManager";

    private static ProjectorManager instance;

    private SharedPreferences sharedPreferences;
    private DisplayManager displayManager;
    private InstrumentProjector instrumentProjector;
    private InstrumentProjector2 instrumentProjector2;

    private final Map<Integer, BiConsumer<android.content.Context, Display>> projectorCreators = new HashMap<>();

    public static synchronized ProjectorManager getInstance() {
        if (instance == null) {
            instance = new ProjectorManager();
        }
        return instance;
    }

    private ProjectorManager() {
        sharedPreferences = App.getDeviceProtectedContext().getSharedPreferences("haval_prefs", Context.MODE_PRIVATE);

        projectorCreators.put(1, (ctx, disp) -> {
            instrumentProjector2 = new InstrumentProjector2(ctx, disp);
            instrumentProjector2.show();
            Log.w(TAG, "InstrumentProjector2 initialized and displayed successfully");
        });

        projectorCreators.put(3, (ctx, disp) -> {
            instrumentProjector = new InstrumentProjector(ctx, disp);
            instrumentProjector.show();
            Log.w(TAG, "HudProjector initialized and displayed successfully");
        });
    }

    public void initialize() {
        Log.w(TAG, "Initializing ProjectorManager");
        try {
            displayManager = App.getContext().getSystemService(DisplayManager.class);

            for (Display display : displayManager.getDisplays()) {
                Log.w(TAG, "Display found: " + display.getName() + " (ID: " + display.getDisplayId() + ")");
            }

            Set<Integer> pending = new HashSet<>(projectorCreators.keySet());

            for (Integer id : projectorCreators.keySet()) {
                Display display = getDisplayById(id);
                if (display != null) {
                    projectorCreators.get(id).accept(App.getContext(), display);
                    pending.remove(id);
                }
            }

            if (!pending.isEmpty()) {
                registerDisplayListener(pending);
            }

            ServiceManager.getInstance().addDataChangedListener((key, value) -> {
                if (key.equals(CarConstants.CAR_BASIC_ENGINE_STATE.getValue())) {
                    if (value.equals("-1") || value.equals("15") || value.equals("14") || value.equals("10")) {
                        if (instrumentProjector != null) {
                            instrumentProjector.carMainScreenOff();
                        }
                        if (instrumentProjector2 != null) {
                            instrumentProjector2.carMainScreenOff();
                        }
                    } else {
                        if (instrumentProjector != null) {
                            instrumentProjector.carMainScreenOn();
                        }
                        if (instrumentProjector2 != null) {
                            instrumentProjector2.carMainScreenOn();
                        }
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize ProjectorManager", e);
        }
    }

    private Display getDisplayById(int id) {
        for (Display display : displayManager.getDisplays()) {
            if (display.getDisplayId() == id) {
                return display;
            }
        }
        return null;
    }

    private void registerDisplayListener(Set<Integer> pending) {
        DisplayManager.DisplayListener listener = new DisplayManager.DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {
                Log.w(TAG, "Display added: " + displayId);
                if (pending.contains(displayId)) {
                    Display display = displayManager.getDisplay(displayId);
                    if (display != null) {
                        projectorCreators.get(displayId).accept(App.getContext(), display);
                        pending.remove(displayId);
                        if (pending.isEmpty()) {
                            displayManager.unregisterDisplayListener(this);
                        }
                    }
                }
            }

            @Override
            public void onDisplayRemoved(int displayId) {
                // Handle if needed
                Log.w(TAG, "Display removed: " + displayId);
            }

            @Override
            public void onDisplayChanged(int displayId) {
                // Handle if needed
                Log.w(TAG, "Display changed: " + displayId);
            }
        };
        displayManager.registerDisplayListener(listener, new Handler(Looper.getMainLooper()));
        Log.w(TAG, "Registered listener for missing displays: " + pending);
    }
}