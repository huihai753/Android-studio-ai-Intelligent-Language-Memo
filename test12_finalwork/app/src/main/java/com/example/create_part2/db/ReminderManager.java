package com.example.create_part2.db;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 提醒管理器
 */
public class ReminderManager {

    private static final String TAG = "ReminderManager";
    private static final String ACTION_REMINDER = "com.example.create_part2.ACTION_REMINDER";

    private Context context;
    private AlarmManager alarmManager;

    public ReminderManager(Context context) {
        this.context = context;
        this.alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    }

    /**
     * 设置提醒
     */
    public void setReminder(Note note) {
        if (note.getReminderTime() <= 0) {
            Log.d(TAG, "No reminder set for note: " + note.getId());
            return;
        }

        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ACTION_REMINDER);
        intent.putExtra("noteId", note.getId());
        intent.putExtra("title", note.getTitle());
        intent.putExtra("content", note.getContent());

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                note.getId(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // 设置闹钟
        if (alarmManager != null) {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    note.getReminderTime(),
                    pendingIntent
            );
            Log.d(TAG, "Reminder set for note: " + note.getId() +
                    " at time: " + note.getReminderTime());
        }
    }

    /**
     * 取消提醒
     */
    public void cancelReminder(int noteId) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ACTION_REMINDER);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                noteId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
            Log.d(TAG, "Reminder cancelled for note: " + noteId);
        }
    }
    /**
     * 提醒回调接口
     */
    public interface ReminderCallback {
        void onReminderTriggered(int noteId, String title, String content);
    }

    private ReminderCallback reminderCallback;

    /**
     * 设置提醒回调
     */
    public void setReminderCallback(ReminderCallback callback) {
        this.reminderCallback = callback;
    }

    /**
     * 获取提醒回调（供ReminderReceiver使用）
     */
    public ReminderCallback getReminderCallback() {
        return reminderCallback;
    }
}