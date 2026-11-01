package com.example.contactpause;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 재부팅/시간대 변경/정확한 알람 권한 재허용 후 다음 알람을 다시 건다. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (action == null || !action.startsWith("lock-event-")) {
            AlarmPlanner.reschedule(context);
        }
        // 타이머 시작·종료는 벽시계 기반 LockStore에서 즉시 반영된다.
        // 접근성 서비스는 1.5초마다 전면 앱을 재검사한다.
    }
}
