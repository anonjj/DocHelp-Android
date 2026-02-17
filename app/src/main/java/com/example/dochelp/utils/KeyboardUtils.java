package com.example.dochelp.utils;

import com.example.dochelp.R;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

public final class KeyboardUtils {
    private KeyboardUtils() {
    }

    public static void hideKeyboard(Activity activity) {
        if (activity == null) return;
        View view = activity.getCurrentFocus();
        if (view == null) view = new View(activity);
        InputMethodManager imm =
                (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
}
