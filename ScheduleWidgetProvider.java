package com.coolmassaru.myschedule;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;

public class ScheduleWidgetProvider extends AppWidgetProvider {
    private static final String PREFS = "schedule_prefs_v2";
    private static final String KEY = "events";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int id : appWidgetIds) updateWidget(context, manager, id);
    }

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        android.content.ComponentName name = new android.content.ComponentName(context, ScheduleWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(name);
        for (int id : ids) updateWidget(context, manager, id);
    }

    static void updateWidget(Context context, AppWidgetManager manager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_schedule);

        Calendar today = Calendar.getInstance();
        Calendar tomorrow = (Calendar) today.clone();
        tomorrow.add(Calendar.DAY_OF_MONTH, 1);

        SimpleDateFormat keyFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA);
        SimpleDateFormat titleFmt = new SimpleDateFormat("M월 d일 EEEE", Locale.KOREA);
        String todayKey = keyFmt.format(today.getTime());
        String tomorrowKey = keyFmt.format(tomorrow.getTime());

        views.setTextViewText(R.id.widget_title, "오늘 일정");
        views.setTextViewText(R.id.widget_date, titleFmt.format(today.getTime()));
        views.setTextViewText(R.id.widget_body, buildAgenda(context, todayKey, tomorrowKey));

        Intent open = new Intent(context, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.widget_root, pi);

        manager.updateAppWidget(appWidgetId, views);
    }

    private static String buildAgenda(Context context, String todayKey, String tomorrowKey) {
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]");
        ArrayList<JSONObject> today = new ArrayList<>();
        ArrayList<JSONObject> tomorrow = new ArrayList<>();

        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject e = arr.optJSONObject(i);
                if (e == null) continue;
                String d = e.optString("date");
                if (todayKey.equals(d)) today.add(e);
                else if (tomorrowKey.equals(d)) tomorrow.add(e);
            }
        } catch(Exception ignored) {}

        Comparator<JSONObject> byTime = (a,b) -> a.optString("time","99:99").compareTo(b.optString("time","99:99"));
        Collections.sort(today, byTime);
        Collections.sort(tomorrow, byTime);

        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (JSONObject e : today) {
            if (shown >= 4) break;
            if (shown > 0) sb.append("\n");
            sb.append("• ").append(e.optString("time","--:--")).append("  ").append(e.optString("title","일정"));
            shown++;
        }

        if (today.isEmpty()) sb.append("오늘 등록된 일정이 없습니다.");

        if (!tomorrow.isEmpty() && shown < 5) {
            sb.append("\n\n내일");
            int count = 0;
            for (JSONObject e : tomorrow) {
                if (count >= 2) break;
                sb.append("\n• ").append(e.optString("time","--:--")).append("  ").append(e.optString("title","일정"));
                count++;
            }
        }
        return sb.toString();
    }
}
