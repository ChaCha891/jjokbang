package com.example.contactpause;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 연락 잠금 0.1 - 순수 Java/Android SDK (외부 라이브러리·서버 없음)
 * 수동 잠금 + 5개 최근 사용 + ISO 주당 3개 일회성 예약.
 */
public class MainActivity extends Activity {
    private static final int DARK = Color.rgb(29, 37, 54);
    private static final int PURPLE = Color.rgb(87, 74, 188);
    private static final int GRAY = Color.rgb(98, 108, 126);
    private static final int BG = Color.rgb(246, 247, 252);
    private static final int RED = Color.rgb(172, 57, 68);

    private LinearLayout screen;
    private TextView liveStatus;
    private TextView capabilities;
    private EditText hoursInput;
    private EditText minutesInput;
    private long chosenStart;
    private long chosenEnd;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            updateLiveStatus();
            handler.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(DARK);
        long start = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1);
        chosenStart = start;
        chosenEnd = start + TimeUnit.HOURS.toMillis(7);
        buildUi();
        AlarmPlanner.reschedule(this);
    }

    @Override public void onResume() {
        super.onResume();
        buildUi();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override public void onPause() {
        handler.removeCallbacks(ticker);
        super.onPause();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setPadding(dp(18), dp(24), dp(18), dp(35));
        scroll.addView(screen);
        setContentView(scroll);

        TextView title = text("쪽방", 28, DARK, true);
        screen.addView(title);
        TextView sub = text("연락하고 싶은 순간을 잠시 넘기는 타이머", 14, GRAY, false);
        sub.setPadding(0, dp(4), 0, dp(18));
        screen.addView(sub);

        LinearLayout statusCard = card(screen);
        liveStatus = text("", 18, DARK, true);
        statusCard.addView(liveStatus);
        TextView about = text("잠금 중에는 앱에서 중간 해제하거나 시간을 줄일 수 없습니다.\n휴대폰 시스템 설정에서 권한을 끄는 것까지 방지할 수는 없습니다.", 13, GRAY, false);
        about.setPadding(0, dp(8), 0, 0);
        statusCard.addView(about);

        LinearLayout permCard = card(screen);
        permCard.addView(text("필수 권한", 18, DARK, true));
        capabilities = text("", 13, GRAY, false);
        capabilities.setPadding(0, dp(8), 0, dp(8));
        permCard.addView(capabilities);
        Button accessibility = button("문자 · 카톡 · 인스타 앱 차단 권한 설정", false);
        permCard.addView(accessibility);
        accessibility.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        Button callRole = button("일반 전화 발신 차단 권한 설정", false);
        permCard.addView(callRole);
        callRole.setOnClickListener(v -> requestCallRole());
        Button exactAlarm = button("예약 시간 정확히 실행하기 권한 설정", false);
        permCard.addView(exactAlarm);
        exactAlarm.setOnClickListener(v -> requestExactAlarm());

        LinearLayout manual = card(screen);
        manual.addView(text("지금 바로 잠그기", 18, DARK, true));
        manual.addView(note("전화 발신 · 문자 · 카카오톡 · 인스타그램 동시 제한"));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        hoursInput = numberInput("시간", "7");
        minutesInput = numberInput("분", "0");
        row.addView(hoursInput, new LinearLayout.LayoutParams(0, dp(52), 1));
        TextView hUnit = text("시간", 14, GRAY, false);
        hUnit.setPadding(dp(6), 0, dp(12), 0);
        row.addView(hUnit);
        row.addView(minutesInput, new LinearLayout.LayoutParams(0, dp(52), 1));
        TextView mUnit = text("분", 14, GRAY, false);
        mUnit.setPadding(dp(6), 0, 0, 0);
        row.addView(mUnit);
        manual.addView(row);
        Button startNow = button("잠금 시작", true);
        manual.addView(startNow);
        startNow.setOnClickListener(v -> {
            int hours = parse(hoursInput);
            int minutes = parse(minutesInput);
            if (hours < 0 || minutes < 0 || minutes > 59) {
                alert("입력 확인", "시간과 분을 올바르게 입력해 줘.");
                return;
            }
            long total = hours * 60L + minutes;
            if (total < 1 || total > 10080L) {
                alert("시간 범위", "최소 1분부터 최대 168시간(7일)까지 설정할 수 있어.");
                return;
            }
            startFor((int) total);
        });

        LinearLayout recentCard = card(screen);
        recentCard.addView(text("최근 사용 시간", 18, DARK, true));
        recentCard.addView(note("최근에 사용한 최대 5개를 표시 · 버튼을 누르면 즉시 잠금 시작"));
        List<Integer> recent = LockStore.recents(this);
        if (recent.isEmpty()) recentCard.addView(note("아직 기록이 없어. 첫 잠금을 시작하면 여기에 표시돼."));
        for (int mins : recent) {
            Button quick = button(LockStore.durationText(mins) + " 바로 시작", false);
            recentCard.addView(quick);
            quick.setOnClickListener(v -> startFor(mins));
        }

        LinearLayout schedCard = card(screen);
        schedCard.addView(text("한 번만 자동 예약", 18, DARK, true));
        int count = LockStore.weekCount(this, System.currentTimeMillis());
        schedCard.addView(note("이번 주 사용·예약: " + count + "/3 (월~일 기준) · 수동 잠금 무제한"));
        schedCard.addView(note("시작·종료 일시를 각각 선택해. 다음 날로 넘어가는 예약도 가능해."));
        Button startPicker = button("시작: " + formatted(chosenStart), false);
        schedCard.addView(startPicker);
        startPicker.setOnClickListener(v -> pickDateTime(chosenStart, true));
        Button endPicker = button("종료: " + formatted(chosenEnd), false);
        schedCard.addView(endPicker);
        endPicker.setOnClickListener(v -> pickDateTime(chosenEnd, false));
        Button reserve = button("자동 잠금 예약 추가", true);
        schedCard.addView(reserve);
        reserve.setOnClickListener(v -> createSchedule());

        schedCard.addView(note("\n예약 목록 (시작 전에는 삭제 가능)"));
        List<LockStore.Entry> entries = LockStore.schedules(this);
        int shown = 0;
        for (LockStore.Entry entry : entries) {
            if (entry.end < System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)) continue;
            shown++;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(0, dp(8), 0, dp(8));
            String phase = entry.end <= System.currentTimeMillis() ? "완료"
                    : entry.start <= System.currentTimeMillis() ? "진행 중" : "예정";
            item.addView(text(phase + "  " + formatted(entry.start) + "\n       ~ " + formatted(entry.end), 13, DARK, false));
            if (entry.start > System.currentTimeMillis()) {
                Button del = button("이 예약 취소", false);
                item.addView(del);
                del.setOnClickListener(v -> {
                    AlarmPlanner.cancelSchedule(this, entry.id);
                    if (!LockStore.cancelUpcoming(this, entry.id)) {
                        alert("취소 불가", "이미 시작된 예약은 취소할 수 없어.");
                    }
                    buildUi();
                });
            }
            schedCard.addView(item);
        }
        if (shown == 0) schedCard.addView(note("등록된 예약이 없어."));

        LinearLayout bottom = card(screen);
        bottom.addView(text("안전 및 작동 범위", 16, DARK, true));
        bottom.addView(note("• 112·119 등 긴급통화는 막지 않습니다.\n" +
                "• 수신 전화·문자는 차단하지 않습니다.\n" +
                "• 통신사 일반 발신은 통화 리디렉션 권한이 있을 때 취소합니다.\n" +
                "• 문자·카톡·인스타는 앱 자체를 열지 못하게 합니다.\n" +
                "• 브라우저, 다른 문자 앱, 앱 재설치, 권한 해제, 시스템 시각 변경 등으로 우회할 수 있습니다.\n" +
                "• 데이터는 휴대폰에만 저장하며 서버로 전송하지 않습니다."));

        updateCapabilities();
        updateLiveStatus();
    }

    private void startFor(int minutes) {
        if (!ensureReady(false)) return;
        if (LockStore.isLocked(this)) {
            alert("이미 잠금 중", "현재 잠금이 끝난 뒤 새로운 잠금을 시작할 수 있어.");
            return;
        }
        if (!LockStore.startManual(this, minutes)) {
            alert("잠금 실패", "저장을 완료하지 못했어. 다시 시도해 줘.");
            return;
        }
        AlarmPlanner.reschedule(this);
        buildUi();
    }

    private void createSchedule() {
        if (!ensureReady(true)) return;
        if (chosenStart <= System.currentTimeMillis()) {
            alert("시작 시간 확인", "미래의 시작 시간을 골라줘.");
            return;
        }
        if (chosenEnd <= chosenStart || chosenEnd - chosenStart > LockStore.MAX_DURATION) {
            alert("종료 시간 확인", "종료는 시작보다 늦어야 하고, 잠금은 최대 7일이야.");
            return;
        }
        if (LockStore.weekCount(this, chosenStart) >= 3) {
            alert("주간 제한", "예약 시작일이 속한 주에는 최대 3개까지만 등록할 수 있어.");
            return;
        }
        LockStore.Entry result = LockStore.addSchedule(this, chosenStart, chosenEnd);
        if (result == null) {
            alert("예약 불가", "다른 예약과 시간이 겹치거나, 주 3개 제한에 도달했는지 확인해 줘.");
            return;
        }
        AlarmPlanner.reschedule(this);
        buildUi();
        alert("예약 완료", "시작: " + formatted(result.start) + "\n종료: " + formatted(result.end)
                + "\n잠금 시작 후에는 앱에서 취소할 수 없어.");
    }

    private boolean ensureReady(boolean forSchedule) {
        StringBuilder missing = new StringBuilder();
        if (!accessibilityEnabled()) missing.append("• 앱 차단 접근성 권한\n");
        if (!callRoleHeld()) missing.append("• 전화 발신 제한 역할\n");
        if (forSchedule && !AlarmPlanner.exactAvailable(this)) missing.append("• 정시 알람 권한\n");
        if (missing.length() != 0) {
            alert("권한 설정 필요", "완전하지 않은 잠금을 시작하지 않도록 다음 권한을 먼저 켜 줘.\n\n"
                    + missing + "\n위쪽의 권한 설정 버튼에서 허용할 수 있어.");
            return false;
        }
        return true;
    }

    private boolean accessibilityEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        ComponentName expected = new ComponentName(this, GuardAccessibilityService.class);
        for (String s : enabled.split(":")) {
            ComponentName name = ComponentName.unflattenFromString(s);
            if (expected.equals(name)) return true;
        }
        return false;
    }

    private RoleManager roles() {
        return (RoleManager) getSystemService(ROLE_SERVICE);
    }

    private boolean callRoleHeld() {
        RoleManager m = roles();
        return m != null && m.isRoleAvailable(RoleManager.ROLE_CALL_REDIRECTION)
                && m.isRoleHeld(RoleManager.ROLE_CALL_REDIRECTION);
    }

    private void requestCallRole() {
        RoleManager m = roles();
        if (m == null || !m.isRoleAvailable(RoleManager.ROLE_CALL_REDIRECTION)) {
            alert("기기에서 이용 불가", "이 갤럭시에서는 통화 리디렉션 역할을 사용할 수 없어. "
                    + "일반 발신을 안전하게 차단할 수 없으므로 잠금을 시작하지 않도록 만들었어.");
            return;
        }
        if (callRoleHeld()) {
            alert("권한 설정됨", "이미 전화 발신 제한 역할이 허용돼 있어.");
            return;
        }
        startActivityForResult(m.createRequestRoleIntent(RoleManager.ROLE_CALL_REDIRECTION), 17);
    }

    private void requestExactAlarm() {
        if (AlarmPlanner.exactAvailable(this)) {
            alert("권한 설정됨", "정시 알람 권한이 이미 허용돼 있어.");
            return;
        }
        if (Build.VERSION.SDK_INT >= 31) {
            Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName()));
            try {
                startActivity(i);
            } catch (Exception e) {
                alert("권한 화면 오류", "설정 > 앱 > 특별한 접근 > 알람 및 리마인더에서 허용해 줘.");
            }
        }
    }

    private void updateCapabilities() {
        if (capabilities == null) return;
        capabilities.setText("앱 차단: " + (accessibilityEnabled() ? "켜짐 ✓" : "꺼짐 ✕") + "\n"
                + "일반 발신 제한: " + (callRoleHeld() ? "켜짐 ✓" : "꺼짐 ✕") + "\n"
                + "정확한 예약 알람: " + (AlarmPlanner.exactAvailable(this) ? "허용 ✓" : "허용 필요 ✕"));
    }

    private void updateLiveStatus() {
        if (liveStatus == null) return;
        long until = LockStore.activeUntil(this);
        if (until <= System.currentTimeMillis()) {
            liveStatus.setText("○ 현재 잠금 해제 상태");
        } else {
            long remain = Math.max(0, until - System.currentTimeMillis());
            long hours = TimeUnit.MILLISECONDS.toHours(remain);
            long minutes = TimeUnit.MILLISECONDS.toMinutes(remain) % 60;
            long seconds = TimeUnit.MILLISECONDS.toSeconds(remain) % 60;
            liveStatus.setText(String.format(Locale.KOREA,
                    "● 잠금 작동 중\n남은 시간 %02d:%02d:%02d\n해제: %s",
                    hours, minutes, seconds, formatted(until)));
        }
    }

    private void pickDateTime(long original, boolean start) {
        Calendar date = Calendar.getInstance();
        date.setTimeInMillis(original);
        new DatePickerDialog(this, (picker, y, month, day) -> {
            Calendar selected = Calendar.getInstance();
            selected.set(y, month, day);
            new TimePickerDialog(this, (tp, hour, minute) -> {
                selected.set(Calendar.HOUR_OF_DAY, hour);
                selected.set(Calendar.MINUTE, minute);
                selected.set(Calendar.SECOND, 0);
                selected.set(Calendar.MILLISECOND, 0);
                if (start) chosenStart = selected.getTimeInMillis();
                else chosenEnd = selected.getTimeInMillis();
                buildUi();
            }, date.get(Calendar.HOUR_OF_DAY), date.get(Calendar.MINUTE), true).show();
        }, date.get(Calendar.YEAR), date.get(Calendar.MONTH), date.get(Calendar.DAY_OF_MONTH)).show();
    }

    private static String formatted(long ms) {
        DateFormat fmt = new java.text.SimpleDateFormat("M월 d일 (EEE) HH:mm", Locale.KOREA);
        return fmt.format(new Date(ms));
    }

    private int parse(EditText input) {
        try { return Integer.parseInt(input.getText().toString().trim()); }
        catch (Exception e) { return -1; }
    }

    private EditText numberInput(String hint, String initial) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setText(initial);
        e.setHint(hint);
        e.setSelectAllOnFocus(true);
        e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(3)});
        e.setTextSize(19);
        return e;
    }

    private void alert(String title, String message) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message)
                .setPositiveButton("확인", (d, w) -> {}).show();
    }

    private LinearLayout card(LinearLayout parent) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(17), dp(17), dp(17), dp(17));
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(Color.WHITE);
        shape.setCornerRadius(dp(17));
        box.setBackground(shape);
        box.setElevation(dp(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(15);
        parent.addView(box, lp);
        return box;
    }

    private Button button(String title, boolean main) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setTextColor(main ? Color.WHITE : PURPLE);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(main ? PURPLE : Color.rgb(242, 241, 255));
        shape.setCornerRadius(dp(11));
        b.setBackground(shape);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50));
        lp.topMargin = dp(9);
        b.setLayoutParams(lp);
        return b;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(sp);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private TextView note(String value) {
        TextView view = text(value, 13, GRAY, false);
        view.setPadding(0, dp(7), 0, dp(3));
        return view;
    }

    private int dp(float val) {
        return (int) (val * getResources().getDisplayMetrics().density + 0.5f);
    }
}
