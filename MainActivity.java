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
    private CalendarView calendarView;
    private LinearLayout eventList;
    private TextView selectedTitle;
    private Calendar selected = Calendar.getInstance();
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

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(16));
        root.setBackgroundColor(Color.rgb(247,248,252));

        TextView title = tv("나만의 일정", 28, true);
        root.addView(title);

        TextView sub = tv("카카오톡·문자 메시지·스크린샷을 일정으로 바로 저장 · 기본 1시간 전 알림", 14, false);
        sub.setTextColor(Color.GRAY);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1,-2);
        sp.setMargins(0, dp(5),0,dp(12));
        root.addView(sub, sp);

        calendarView = new CalendarView(this);
        calendarView.setFirstDayOfWeek(Calendar.MONDAY);
        calendarView.setDate(selected.getTimeInMillis(), false, true);
        calendarView.setOnDateChangeListener((view, y, m, d) -> {
            selected.set(y,m,d,0,0,0);
            refresh();
        });
        root.addView(calendarView, new LinearLayout.LayoutParams(-1, dp(300)));

        selectedTitle = tv("", 20, true);
        LinearLayout.LayoutParams stp = new LinearLayout.LayoutParams(-1,-2);
        stp.setMargins(0, dp(14),0,dp(8));
        root.addView(selectedTitle, stp);

        ScrollView scroll = new ScrollView(this);
        eventList = new LinearLayout(this);
        eventList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(eventList);
        root.addView(scroll, new LinearLayout.LayoutParams(-1,0,1f));

        Button add = new Button(this);
        add.setText("＋ 일정 추가");
        add.setTextSize(16);
        add.setOnClickListener(v -> openEditor(null, "", new ArrayList<>(), false));
        root.addView(add);

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
            memo.setText(incomingText);

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
        Date date = null;
    }

    private ParsedSchedule parseScheduleText(String text) {
        ParsedSchedule p = new ParsedSchedule();
        if (text == null) text = "";
        String clean = text.replace('\u00A0',' ').trim();
        String[] lines = clean.split("\\n");
        Calendar now = Calendar.getInstance();

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
            String ap = range.group(1);
            if ("오후".equals(ap) && h < 12) h += 12;
            if ("오전".equals(ap) && h == 12) h = 0;
            p.time = String.format(Locale.KOREA,"%02d:00",h);
            return p;
        }

        // "오후 4시", "오전 10시 30분"
        Matcher natural = Pattern.compile("(오전|오후)\\s*(\\d{1,2})\\s*시\\s*(\\d{1,2})?\\s*분?").matcher(clean);
        if (natural.find()) {
            int h = Integer.parseInt(natural.group(2));
            int min = natural.group(3) == null ? 0 : Integer.parseInt(natural.group(3));
            if ("오후".equals(natural.group(1)) && h < 12) h += 12;
            if ("오전".equals(natural.group(1)) && h == 12) h = 0;
            p.time = String.format(Locale.KOREA,"%02d:%02d",h,min);
            return p;
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
