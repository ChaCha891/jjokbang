package com.example.contactpause;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** 예약 시작/종료 알람. 잠금의 최종 판단은 LockStore에서 한다. */
public final class AlarmPlanner {
    private AlarmPlanner() { }

    private static PendingIntent pending(Context c, int code) {
        Intent intent = new Intent(c, AlarmReceiver.class).setAction("lock-event-" + code);
        return PendingIntent.getBroadcast(c, code, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static boolean exactAvailable(Context c) {
        AlarmManager alarm = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        return alarm != null && (Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms());
    }

    private static void plan(Context c, int code, long when) {
        AlarmManager alarm = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (alarm == null || when <= System.currentTimeMillis()) return;
        try {
            if (exactAvailable(c)) {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending(c, code));
            } else {
                // 정시 알람 특별권한이 없을 경우 늦게 실행될 수 있음.
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending(c, code));
            }
        } catch (SecurityException denied) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending(c, code));
        }
    }

    public static void cancelSchedule(Context c, int id) {
        AlarmManager alarm = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (alarm == null) return;
        alarm.cancel(pending(c, id * 2));
        alarm.cancel(pending(c, id * 2 + 1));
    }

    public static void reschedule(Context c) {
        plan(c, 1, LockStore.manualEnd(c));
        for (LockStore.Entry e : LockStore.schedules(c)) {
            plan(c, e.id * 2, e.start);
            plan(c, e.id * 2 + 1, e.end);
        }
    }
}
