package com.example.dochelp.utils;

import com.example.dochelp.R;

import android.app.Activity;
import android.app.ProgressDialog;
import android.view.View;

public final class LoadingUtils {
    private static ProgressDialog dialog;

    private LoadingUtils() {
    }

    public static void show(Activity activity, String message) {
        if (activity == null || activity.isFinishing()) return;
        hide();
        dialog = new ProgressDialog(activity);
        dialog.setCancelable(false);
        dialog.setMessage(message == null ? "Please wait..." : message);
        dialog.show();
    }

    public static void hide() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
        dialog = null;
    }

    public static void setButtonLoading(View button, boolean loading) {
        if (button != null) {
            button.setEnabled(!loading);
        }
    }
}
