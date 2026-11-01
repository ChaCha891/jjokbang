package com.example.contactpause;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 모든 잠금은 '현재 시각이 시작~종료 사이인지'를 읽어서 판단한다.
 * SharedPreferences를 사용하므로 서버, Supabase, 인터넷 연결이 필요 없다.
 * 참고: 일반 APK이므로 사용자가 시스템 시각/앱 권한을 바꾸면 우회할 수 있다.
 */
public final class LockStore {
    private static final String FILE = "contact_pause_v1";
    private static final String MANUAL_START = "manual_start";
    private static final String MANUAL_END = "manual_end";
    private static final String RECENTS = "recent_minutes";
    private static final String SCHEDULES = "schedules";
    private static final String NEXT_ID = "next_id";
    public static final long MAX_DURATION = 7L * 24L * 60L * 60L * 1000L;

    private LockStore() { }

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static final class Entry {
        public final int id;
        public final long start;
        public final long end;

        public Entry(int id, long start, long end) {
            this.id = id;
            this.start = start;
            this.end = end;
        }
    }

    public static synchronized long activeUntil(Context c) {
        long now = System.currentTimeMillis();
        SharedPreferences pref = p(c);
        long result = 0;
        long manualStart = pref.getLong(MANUAL_START, 0);
        long manualEnd = pref.getLong(MANUAL_END, 0);
        if (now >= manualStart && now < manualEnd) result = manualEnd;

        for (Entry entry : schedules(c)) {
            if (now >= entry.start && now < entry.end) {
                result = Math.max(result, entry.end);
            }
        }
        return result;
    }

    public static boolean isLocked(Context c) {
        return activeUntil(c) > System.currentTimeMillis();
    }

    public static synchronized boolean startManual(Context c, int minutes) {
        if (minutes < 1 || minutes > 10080 || isLocked(c)) return false;
        long now = System.currentTimeMillis();
        long end = now + minutes * 60_000L;
        if (!p(c).edit().putLong(MANUAL_START, now).putLong(MANUAL_END, end).commit()) return false;
        addRecent(c, minutes);
        return true;
    }

    public static synchronized long manualEnd(Context c) {
        return p(c).getLong(MANUAL_END, 0);
    }

    public static synchronized List<Integer> recents(Context c) {
        ArrayList<Integer> values = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(p(c).getString(RECENTS, "[]"));
            for (int i = 0; i < array.length(); i++) {
                int val = array.optInt(i);
                if (val > 0 && val <= 10080 && !values.contains(val)) values.add(val);
            }
        } catch (Exception ignored) { }
        return values;
    }

    private static void addRecent(Context c, int minutes) {
        List<Integer> values = recents(c);
        values.remove(Integer.valueOf(minutes));
        values.add(0, minutes);
        JSONArray array = new JSONArray();
        for (int i = 0; i < Math.min(5, values.size()); i++) array.put(values.get(i));
        p(c).edit().putString(RECENTS, array.toString()).commit();
    }

    public static synchronized List<Entry> schedules(Context c) {
        ArrayList<Entry> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(p(c).getString(SCHEDULES, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj == null) continue;
                long start = obj.optLong("start", 0);
                long end = obj.optLong("end", 0);
                int id = obj.optInt("id", 0);
                if (id >= 100 && start > 0 && end > start) result.add(new Entry(id, start, end));
            }
        } catch (Exception ignored) { }
        Collections.sort(result, Comparator.comparingLong(e -> e.start));
        return result;
    }

    public static String weekKey(long time) {
        LocalDate day = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate();
        WeekFields iso = WeekFields.ISO;
        return day.get(iso.weekBasedYear()) + "-" + day.get(iso.weekOfWeekBasedYear());
    }

    public static synchronized int weekCount(Context c, long startTime) {
        String wanted = weekKey(startTime);
        int count = 0;
        for (Entry entry : schedules(c)) {
            if (wanted.equals(weekKey(entry.start))) count++;
        }
        return count;
    }

    /** 주당 3회: 시작 날짜가 속한 ISO 주(월요일~일요일) 기준. */
    public static synchronized Entry addSchedule(Context c, long start, long end) {
        long now = System.currentTimeMillis();
        if (start <= now || end <= start || end - start > MAX_DURATION) return null;
        List<Entry> values = schedules(c);
        if (weekCount(c, start) >= 3) return null;

        // 두 예약이 겹치면 어느 예약이 끝나야 풀릴지 혼동되므로 사전 방지한다.
        for (Entry entry : values) {
            if (start < entry.end && entry.start < end) return null;
        }
        int id = Math.max(100, p(c).getInt(NEXT_ID, 100));
        Entry newEntry = new Entry(id, start, end);
        values.add(newEntry);
        if (!persistSchedules(c, values, id + 1)) return null;
        return newEntry;
    }

    public static synchronized boolean cancelUpcoming(Context c, int id) {
        List<Entry> entries = schedules(c);
        long now = System.currentTimeMillis();
        Entry target = null;
        for (Entry e : entries) if (e.id == id) target = e;
        if (target == null || target.start <= now) return false;
        entries.remove(target);
        return persistSchedules(c, entries, p(c).getInt(NEXT_ID, 100));
    }

    private static boolean persistSchedules(Context c, List<Entry> entries, int nextId) {
        JSONArray array = new JSONArray();
        for (Entry entry : entries) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("id", entry.id);
                obj.put("start", entry.start);
                obj.put("end", entry.end);
                array.put(obj);
            } catch (Exception ignored) { }
        }
        return p(c).edit().putString(SCHEDULES, array.toString())
                .putInt(NEXT_ID, nextId).commit();
    }

    public static String durationText(int minutes) {
        int h = minutes / 60;
        int m = minutes % 60;
        if (h == 0) return m + "분";
        if (m == 0) return h + "시간";
        return h + "시간 " + m + "분";
    }
}
