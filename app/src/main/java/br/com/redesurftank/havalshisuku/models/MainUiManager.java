package br.com.redesurftank.havalshisuku.models;

import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;

import br.com.redesurftank.havalshisuku.managers.ServiceManager;
import br.com.redesurftank.havalshisuku.models.screens.AcControlScreen;
import br.com.redesurftank.havalshisuku.models.screens.MainMenu;
import br.com.redesurftank.havalshisuku.models.screens.Screen;

public class MainUiManager {
    private static final String TAG = "MainUiManager";

    // These fields are not declared in the original file. I'm declaring them here to make the code compile.
    private SharedPreferences sharedPreferences;

    private static volatile MainUiManager INSTANCE;

    // Estado atual da tela (MainMenu ou um sub-menu)
    private Screen currentScreenCard1;
    private Screen currentScreenCard3;

    private int currentCard = 1;
    private static final int MAIN_MENU_CARD_ID = 1;
    private static final int AC_CARD_ID = 3;
    private static final int HIDDEN_CARD_ID = 0;
    private static final long HIDDEN_CARD_BOUNCE_WINDOW_MS = 1500L;
    private long lastMainMenuInputAtMs = 0L;


    private MainUiManager() {
        MainMenu initialMenu = new MainMenu();
        initialMenu.initialize();
        Screen acScreen = new AcControlScreen();
        acScreen.initialize();
        this.currentScreenCard1 = initialMenu.setInitialScreen(this);
        this.currentScreenCard3 = acScreen;
        this.sharedPreferences = ServiceManager.getInstance().getSharedPreferences();
    }

    public void updateScreen() {
        if (this.currentCard == MAIN_MENU_CARD_ID) {
            this.currentScreenCard1.initialize();
            if (sharedPreferences != null)
                sharedPreferences.edit().putString(SharedPreferencesKeys.LAST_CLUSTER_SCREEN.getKey(), this.currentScreenCard1.getJsName()).apply();
        } else if (this.currentCard == AC_CARD_ID) {
            this.currentScreenCard3.initialize();
        }
    }

    public void updateScreen(Screen newScreen) {
        newScreen.initialize();
        if (this.currentCard == MAIN_MENU_CARD_ID) {
            this.currentScreenCard1 = newScreen;
            if (sharedPreferences != null)
                sharedPreferences.edit().putString(SharedPreferencesKeys.LAST_CLUSTER_SCREEN.getKey(), this.currentScreenCard1.getJsName()).apply();
        } else if (this.currentCard == AC_CARD_ID) {
            this.currentScreenCard3 = newScreen;
        }
    }

    public static MainUiManager getInstance() {
        if (INSTANCE == null) {
            synchronized (MainUiManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new MainUiManager();
                }
            }
        }
        return INSTANCE;
    }

    public void handleCardChange(int cardID) {
        if (cardID == HIDDEN_CARD_ID && shouldKeepMainMenuSession()) {
            Log.w(TAG, "Ignoring transient hidden card during Mainmenu session: " + this.currentScreenCard1.getJsName());
            return;
        }
        this.currentCard = cardID;
        updateScreen();
    }

    public void handleGeneralKeyEvents(Screen.Key key) {
        if (this.currentCard == HIDDEN_CARD_ID && isMainMenuSessionScreen(this.currentScreenCard1)) {
            Log.w(TAG, "Recovering Mainmenu session input while cluster card is hidden: " + this.currentScreenCard1.getJsName());
            this.currentCard = MAIN_MENU_CARD_ID;
        }

        if (this.currentCard == MAIN_MENU_CARD_ID) {
            this.lastMainMenuInputAtMs = SystemClock.uptimeMillis();
            this.currentScreenCard1.processKey(key);
        }
        else if (this.currentCard == AC_CARD_ID) this.currentScreenCard3.processKey(key);
    }

    private boolean shouldKeepMainMenuSession() {
        return this.currentCard == MAIN_MENU_CARD_ID &&
                isMainMenuSessionScreen(this.currentScreenCard1) &&
                SystemClock.uptimeMillis() - this.lastMainMenuInputAtMs <= HIDDEN_CARD_BOUNCE_WINDOW_MS;
    }

    private boolean isMainMenuSessionScreen(Screen screen) {
        if (screen == null) return false;
        String jsName = screen.getJsName();
        return "main_menu".equals(jsName) || "graph".equals(jsName) || "regen".equals(jsName) || "display_selection".equals(jsName);
    }

}
