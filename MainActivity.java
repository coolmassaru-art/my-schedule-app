package com.coolmassaru.myschedule;

import android.*;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;

import org.json.*;

import java.io.*;
import java.text.*;
import java.util.*;
import java.util.regex.*;

public class MainActivity extends Activity {
    private LinearLayout eventList;
    private TextView selectedTitle;
    private TextView monthTitle;
    private LinearLayout calendarDaysGrid;
    private Calendar selected = Calendar.getInstance();
    private Calendar displayMonth = Calendar.getInstance();
    private JSONArray events = new JSONArray();

    private final String PREFS = "schedule_prefs_v2";
    private final String KEY = "events";
    private final String[] CATS = {"개인","가족","아이","운동","가게","기타"};

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestNotificationPermission();
        loadEvents();
        migrateAndScheduleExistingEvents();
        buildUi();
        handleShare(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleShare(intent);
    }

    private int dp(int x) {
        return (int)(x * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int getBottomSafePadding() {
        int id = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        int nav = id > 0 ? getResources().getDimensionPixelSize(id) : 0;
        return dp(12) + nav;
    }


    private TextView tv(String text, int sp, boolean bold) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(sp);
        v.setTextColor(Color.rgb(24, 27, 34));
        if (bold) v.setTypeface(null, Typeface.BOLD);
        return v;
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 77);
        }
    }

    private boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
            && a.get(Calendar.MONTH) == b.get(Calendar.MONTH)
            && a.get(Calendar.DAY_OF_MONTH) == b.get(Calendar.DAY_OF_MONTH);
    }

    private void shiftMonth(int delta) {
        displayMonth.add(Calendar.MONTH, delta);
        int maxDay = displayMonth.getActualMaximum(Calendar.DAY_OF_MONTH);
        int keepDay = Math.min(selected.get(Calendar.DAY_OF_MONTH), maxDay);
        selected.set(displayMonth.get(Calendar.YEAR), displayMonth.get(Calendar.MONTH), keepDay, 0, 0, 0);
        refresh();
    }

    private TextView makeNavButton(String text) {
        TextView v = tv(text, 22, true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(8), dp(4), dp(8), dp(4));
        return v;
    }

    private LinearLayout.LayoutParams weekCellParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        lp.setMargins(dp(2), dp(2), dp(2), dp(2));
        return lp;
    }

    private boolean hasEventOn(Calendar c) {
        String dateKey = key(c);
        for (int i = 0; i < events.length(); i++) {
            JSONObject e = events.optJSONObject(i);
            if (e != null && dateKey.equals(e.optString("date"))) return true;
        }
        return false;
    }

    private android.graphics.drawable.GradientDrawable roundedBg(int fillColor, int strokeColor, int strokeWidthDp, int radiusDp) {
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(fillColor);
        bg.setCornerRadius(dp(radiusDp));
        if (strokeWidthDp > 0) bg.setStroke(dp(strokeWidthDp), strokeColor);
        return bg;
    }

    private void styleMonthNav(TextView v) {
        v.setTextSize(22);
        v.setTextColor(Color.rgb(60, 64, 72));
        v.setBackground(roundedBg(Color.rgb(239, 241, 247), Color.TRANSPARENT, 0, 18));
        v.setPadding(dp(10), dp(4), dp(10), dp(4));
    }

    private void renderCalendar() {
        if (monthTitle == null || calendarDaysGrid == null) return;

        monthTitle.setText(new SimpleDateFormat("yyyy년 M월", Locale.KOREA).format(displayMonth.getTime()));
        calendarDaysGrid.removeAllViews();

        Calendar today = Calendar.getInstance();
        Calendar first = (Calendar) displayMonth.clone();
        first.set(Calendar.DAY_OF_MONTH, 1);
        int offset = first.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY;
        Calendar cursor = (Calendar) first.clone();
        cursor.add(Calendar.DAY_OF_MONTH, -offset);

        for (int week = 0; week < 6; week++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);

            for (int day = 0; day < 7; day++) {
                final Calendar cellDate = (Calendar) cursor.clone();
                boolean inMonth = cellDate.get(Calendar.YEAR) == displayMonth.get(Calendar.YEAR)
                        && cellDate.get(Calendar.MONTH) == displayMonth.get(Calendar.MONTH);
                int dow = cellDate.get(Calendar.DAY_OF_WEEK);
                boolean isSelected = sameDay(cellDate, selected);
                boolean isToday = sameDay(cellDate, today);
                boolean hasEvent = inMonth && hasEventOn(cellDate);

                LinearLayout cellBox = new LinearLayout(this);
                cellBox.setOrientation(LinearLayout.VERTICAL);
                cellBox.setGravity(Gravity.CENTER);
                cellBox.setPadding(dp(1), dp(2), dp(1), dp(1));

                TextView dayText = new TextView(this);
                dayText.setText(String.valueOf(cellDate.get(Calendar.DAY_OF_MONTH)));
                dayText.setGravity(Gravity.CENTER);
                dayText.setTextSize(16);

                if (!inMonth) {
                    dayText.setTextColor(Color.LTGRAY);
                } else if (isSelected) {
                    dayText.setTextColor(Color.WHITE);
                    dayText.setTypeface(null, Typeface.BOLD);
                } else if (dow == Calendar.SUNDAY) {
                    dayText.setTextColor(Color.rgb(220, 38, 38));
                } else if (dow == Calendar.SATURDAY) {
                    dayText.setTextColor(Color.rgb(37, 99, 235));
                } else {
                    dayText.setTextColor(Color.rgb(24, 27, 34));
                }

                TextView dot = new TextView(this);
                dot.setText(hasEvent ? "●" : "");
                dot.setGravity(Gravity.CENTER);
                dot.setTextSize(8);
                dot.setTextColor(isSelected ? Color.WHITE : Color.rgb(88, 101, 242));

                cellBox.addView(dayText, new LinearLayout.LayoutParams(-1, 0, 1f));
                cellBox.addView(dot, new LinearLayout.LayoutParams(-1, dp(10)));

                if (isSelected) {
                    cellBox.setBackground(roundedBg(Color.rgb(88, 101, 242), Color.TRANSPARENT, 0, 18));
                } else if (isToday && inMonth) {
                    cellBox.setBackground(roundedBg(Color.TRANSPARENT, Color.rgb(88, 101, 242), 1, 18));
                } else {
                    cellBox.setBackgroundColor(Color.TRANSPARENT);
                }

                cellBox.setOnClickListener(v -> {
                    selected.set(cellDate.get(Calendar.YEAR), cellDate.get(Calendar.MONTH), cellDate.get(Calendar.DAY_OF_MONTH), 0, 0, 0);
                    displayMonth.set(cellDate.get(Calendar.YEAR), cellDate.get(Calendar.MONTH), 1, 0, 0, 0);
                    refresh();
                });

                row.addView(cellBox, weekCellParams());
                cursor.add(Calendar.DAY_OF_MONTH, 1);
            }

            calendarDaysGrid.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), getBottomSafePadding());
        root.setBackgroundColor(Color.rgb(247,248,252));

        TextView title = tv("나만의 일정", 24, true);
        root.addView(title);

        TextView sub = tv("카카오톡·문자 메시지·스크린샷을 일정으로 바로 저장 · 기본 1시간 전 알림", 13, false);
        sub.setTextColor(Color.GRAY);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1,-2);
        sp.setMargins(0, dp(4),0,dp(10));
        root.addView(sub, sp);

        LinearLayout calBox = new LinearLayout(this);
        calBox.setOrientation(LinearLayout.VERTICAL);
        calBox.setPadding(dp(8), dp(8), dp(8), dp(8));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);

        TextView prev = makeNavButton("‹");
        styleMonthNav(prev);
        prev.setOnClickListener(v -> shiftMonth(-1));
        monthTitle = tv("", 18, true);
        monthTitle.setGravity(Gravity.CENTER);
        TextView next = makeNavButton("›");
        styleMonthNav(next);
        next.setOnClickListener(v -> shiftMonth(1));

        nav.addView(prev, new LinearLayout.LayoutParams(0, -2, 1f));
        nav.addView(monthTitle, new LinearLayout.LayoutParams(0, -2, 5f));
        nav.addView(next, new LinearLayout.LayoutParams(0, -2, 1f));
        calBox.addView(nav);

        LinearLayout weekHeader = new LinearLayout(this);
        weekHeader.setOrientation(LinearLayout.HORIZONTAL);
        String[] weeks = {"일","월","화","수","목","금","토"};
        for (int i = 0; i < weeks.length; i++) {
            TextView w = tv(weeks[i], 14, false);
            w.setGravity(Gravity.CENTER);
            if (i == 0) w.setTextColor(Color.rgb(220, 38, 38));
            else if (i == 6) w.setTextColor(Color.rgb(37, 99, 235));
            else w.setTextColor(Color.GRAY);
            weekHeader.addView(w, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        LinearLayout.LayoutParams whp = new LinearLayout.LayoutParams(-1, -2);
        whp.setMargins(0, dp(6), 0, dp(4));
        calBox.addView(weekHeader, whp);

        calendarDaysGrid = new LinearLayout(this);
        calendarDaysGrid.setOrientation(LinearLayout.VERTICAL);
        calBox.addView(calendarDaysGrid, new LinearLayout.LayoutParams(-1, -2));

        root.addView(calBox, new LinearLayout.LayoutParams(-1, -2));

        selectedTitle = tv("", 18, true);
        LinearLayout.LayoutParams stp = new LinearLayout.LayoutParams(-1,-2);
        stp.setMargins(0, dp(12),0,dp(8));
        root.addView(selectedTitle, stp);

        ScrollView scroll = new ScrollView(this);
        eventList = new LinearLayout(this);
        eventList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(eventList);
        root.addView(scroll, new LinearLayout.LayoutParams(-1,0,1f));

        Button add = new Button(this);
        add.setText("＋ 일정 추가");
        add.setTextSize(15);
        add.setMinimumHeight(dp(48));
        add.setOnClickListener(v -> openEditor(null, "", new ArrayList<>(), false));
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(-1, -2);
        addLp.setMargins(0, dp(8), 0, dp(6));
        root.addView(add, addLp);

        displayMonth.set(selected.get(Calendar.YEAR), selected.get(Calendar.MONTH), 1, 0, 0, 0);
        setContentView(root);
        refresh();
    }

    private String key(Calendar c) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(c.getTime());
    }

    private String niceDate(Calendar c) {
        return new SimpleDateFormat("M월 d일 EEEE", Locale.KOREA).format(c.getTime());
    }

    private void refresh() {
        renderCalendar();
        selectedTitle.setText(niceDate(selected));
        eventList.removeAllViews();
        boolean any = false;

        ArrayList<Integer> indices = new ArrayList<>();
        for (int i=0; i<events.length(); i++) {
            JSONObject e = events.optJSONObject(i);
            if (e != null && key(selected).equals(e.optString("date"))) indices.add(i);
        }

        Collections.sort(indices, (a,b) ->
            events.optJSONObject(a).optString("time","99:99")
                .compareTo(events.optJSONObject(b).optString("time","99:99"))
        );

        for (int idx : indices) {
            any = true;
            JSONObject e = events.optJSONObject(idx);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14),dp(12),dp(14),dp(12));
            card.setBackgroundColor(Color.WHITE);

            card.addView(tv(e.optString("title","일정"), 17, true));

            String meta = e.optString("time","") + " · " + e.optString("category","개인");
            int reminderMin = e.optInt("reminderMin", 60);
            if (reminderMin > 0) meta += " · " + (reminderMin == 60 ? "1시간 전 알림" : reminderMin + "분 전 알림");

            TextView m = tv(meta, 13, false);
            m.setTextColor(Color.GRAY);
            card.addView(m);

            String memo = e.optString("memo","");
            if (!memo.isEmpty()) {
                TextView mm = tv(memo, 14, false);
                mm.setMaxLines(3);
                LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1,-2);
                mp.setMargins(0,dp(5),0,0);
                card.addView(mm, mp);
            }

            JSONArray imgs = e.optJSONArray("images");
            if (imgs != null && imgs.length() > 0) {
                TextView at = tv("📎 스크린샷 " + imgs.length() + "장", 13, false);
                at.setTextColor(Color.rgb(88,101,242));
                card.addView(at);
            }

            card.setOnClickListener(v -> openEditor(e, "", new ArrayList<>(), true));
            card.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this)
                    .setTitle("일정 삭제")
                    .setMessage("이 일정을 삭제할까요?")
                    .setNegativeButton("취소", null)
                    .setPositiveButton("삭제", (d,w) -> deleteEventById(e.optString("id")))
                    .show();
                return true;
            });

            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1,-2);
            cp.setMargins(0,0,0,dp(8));
            eventList.addView(card, cp);
        }

        if (!any) {
            TextView empty = tv("등록된 일정이 없습니다.\n아래의 ‘일정 추가’를 눌러보세요.",15,false);
            empty.setGravity(Gravity.CENTER);
            empty.setTextColor(Color.GRAY);
            empty.setPadding(0,dp(32),0,dp(32));
            eventList.addView(empty);
        }
    }

    private void openEditor(JSONObject existing, String incomingText,
                            ArrayList<String> incomingImages, boolean editing) {

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18),0,dp(18),0);

        EditText title = new EditText(this);
        title.setHint("일정 제목");
        box.addView(title);

        Button dateBtn = new Button(this);
        Calendar editDate = (Calendar) selected.clone();
        dateBtn.setText("날짜: " + key(editDate));
        dateBtn.setOnClickListener(v -> new DatePickerDialog(
            this,
            (view,y,m,d) -> {
                editDate.set(y,m,d,0,0,0);
                dateBtn.setText("날짜: " + key(editDate));
            },
            editDate.get(Calendar.YEAR),
            editDate.get(Calendar.MONTH),
            editDate.get(Calendar.DAY_OF_MONTH)
        ).show());
        box.addView(dateBtn);

        EditText time = new EditText(this);
        time.setHint("시간 예: 16:00");
        box.addView(time);

        Spinner category = new Spinner(this);
        category.setAdapter(new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_dropdown_item, CATS));
        box.addView(category);

        Spinner reminder = new Spinner(this);
        String[] reminderLabels = {"알림 없음","10분 전","30분 전","1시간 전","1일 전"};
        int[] reminderVals = {0,10,30,60,1440};
        reminder.setAdapter(new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_dropdown_item, reminderLabels));
        reminder.setSelection(3); // 새 일정은 항상 1시간 전 알림이 기본
        box.addView(reminder);

        EditText memo = new EditText(this);
        memo.setHint("메모 / 카카오톡 원문");
        memo.setMinLines(3);
        box.addView(memo);

        ArrayList<String> images = new ArrayList<>(incomingImages);

        if (existing != null) {
            title.setText(existing.optString("title",""));
            time.setText(existing.optString("time","09:00"));
            memo.setText(existing.optString("memo",""));

            try {
                Date d = new SimpleDateFormat("yyyy-MM-dd",Locale.KOREA)
                    .parse(existing.optString("date"));
                if (d != null) editDate.setTime(d);
            } catch(Exception ignored) {}
            dateBtn.setText("날짜: " + key(editDate));

            String cat = existing.optString("category","개인");
            for (int i=0;i<CATS.length;i++) if (CATS[i].equals(cat)) category.setSelection(i);

            int rv = existing.optInt("reminderMin",60);
            for (int i=0;i<reminderVals.length;i++) if (reminderVals[i]==rv) reminder.setSelection(i);

            JSONArray a = existing.optJSONArray("images");
            if (a != null) for(int i=0;i<a.length();i++) images.add(a.optString(i));
        } else {
            ParsedSchedule p = parseScheduleText(incomingText);
            title.setText(p.title);
            time.setText(p.time);
            memo.setText(p.cleanMemo.isEmpty() ? incomingText : p.cleanMemo);

            if (p.date != null) {
                editDate.setTime(p.date);
                dateBtn.setText("날짜: " + key(editDate));
            }

            if (incomingText.trim().isEmpty() && images.size() > 0) {
                title.setText("카카오톡 스크린샷 일정");
            }
        }

        TextView att = tv(images.isEmpty() ? "" :
            "📎 첨부 스크린샷 " + images.size() + "장", 13, false);
        att.setTextColor(Color.rgb(88,101,242));
        box.addView(att);

        new AlertDialog.Builder(this)
            .setTitle(editing ? "일정 수정" :
                (incomingText.isEmpty() && images.isEmpty() ? "새 일정" : "공유 내용을 일정으로 저장"))
            .setView(box)
            .setNegativeButton("취소", null)
            .setPositiveButton(editing ? "수정 저장" : "저장", (d,w) -> {
                String id = existing != null ? existing.optString("id")
                    : UUID.randomUUID().toString();
                JSONObject e = new JSONObject();
                try {
                    e.put("id", id);
                    e.put("title", title.getText().toString().trim().isEmpty()
                        ? "새 일정" : title.getText().toString().trim());
                    e.put("date", key(editDate));
                    e.put("time", normalizeTime(time.getText().toString().trim()));
                    e.put("category", CATS[category.getSelectedItemPosition()]);
                    e.put("memo", memo.getText().toString().trim());
                    e.put("reminderMin", reminderVals[reminder.getSelectedItemPosition()]);
                    JSONArray ja = new JSONArray();
                    for(String s: images) ja.put(s);
                    e.put("images", ja);

                    if (existing != null) replaceById(id, e);
                    else events.put(e);

                    selected = (Calendar) editDate.clone();
                    calendarView.setDate(selected.getTimeInMillis(), false, true);
                    saveEvents();
                    scheduleReminder(e);
                    refresh();
                } catch(Exception ignored) {}
            })
            .show();
    }

    private String normalizeTime(String s) {
        Matcher m = Pattern.compile("(\\d{1,2})[:시 ](\\d{1,2})?").matcher(s);
        if (m.find()) {
            int h = Integer.parseInt(m.group(1));
            int min = m.group(2)==null ? 0 : Integer.parseInt(m.group(2));
            return String.format(Locale.KOREA,"%02d:%02d",h,min);
        }
        return "09:00";
    }

    private void replaceById(String id, JSONObject replacement) {
        JSONArray n = new JSONArray();
        for(int i=0;i<events.length();i++) {
            JSONObject e = events.optJSONObject(i);
            if (e != null && id.equals(e.optString("id"))) n.put(replacement);
            else n.put(events.opt(i));
        }
        events = n;
    }

    private void deleteEventById(String id) {
        JSONArray n = new JSONArray();
        for(int i=0;i<events.length();i++) {
            JSONObject e = events.optJSONObject(i);
            if (e == null || !id.equals(e.optString("id"))) n.put(events.opt(i));
        }
        events = n;
        saveEvents();
        refresh();
    }

    private void handleShare(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        String type = intent.getType();
        if (action == null || type == null) return;

        if (Intent.ACTION_SEND.equals(action)) {
            if (type.startsWith("text/")) {
                String text = intent.getStringExtra(Intent.EXTRA_TEXT);
                if (text == null) text = "";
                openEditor(null, text, new ArrayList<>(), false);
            } else if (type.startsWith("image/")) {
                Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (uri != null) {
                    ArrayList<String> imgs = new ArrayList<>();
                    String saved = copyImage(uri);
                    if (saved != null) imgs.add(saved);
                    runOcrAndOpen(uri, imgs);
                }
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type.startsWith("image/")) {
            ArrayList<Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            ArrayList<String> imgs = new ArrayList<>();
            if (uris != null) {
                for (Uri u: uris) {
                    String saved = copyImage(u);
                    if (saved != null) imgs.add(saved);
                }
                if (!uris.isEmpty()) runOcrAndOpen(uris.get(0), imgs);
                else openEditor(null, "", imgs, false);
            }
        }
    }

    private void runOcrAndOpen(Uri uri, ArrayList<String> imgs) {
        try {
            InputImage image = InputImage.fromFilePath(this, uri);
            TextRecognizer recognizer = TextRecognition.getClient(
                new KoreanTextRecognizerOptions.Builder().build()
            );
            recognizer.process(image)
                .addOnSuccessListener(result -> {
                    String text = result.getText();
                    openEditor(null, text == null ? "" : text, imgs, false);
                    recognizer.close();
                })
                .addOnFailureListener(e -> {
                    openEditor(null, "", imgs, false);
                    recognizer.close();
                });
        } catch(Exception e) {
            openEditor(null, "", imgs, false);
        }
    }

    static class ParsedSchedule {
        String title = "";
        String time = "09:00";
        String endTime = "";
        String location = "";
        String cleanMemo = "";
        Date date = null;
    }

    private ParsedSchedule parseScheduleText(String text) {
        ParsedSchedule p = new ParsedSchedule();
        if (text == null) text = "";
        String clean = text.replace('\u00A0',' ').trim();
        String[] lines = clean.split("\\n");
        Calendar now = Calendar.getInstance();

        ArrayList<String> usefulLines = new ArrayList<>();
        for (String raw : lines) {
            String line = raw.trim();
            if (isNoiseLine(line)) continue;
            usefulLines.add(line);
        }

        // 1) 제목: 카톡/문자에서 일정 안내 제목이나 일정 키워드가 있는 줄을 우선
        for (String raw : lines) {
            String line = raw.trim();
            if (line.matches("^\\[.+(일정|안내).+\\]$") || line.matches("^\\[.+(일정|안내)\\]$")) {
                p.title = line.replaceAll("^\\[|\\]$", "").trim();
                break;
            }
        }
        if (p.title.isEmpty()) {
            for (String raw : lines) {
                String line = raw.trim();
                if (isNoiseLine(line)) continue;
                if (line.contains("게임") || line.contains("경기") || line.contains("훈련") ||
                    line.contains("레슨") || line.contains("모임") || line.contains("예약") ||
                    line.contains("병원") || line.contains("진료") || line.contains("방문") ||
                    line.contains("상담") || line.contains("미팅") || line.contains("면접")) {
                    p.title = line.length() > 40 ? line.substring(0,40) : line;
                    break;
                }
            }
        }
        if (p.title.isEmpty()) p.title = "메시지 일정";

        // 2) 날짜: 9/12(토), 9/12, 9-12, 9.12, 9월 12일, 내일/모레
        if (clean.contains("모레")) {
            Calendar c = Calendar.getInstance();
            c.add(Calendar.DAY_OF_MONTH,2);
            p.date = c.getTime();
        } else if (clean.contains("내일")) {
            Calendar c = Calendar.getInstance();
            c.add(Calendar.DAY_OF_MONTH,1);
            p.date = c.getTime();
        } else {
            Matcher slash = Pattern.compile("(?<!\\d)(\\d{1,2})\\s*/\\s*(\\d{1,2})(?:\\s*\\([^)]*\\))?").matcher(clean);
            Matcher korean = Pattern.compile("(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일").matcher(clean);
            Matcher dotted = Pattern.compile("(?<!\\d)(\\d{1,2})\\s*[.-]\\s*(\\d{1,2})(?!\\d)").matcher(clean);
            int month = -1, day = -1;
            if (slash.find()) {
                month = Integer.parseInt(slash.group(1));
                day = Integer.parseInt(slash.group(2));
            } else if (korean.find()) {
                month = Integer.parseInt(korean.group(1));
                day = Integer.parseInt(korean.group(2));
            } else if (dotted.find()) {
                month = Integer.parseInt(dotted.group(1));
                day = Integer.parseInt(dotted.group(2));
            }
            if (month > 0 && day > 0) {
                Calendar c = Calendar.getInstance();
                c.set(Calendar.MONTH, month-1);
                c.set(Calendar.DAY_OF_MONTH, day);
                c.set(Calendar.HOUR_OF_DAY,0);
                c.set(Calendar.MINUTE,0);
                if (c.before(now)) c.add(Calendar.YEAR,1);
                p.date = c.getTime();
            }
        }

        // 3) 시간: "오후4~6시" 같은 범위를 가장 먼저 찾음 → 시작시간 16:00
        Matcher range = Pattern.compile("(오전|오후)?\\s*(\\d{1,2})\\s*(?:시)?\\s*[~～\\-]\\s*(\\d{1,2})\\s*시").matcher(clean);
        if (range.find()) {
            int h = Integer.parseInt(range.group(2));
            int endH = Integer.parseInt(range.group(3));
            String ap = range.group(1);
            if ("오후".equals(ap) && h < 12) h += 12;
            if ("오전".equals(ap) && h == 12) h = 0;
            if ("오후".equals(ap) && endH < 12) endH += 12;
            if ("오전".equals(ap) && endH == 12) endH = 0;
            p.time = String.format(Locale.KOREA,"%02d:00",h);
            p.endTime = String.format(Locale.KOREA,"%02d:00",endH);
        }

        // "오후 4시", "오전 10시 30분"
        Matcher natural = Pattern.compile("(오전|오후)\\s*(\\d{1,2})\\s*시\\s*(\\d{1,2})?\\s*분?").matcher(clean);
        if (natural.find()) {
            int h = Integer.parseInt(natural.group(2));
            int min = natural.group(3) == null ? 0 : Integer.parseInt(natural.group(3));
            if ("오후".equals(natural.group(1)) && h < 12) h += 12;
            if ("오전".equals(natural.group(1)) && h == 12) h = 0;
            p.time = String.format(Locale.KOREA,"%02d:%02d",h,min);
        }

        // 콜론 시간은 상태바/대화 전송시간 오인 방지를 위해 일정 키워드가 같은 줄에 있을 때만 사용
        for (String raw : lines) {
            String line = raw.trim();
            if (!(line.contains("일정") || line.contains("게임") || line.contains("경기") ||
                  line.contains("훈련") || line.contains("레슨") || line.contains("예약") ||
                  line.contains("병원") || line.contains("진료") || line.contains("방문") ||
                  line.contains("상담") || line.contains("미팅") || line.contains("면접"))) continue;
            Matcher clock = Pattern.compile("(오전|오후)?\\s*(\\d{1,2}):(\\d{2})").matcher(line);
            if (clock.find()) {
                int h = Integer.parseInt(clock.group(2));
                int min = Integer.parseInt(clock.group(3));
                String ap = clock.group(1);
                if ("오후".equals(ap) && h < 12) h += 12;
                if ("오전".equals(ap) && h == 12) h = 0;
                p.time = String.format(Locale.KOREA,"%02d:%02d",h,min);
                break;
            }
        }

        // 4) 장소와 메모 정리
        for (String line : usefulLines) {
            if (line.matches(".*(공원|병원|의원|센터|학교|학원|식당|카페|체육관|운동장|회의실|매장|호텔|역|공항).*")) {
                if (!line.contains("일정") && !line.matches(".*\\d{1,2}[:시].*")) {
                    p.location = line;
                    break;
                }
            }
        }

        ArrayList<String> memoParts = new ArrayList<>();
        if (!p.location.isEmpty()) memoParts.add("장소: " + p.location);
        if (!p.endTime.isEmpty()) memoParts.add("시간: " + p.time + " ~ " + p.endTime);

        for (String line : usefulLines) {
            if (line.equals(p.title)) continue;
            if (line.equals(p.location)) continue;
            if (line.matches(".*\\d{1,2}\\s*[/.-]\\s*\\d{1,2}.*")) continue;
            if (line.matches(".*\\d{1,2}\\s*월\\s*\\d{1,2}\\s*일.*")) continue;
            if (line.matches(".*(오전|오후)?\\s*\\d{1,2}\\s*(시|:)?.*")) continue;
            if (line.length() < 3) continue;
            memoParts.add(line);
            if (memoParts.size() >= 4) break;
        }

        p.cleanMemo = android.text.TextUtils.join("\n", memoParts);
        return p;
    }

    private boolean isNoiseLine(String line) {
        if (line == null || line.isEmpty()) return true;
        if (line.matches("^KT\\b.*")) return true;
        if (line.matches(".*\\b(TALK|LTE|5G|HD)\\b.*")) return true;
        if (line.matches("^\\d{1,2}:\\d{2}.*$")) return true;
        if (line.matches("^(오전|오후)?\\s*\\d{1,2}:\\d{2}$")) return true;
        if (line.matches("^\\d{4}년\\s*\\d{1,2}월\\s*\\d{1,2}일.*$")) return true;
        if (line.matches("^[가-힣]{2,4}$")) return true; // 사람 이름만 있는 줄
        if (line.equals("메시지 입력")) return true;
        if (line.equals("TALK")) return true;
        if (line.matches("^[<←→>]+.*")) return true;
        if (line.matches("^오후\\s*\\d{1,2}:\\d{2}.*")) return true;
        return false;
    }

    private void scheduleReminder(JSONObject e) {
        int minBefore = e.optInt("reminderMin",60);
        if (minBefore <= 0) return;

        try {
            String date = e.optString("date");
            String time = e.optString("time","09:00");
            Date when = new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.KOREA)
                .parse(date + " " + time);
            if (when == null) return;

            long at = when.getTime() - (minBefore * 60_000L);
            if (at <= System.currentTimeMillis()) return;

            AlarmManager am = (AlarmManager)getSystemService(ALARM_SERVICE);
            Intent i = new Intent(this, ReminderReceiver.class);
            i.putExtra("title", e.optString("title","일정 알림"));
            i.putExtra("text", "1시간 후 일정이 시작됩니다 · " + date + " " + time);
            int requestCode = Math.abs(e.optString("id").hashCode());

            PendingIntent pi = PendingIntent.getBroadcast(
                this, requestCode, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch(Exception ignored) {}
    }

    private void migrateAndScheduleExistingEvents() {
        boolean changed = false;
        for (int i=0; i<events.length(); i++) {
            JSONObject e = events.optJSONObject(i);
            if (e == null) continue;
            try {
                if (!e.has("reminderMin") || e.optInt("reminderMin",0) == 0) {
                    e.put("reminderMin",60);
                    changed = true;
                }
                if (e.optString("id").isEmpty()) {
                    e.put("id", UUID.randomUUID().toString());
                    changed = true;
                }
                scheduleReminder(e);
            } catch(Exception ignored) {}
        }
        if (changed) saveEvents();
    }

    private String copyImage(Uri uri) {
        try {
            File dir = new File(getFilesDir(),"attachments");
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir,"img_"+System.currentTimeMillis()+".jpg");
            InputStream in = getContentResolver().openInputStream(uri);
            FileOutputStream fos = new FileOutputStream(out);
            byte[] buf = new byte[8192];
            int n;
            while((n=in.read(buf))>0) fos.write(buf,0,n);
            in.close();
            fos.close();
            return out.getAbsolutePath();
        } catch(Exception e) {
            return null;
        }
    }

    private void loadEvents() {
        String s = getSharedPreferences(PREFS,MODE_PRIVATE).getString(KEY,"[]");
        try { events = new JSONArray(s); }
        catch(Exception e) { events = new JSONArray(); }
    }

    private void saveEvents() {
        getSharedPreferences(PREFS,MODE_PRIVATE)
            .edit().putString(KEY,events.toString()).apply();
    }
}
