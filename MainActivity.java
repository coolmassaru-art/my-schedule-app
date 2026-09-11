package com.coolmassaru.myschedule;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import org.json.*;

import java.io.*;
import java.text.*;
import java.util.*;

public class MainActivity extends Activity {
    private LinearLayout list;
    private TextView dateTitle;
    private Calendar selected = Calendar.getInstance();
    private JSONArray events = new JSONArray();
    private final String PREFS = "schedule_prefs";
    private final String KEY = "events";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        loadEvents();
        buildUi();
        handleShare(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleShare(intent);
    }

    private TextView tv(String text, int sp, boolean bold) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(sp);
        v.setTextColor(Color.rgb(25, 28, 35));
        if (bold) v.setTypeface(null, android.graphics.Typeface.BOLD);
        return v;
    }

    private int dp(int x) {
        return (int)(x * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(20), dp(18), dp(18));
        root.setBackgroundColor(Color.rgb(247,248,252));

        TextView title = tv("나만의 일정", 28, true);
        root.addView(title);

        TextView desc = tv("카카오톡 메시지나 스크린샷을 공유해서 일정으로 저장할 수 있어요.", 14, false);
        desc.setTextColor(Color.DKGRAY);
        LinearLayout.LayoutParams dp1 = new LinearLayout.LayoutParams(-1, -2);
        dp1.setMargins(0, dp(6), 0, dp(16));
        root.addView(desc, dp1);

        Button dateButton = new Button(this);
        dateButton.setText("날짜 선택");
        dateButton.setOnClickListener(v -> chooseDate());
        root.addView(dateButton);

        dateTitle = tv("", 20, true);
        LinearLayout.LayoutParams dpp = new LinearLayout.LayoutParams(-1, -2);
        dpp.setMargins(0, dp(18), 0, dp(8));
        root.addView(dateTitle, dpp);

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        Button add = new Button(this);
        add.setText("＋ 일정 추가");
        add.setTextSize(17);
        add.setOnClickListener(v -> openEditor("", "", new ArrayList<>()));
        root.addView(add);

        setContentView(root);
        refresh();
    }

    private void chooseDate() {
        DatePickerDialog dlg = new DatePickerDialog(
            this,
            (view, y, m, d) -> {
                selected.set(Calendar.YEAR, y);
                selected.set(Calendar.MONTH, m);
                selected.set(Calendar.DAY_OF_MONTH, d);
                refresh();
            },
            selected.get(Calendar.YEAR),
            selected.get(Calendar.MONTH),
            selected.get(Calendar.DAY_OF_MONTH)
        );
        dlg.show();
    }

    private String dayKey(Calendar c) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(c.getTime());
    }

    private void refresh() {
        dateTitle.setText(new SimpleDateFormat("M월 d일 EEEE", Locale.KOREA).format(selected.getTime()));
        list.removeAllViews();
        boolean any = false;

        for (int i = 0; i < events.length(); i++) {
            JSONObject e = events.optJSONObject(i);
            if (e == null) continue;
            if (!dayKey(selected).equals(e.optString("date"))) continue;
            any = true;

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(12), dp(14), dp(12));
            card.setBackgroundColor(Color.WHITE);

            TextView t = tv(e.optString("title", "일정"), 17, true);
            card.addView(t);

            String meta = e.optString("time", "") + " · " + e.optString("category", "개인");
            TextView m = tv(meta, 13, false);
            m.setTextColor(Color.GRAY);
            card.addView(m);

            String memo = e.optString("memo", "");
            if (!memo.isEmpty()) {
                TextView mm = tv(memo, 14, false);
                LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, -2);
                mp.setMargins(0, dp(5), 0, 0);
                card.addView(mm, mp);
            }

            JSONArray imgs = e.optJSONArray("images");
            if (imgs != null && imgs.length() > 0) {
                TextView at = tv("📎 첨부 스크린샷 " + imgs.length() + "장", 13, false);
                at.setTextColor(Color.rgb(88,101,242));
                card.addView(at);
            }

            int idx = i;
            card.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this)
                    .setTitle("일정 삭제")
                    .setMessage("이 일정을 삭제할까요?")
                    .setNegativeButton("취소", null)
                    .setPositiveButton("삭제", (d,w) -> {
                        JSONArray n = new JSONArray();
                        for (int j=0;j<events.length();j++) if (j != idx) n.put(events.opt(j));
                        events = n;
                        saveEvents();
                        refresh();
                    }).show();
                return true;
            });

            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
            cp.setMargins(0, 0, 0, dp(10));
            list.addView(card, cp);
        }

        if (!any) {
            TextView empty = tv("등록된 일정이 없습니다.\n아래의 ‘일정 추가’를 눌러보세요.", 15, false);
            empty.setGravity(Gravity.CENTER);
            empty.setTextColor(Color.GRAY);
            empty.setPadding(0, dp(40), 0, dp(40));
            list.addView(empty);
        }
    }

    private void openEditor(String sharedText, String defaultTitle, ArrayList<String> images) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), 0, dp(18), 0);

        EditText title = new EditText(this);
        title.setHint("일정 제목");
        title.setText(defaultTitle);
        box.addView(title);

        EditText time = new EditText(this);
        time.setHint("시간 예: 15:30");
        time.setText("09:00");
        box.addView(time);

        Spinner category = new Spinner(this);
        String[] cats = {"개인","가족","아이","운동","가게","기타"};
        category.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, cats));
        box.addView(category);

        EditText memo = new EditText(this);
        memo.setHint("메모 / 카카오톡 원문");
        memo.setMinLines(3);
        memo.setText(sharedText);
        box.addView(memo);

        TextView attach = tv(images.isEmpty() ? "" : "첨부 스크린샷 " + images.size() + "장", 13, false);
        attach.setTextColor(Color.rgb(88,101,242));
        box.addView(attach);

        new AlertDialog.Builder(this)
            .setTitle(images.isEmpty() && sharedText.isEmpty() ? "새 일정" : "공유받은 내용을 일정으로 저장")
            .setView(box)
            .setNegativeButton("취소", null)
            .setPositiveButton("저장", (d,w) -> {
                String tt = title.getText().toString().trim();
                if (tt.isEmpty()) tt = "새 일정";
                JSONObject e = new JSONObject();
                try {
                    e.put("title", tt);
                    e.put("date", dayKey(selected));
                    e.put("time", time.getText().toString().trim());
                    e.put("category", cats[category.getSelectedItemPosition()]);
                    e.put("memo", memo.getText().toString().trim());
                    JSONArray a = new JSONArray();
                    for (String s: images) a.put(s);
                    e.put("images", a);
                    events.put(e);
                    saveEvents();
                    refresh();
                } catch (JSONException ignored) {}
            }).show();
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
                String first = text.trim().isEmpty() ? "카카오톡에서 가져온 일정" : text.trim().split("\\n")[0];
                openEditor(text, first, new ArrayList<>());
            } else if (type.startsWith("image/")) {
                ArrayList<String> imgs = new ArrayList<>();
                Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (uri != null) {
                    String saved = copyImage(uri);
                    if (saved != null) imgs.add(saved);
                }
                openEditor("카카오톡에서 공유한 스크린샷", "카카오톡 스크린샷 일정", imgs);
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type.startsWith("image/")) {
            ArrayList<String> imgs = new ArrayList<>();
            ArrayList<Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (uris != null) for (Uri u: uris) {
                String saved = copyImage(u);
                if (saved != null) imgs.add(saved);
            }
            openEditor("카카오톡에서 공유한 스크린샷", "카카오톡 스크린샷 일정", imgs);
        }
    }

    private String copyImage(Uri uri) {
        try {
            File dir = new File(getFilesDir(), "attachments");
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, "img_" + System.currentTimeMillis() + ".jpg");
            InputStream in = getContentResolver().openInputStream(uri);
            FileOutputStream fos = new FileOutputStream(out);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
            in.close(); fos.close();
            return out.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    private void loadEvents() {
        String s = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY, "[]");
        try { events = new JSONArray(s); }
        catch (JSONException e) { events = new JSONArray(); }
    }

    private void saveEvents() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY, events.toString()).apply();
    }
}
