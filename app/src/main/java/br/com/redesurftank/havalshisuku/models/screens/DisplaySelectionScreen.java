package br.com.redesurftank.havalshisuku.models.screens;

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
    private int focusedTemplateIndex = 0;
    private int focusedDisplayIndex = 0;

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
                focusedTemplateIndex++;
                if (focusedTemplateIndex >= ITEM_IDS.length) {
                    focusedTemplateIndex = 0;
                }
                updateFocus();
                handleSelection(key);
                break;
            case UP:
                focusedTemplateIndex--;
                if (focusedTemplateIndex < 0) {
                    focusedTemplateIndex = ITEM_IDS.length - 1;
                }
                updateFocus();
                handleSelection(key);
                break;
            case BACK:
            case BACK_LONG:
                handleSelection(key);
                MainUiManager.getInstance().updateScreen(previousScreen);
                break;
            default:
                handleSelection(key);
                break;
        }
    }

    private void updateFocus() {
        serviceManager.dispatchServiceManagerEvent(ServiceManagerEventType.MENU_ITEM_NAVIGATION, ITEM_IDS[focusedTemplateIndex]);
    }

    private void handleSelection(Key key) {
        String selectedId = ITEM_IDS[focusedTemplateIndex];
        if (key == Key.ENTER) {
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
                switch (selectedId) {
                    case "mode_normal":
                        focusedDisplayIndex = 0;
                        break;
                    case "mode_reduzido":
                        focusedDisplayIndex = 1;
                        break;
                    case "mode_clean":
                        if (focusedDisplayIndex == 2) {
                            focusedDisplayIndex = 0;
                        } else {
                            focusedDisplayIndex = 2;
                        }
                        break;
                }

                //serviceManager.getSharedPreferences().edit().putString(SharedPreferencesKeys.CURRENT_CLUSTER_DISPLAY.getKey(), newDisplay).apply();
                serviceManager.dispatchServiceManagerEvent(ServiceManagerEventType.DISPLAY_SCREEN_SELECTION, "control('display', '" + DISPLAYS[focusedDisplayIndex] + "')");
            }
        } else {
            if (focusedDisplayIndex == 2) {
                focusedDisplayIndex = 0;
                serviceManager.dispatchServiceManagerEvent(ServiceManagerEventType.DISPLAY_SCREEN_SELECTION, "control('display', '" + DISPLAYS[focusedDisplayIndex] + "')");

            }
        }
    }

    @Override
    public void initialize() {
        this.serviceManager = ServiceManager.getInstance();
        focusedTemplateIndex = 0;
        serviceManager.dispatchServiceManagerEvent(ServiceManagerEventType.UPDATE_SCREEN, this);
        updateFocus();
    }

    @Override
    public void setReturnScreen(Screen previousScreen) {
        this.previousScreen = previousScreen;
    }
}
