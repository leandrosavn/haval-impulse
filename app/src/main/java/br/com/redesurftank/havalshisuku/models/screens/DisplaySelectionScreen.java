package br.com.redesurftank.havalshisuku.models.screens;

import android.util.Log;

import br.com.redesurftank.havalshisuku.managers.ServiceManager;
import br.com.redesurftank.havalshisuku.models.MainUiManager;
import br.com.redesurftank.havalshisuku.models.ServiceManagerEventType;
import br.com.redesurftank.havalshisuku.models.SharedPreferencesKeys;

public class DisplaySelectionScreen implements Screen {

    private static final String TAG = "DisplaySelectionScreen";

    private ServiceManager serviceManager;
    private Screen previousScreen = this;
    
    // The items mirror the frontend displaySelection.js
    private static final String[] ITEM_IDS = {
        "sel_template",
        "mode_normal",
        "mode_reduzido",
        "mode_clean"
    };
    private int focusedIndex = 0;

    private static final String[] TEMPLATES = {"Normal"};
    private static final String[] DISPLAYS = {"Normal", "Reduzido", "Clean"};

    @Override
    public String getJsName() {
        return "display_selection";
    }

    @Override
    public void processKey(Key key) {
        switch (key) {
            case DOWN:
                focusedIndex++;
                if (focusedIndex >= ITEM_IDS.length) {
                    focusedIndex = 0;
                }
                updateFocus();
                break;
            case UP:
                focusedIndex--;
                if (focusedIndex < 0) {
                    focusedIndex = ITEM_IDS.length - 1;
                }
                updateFocus();
                break;
            case ENTER:
                handleSelection();
                break;
            case BACK:
            case BACK_LONG:
                // Check if display is Reduzido, user wants Backspace to reset to Normal? The frontend handles this, but let's mirror it or let user manually change.
                MainUiManager.getInstance().updateScreen(previousScreen);
                break;
        }
    }

    private void updateFocus() {
        serviceManager.dispatchServiceManagerEvent(ServiceManagerEventType.MENU_ITEM_NAVIGATION, ITEM_IDS[focusedIndex]);
    }

    private void handleSelection() {
        String selectedId = ITEM_IDS[focusedIndex];
        if (selectedId.equals("sel_template")) {
            String current = serviceManager.getSharedPreferences().getString(SharedPreferencesKeys.CURRENT_CLUSTER_TEMPLATE.getKey(), "Normal");
            int idx = 0;
            for (int i = 0; i < TEMPLATES.length; i++) {
                if (TEMPLATES[i].equals(current)) idx = i;
            }
            String next = TEMPLATES[(idx + 1) % TEMPLATES.length];
            serviceManager.getSharedPreferences().edit().putString(SharedPreferencesKeys.CURRENT_CLUSTER_TEMPLATE.getKey(), next).apply();
            // We use the same generic mechanism or send to projectort2
            serviceManager.dispatchServiceManagerEvent(ServiceManagerEventType.DISPLAY_SCREEN_SELECTION, "control('template', '" + next + "')");
        } else if (selectedId.startsWith("mode_")) {
            String newDisplay = "Normal";
            if (selectedId.equals("mode_normal")) newDisplay = "Normal";
            else if (selectedId.equals("mode_reduzido")) newDisplay = "Reduzido";
            else if (selectedId.equals("mode_clean")) newDisplay = "Clean";
            
            serviceManager.getSharedPreferences().edit().putString(SharedPreferencesKeys.CURRENT_CLUSTER_DISPLAY.getKey(), newDisplay).apply();
            serviceManager.dispatchServiceManagerEvent(ServiceManagerEventType.DISPLAY_SCREEN_SELECTION, "control('display', '" + newDisplay + "')");
        }
        
    }

    @Override
    public void initialize() {
        this.serviceManager = ServiceManager.getInstance();
        focusedIndex = 0;
        serviceManager.dispatchServiceManagerEvent(ServiceManagerEventType.UPDATE_SCREEN, this);
        updateFocus();
    }

    @Override
    public void setReturnScreen(Screen previousScreen) {
        this.previousScreen = previousScreen;
    }
}
