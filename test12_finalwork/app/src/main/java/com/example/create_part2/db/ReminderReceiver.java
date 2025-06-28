package com.example.create_part2.db;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class ReminderReceiver extends BroadcastReceiver {
    private static final String TAG = "ReminderReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if ("com.example.create_part2.ACTION_REMINDER".equals(action)) {
            int noteId = intent.getIntExtra("noteId", -1);
            String title = intent.getStringExtra("title");
            String content = intent.getStringExtra("content");

            Log.d(TAG, "收到提醒: noteId=" + noteId + ", title=" + title);

            // 通知MainActivity处理提醒
            Intent reminderIntent = new Intent("com.example.create_part2.REMINDER_TRIGGERED");
            reminderIntent.putExtra("noteId", noteId);
            reminderIntent.putExtra("title", title);
            reminderIntent.putExtra("content", content);
            context.sendBroadcast(reminderIntent);
        }
    }
}