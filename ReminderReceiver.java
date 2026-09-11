package com.coolmassaru.myschedule;

import android.app.*;
import android.content.*;
import android.os.Build;

public class ReminderReceiver extends BroadcastReceiver {
    public static final String CHANNEL_ID = "schedule_reminders";

    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationManager nm =
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID,
                "일정 알림",
                NotificationManager.IMPORTANCE_HIGH
            );
            ch.setDescription("등록한 일정 시작 전 알림");
            nm.createNotificationChannel(ch);
        }

        String title = intent.getStringExtra("title");
        String text = intent.getStringExtra("text");
        int id = intent.getIntExtra("id", (int) System.currentTimeMillis());

        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? new Notification.Builder(context, CHANNEL_ID)
            : new Notification.Builder(context);

        b.setSmallIcon(android.R.drawable.ic_dialog_info)
         .setContentTitle(title == null ? "일정 알림" : title)
         .setContentText(text == null ? "곧 일정이 시작됩니다." : text)
         .setAutoCancel(true);

        nm.notify(id, b.build());
    }
}
