package com.example.contactpause;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 실행 중인 앱의 패키지만 확인하여 차단 대상이면 홈으로 돌아감.
 * 채팅·DM·문자 내용과 입력 텍스트는 읽거나 기록하지 않음.
 * 전화 다이얼러는 차단하지 않음(긴급전화 접근을 유지하기 위함).
 */
public class GuardAccessibilityService extends AccessibilityService {
    private static final Set<String> BLOCKED = new HashSet<>(Arrays.asList(
            "com.kakao.talk",                       // 카카오톡
            "com.instagram.android",                 // 인스타그램
            "com.samsung.android.messaging",        // 삼성 메시지
            "com.google.android.apps.messaging",    // 구글 메시지
            "com.android.mms",                      // AOSP 메시지
            "com.android.messaging"
    ));

    private final Handler handler = new Handler(Looper.getMainLooper());
    private String lastPackage = "";
    private long lastToast = 0;
    private boolean running = false;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            if (LockStore.isLocked(GuardAccessibilityService.this)) {
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root != null) {
                    try {
                        if (root.getPackageName() != null) {
                            checkPackage(root.getPackageName().toString());
                        }
                    } finally {
                        root.recycle();
                    }
                } else {
                    checkPackage(lastPackage);
                }
            }
            handler.postDelayed(this, 1500L);
        }
    };

    @Override public void onServiceConnected() {
        super.onServiceConnected();
        running = true;
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        lastPackage = event.getPackageName().toString();
        checkPackage(lastPackage);
    }

    private void checkPackage(String pkg) {
        if (!BLOCKED.contains(pkg) || !LockStore.isLocked(this)) return;
        performGlobalAction(GLOBAL_ACTION_HOME);
        long now = System.currentTimeMillis();
        if (now - lastToast > 3500L) {
            lastToast = now;
            Toast.makeText(this, "쪽방 잠금 중입니다. 종료 시간까지 앱을 열 수 없어요.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override public void onInterrupt() { }
    @Override public void onDestroy() {
        running = false;
        handler.removeCallbacks(tick);
        super.onDestroy();
    }
}
