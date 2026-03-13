package br.com.redesurftank.havalshisuku.services;

import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import br.com.redesurftank.havalshisuku.managers.DisplayAppLauncher;

public class AccessibilityService extends android.accessibilityservice.AccessibilityService {

    private static final String TAG = "AccessibilityService";

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            CharSequence pkg = event.getPackageName();
            if (pkg != null) {
                DisplayAppLauncher.INSTANCE.onAppWindowChanged(pkg.toString());
            }
        }
    }

    @Override
    public void onInterrupt() {

    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        Log.w(TAG, "onKeyEvent: " + event.getKeyCode());
        return false;
    }
}
