package org.love2d.android;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textview.MaterialTextView;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;

/**
 * آماده‌سازی و اجرای مرحله انتخاب‌شده:
 * 1) نوشتن اتمیک chooser.txt در پوشه قراردادی save/archive (مشترک با Lua)
 * 2) فقط در صورت موفقیت: اجرای GameActivity
 * 3) در صورت شکست: دیالوگ فارسی با «تلاش دوباره»؛ بازی هرگز با chooser ناقص اجرا نمی‌شود
 *
 * فیکس کرش P0: نوع loading_bar با dialog_loading.xml هماهنگ شد
 * (CircularProgressIndicator به‌جای LinearProgressIndicator → ClassCastException قبلی)
 */
public class gotoLevel extends AsyncTask<Void, Void, Boolean> {

    private static final String TAG = "gotoLevel";

    /** task فعال فعلی — مهار دوبار لمس سریع و جلوگیری از چند task موازی */
    private static WeakReference<gotoLevel> activeTask;

    private final Activity activity;
    private final String episodeId;
    private androidx.appcompat.app.AlertDialog progressDialog;

    public gotoLevel(Activity activity, String level) {
        this.activity = activity;
        // فقط شناسه‌های معتبر عبور می‌کنند؛ هر چیز دیگری → آموزش (رفتار امن)
        this.episodeId = SavePaths.normalizeEpisodeId(level);
    }

    /** نقطه ورود امن برای منوها؛ درخواست تکراری در حالت در‌حال‌آماده‌سازی نادیده گرفته می‌شود */
    public static void launch(Activity activity, String episodeId) {
        gotoLevel running = activeTask == null ? null : activeTask.get();
        if (running != null && running.getStatus() != Status.FINISHED) {
            Toast.makeText(activity, R.string.level_already_preparing, Toast.LENGTH_SHORT).show();
            return;
        }
        new gotoLevel(activity, episodeId).execute();
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        activeTask = new WeakReference<>(this);
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        View view = View.inflate(activity, R.layout.dialog_loading, null);
        CircularProgressIndicator bar = view.findViewById(R.id.loading_bar); // هم‌نوع با XML
        bar.setIndeterminate(true);
        MaterialTextView text = view.findViewById(R.id.loading_text);
        text.setText(R.string.preparing_level);

        progressDialog = new MaterialAlertDialogBuilder(activity)
                .setView(view)
                .setCancelable(false)
                .create();
        progressDialog.show();
        Window w = progressDialog.getWindow();
        if (w != null) {
            w.setLayout(Window.LayoutParams.WRAP_CONTENT, Window.LayoutParams.WRAP_CONTENT);
        }
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        try {
            File base = new File(SplashActivity.baseDir(activity));
            SavePaths.writeChooser(base, episodeId);
            return Boolean.TRUE;
        } catch (IOException e) {
            Log.e(TAG, "writing chooser.txt failed for episode " + episodeId, e);
            return Boolean.FALSE;
        }
    }

    @Override
    protected void onPostExecute(Boolean success) {
        dismissDialogSafely();

        if (!Boolean.TRUE.equals(success)) {
            if (activity.isFinishing() || activity.isDestroyed()) {
                return; // Activity مرده؛ فقط لاگ کافی است
            }
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.level_start_failed_title)
                    .setMessage(R.string.level_start_failed_message)
                    .setCancelable(false)
                    .setPositiveButton(R.string.retry, (d, which) -> launch(activity, episodeId))
                    .setNegativeButton(R.string.back, (d, which) -> d.dismiss())
                    .show();
            return;
        }

        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        Intent intent = new Intent(activity, GameActivity.class);
        activity.startActivity(intent);
        activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    protected void onCancelled(Boolean aBoolean) {
        super.onCancelled(aBoolean);
        dismissDialogSafely();
    }

    private void dismissDialogSafely() {
        if (progressDialog != null) {
            try {
                if (progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
            } catch (Exception ignored) {
                // پنجره ممکن است از قبل بسته شده باشد
            }
            progressDialog = null;
        }
    }
}
