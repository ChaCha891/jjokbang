package com.example.contactpause;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Shows an accessibility overlay when a blocked messaging/social app is foregrounded.
 * Only package names are inspected; no message text, contact data, or input is collected.
 * The phone dialer is deliberately excluded so emergency calls remain accessible.
 */
public class GuardAccessibilityService extends AccessibilityService {
    private static final Map<String, String> BLOCKED;
    static {
        Map<String, String> apps = new HashMap<>();
        apps.put("com.kakao.talk", "카카오톡");
        apps.put("com.instagram.android", "인스타그램");
        apps.put("com.samsung.android.messaging", "문자 메시지");
        apps.put("com.google.android.apps.messaging", "문자 메시지");
        apps.put("com.android.mms", "문자 메시지");
        apps.put("com.android.messaging", "문자 메시지");
        BLOCKED = Collections.unmodifiableMap(apps);
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private LinearLayout overlay;
    private TextView titleView;
    private TextView timerView;
    private String currentPackage = "";
    private boolean running = false;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            long until = LockStore.activeUntil(GuardAccessibilityService.this);
            if (until <= System.currentTimeMillis()) {
                hideOverlay();
            } else {
                // Check the active app as well as accessibility events to handle app switching.
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root != null) {
                    try {
                        if (root.getPackageName() != null) {
                            updateForPackage(root.getPackageName().toString());
                        }
                    } finally {
                        root.recycle();
                    }
                }
                if (overlay != null) updateCountdown(until);
            }
            handler.postDelayed(this, 1000L);
        }
    };

    @Override public void onServiceConnected() {
        super.onServiceConnected();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        running = true;
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return;
        updateForPackage(event.getPackageName().toString());
    }

    private void updateForPackage(String pkg) {
        // Ignore our own overlay/window events; they must not dismiss the guard.
        if (getPackageName().equals(pkg)) return;
        currentPackage = pkg;
        long until = LockStore.activeUntil(this);
        if (until <= System.currentTimeMillis() || !BLOCKED.containsKey(pkg)) {
            hideOverlay();
            return;
        }
        showOverlay(BLOCKED.get(pkg), until);
    }

    private int dp(float value) {
        return (int) (getResources().getDisplayMetrics().density * value + 0.5f);
    }

    private TextView label(String text, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private void showOverlay(String appName, long until) {
        if (overlay == null) {
            LinearLayout screen = new LinearLayout(this);
            screen.setOrientation(LinearLayout.VERTICAL);
            screen.setGravity(Gravity.CENTER);
            screen.setPadding(dp(24), dp(32), dp(24), dp(32));
            screen.setBackgroundColor(Color.rgb(19, 24, 40));

            TextView icon = label("🔒", 55, Color.WHITE, false);
            screen.addView(icon);

            TextView heading = label("쪽방", 34, Color.WHITE, true);
            LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(-1, -2);
            headingParams.topMargin = dp(14);
            screen.addView(heading, headingParams);

            titleView = label("", 21, Color.WHITE, true);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
            titleParams.topMargin = dp(34);
            screen.addView(titleView, titleParams);

            TextView description = label("쪽방 앱에 의해 사용이 제한되었습니다.", 15,
                    Color.rgb(194, 201, 218), false);
            LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(-1, -2);
            descriptionParams.topMargin = dp(12);
            screen.addView(description, descriptionParams);

            TextView remainingLabel = label("⏳  남은 잠금 시간", 16,
                    Color.rgb(194, 201, 218), false);
            LinearLayout.LayoutParams remainingParams = new LinearLayout.LayoutParams(-1, -2);
            remainingParams.topMargin = dp(48);
            screen.addView(remainingLabel, remainingParams);

            timerView = label("00:00:00", 43, Color.rgb(255, 211, 115), true);
            LinearLayout.LayoutParams timerParams = new LinearLayout.LayoutParams(-1, -2);
            timerParams.topMargin = dp(12);
            screen.addView(timerView, timerParams);

            TextView slogan = label("술 마셨으면 쪽방에서 곱게 자라! 💤", 14,
                    Color.rgb(194, 201, 218), false);
            LinearLayout.LayoutParams sloganParams = new LinearLayout.LayoutParams(-1, -2);
            sloganParams.topMargin = dp(38);
            screen.addView(slogan, sloganParams);

            Button home = new Button(this);
            home.setText("홈으로 돌아가기");
            home.setTextSize(16);
            home.setAllCaps(false);
            home.setTextColor(Color.rgb(19, 24, 40));
            GradientDrawable buttonBg = new GradientDrawable();
            buttonBg.setColor(Color.rgb(255, 211, 115));
            buttonBg.setCornerRadius(dp(14));
            home.setBackground(buttonBg);
            home.setOnClickListener(v -> {
                hideOverlay();
                performGlobalAction(GLOBAL_ACTION_HOME);
            });
            LinearLayout.LayoutParams homeParams = new LinearLayout.LayoutParams(-1, dp(54));
            homeParams.topMargin = dp(44);
            screen.addView(home, homeParams);

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    android.graphics.PixelFormat.OPAQUE);
            params.gravity = Gravity.TOP | Gravity.START;
            try {
                windowManager.addView(screen, params);
                overlay = screen;
            } catch (RuntimeException e) {
                // Avoid crashing the accessibility service if the window token is unavailable.
                overlay = null;
                return;
            }
        }
        titleView.setText(appName + "은(는) 현재 차단된 앱입니다.");
        updateCountdown(until);
    }

    private void updateCountdown(long until) {
        if (timerView == null) return;
        long seconds = Math.max(0, (until - System.currentTimeMillis() + 999) / 1000);
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainingSeconds = seconds % 60;
        timerView.setText(String.format(java.util.Locale.KOREA, "%02d:%02d:%02d",
                hours, minutes, remainingSeconds));
    }

    private void hideOverlay() {
        if (overlay == null) return;
        try {
            windowManager.removeViewImmediate(overlay);
        } catch (RuntimeException ignored) { }
        overlay = null;
        titleView = null;
        timerView = null;
    }

    @Override public void onInterrupt() { }

    @Override public void onDestroy() {
        running = false;
        handler.removeCallbacks(tick);
        hideOverlay();
        super.onDestroy();
    }
}
