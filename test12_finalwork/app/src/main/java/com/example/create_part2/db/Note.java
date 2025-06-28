package com.example.create_part2.db;

public class Note {

    private int id;
    private String title;
    private String content;
    private long createdTime;
    private long reminderTime; // 提醒时间，0表示没有提醒

    public Note() {
        this.createdTime = System.currentTimeMillis();
        this.reminderTime = 0;
    }

    public Note(String title, String content) {
        this.title = title;
        this.content = content;
        this.createdTime = System.currentTimeMillis();
        this.reminderTime = 0;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(long createdTime) {
        this.createdTime = createdTime;
    }

    public long getReminderTime() {
        return reminderTime;
    }

    public void setReminderTime(long reminderTime) {
        this.reminderTime = reminderTime;
    }
    private boolean isReminderTriggered = false; // 提醒是否已触发

    public boolean isReminderTriggered() {
        return isReminderTriggered;
    }

    public void setReminderTriggered(boolean reminderTriggered) {
        isReminderTriggered = reminderTriggered;
    }
    @Override
    public String toString() {
        return "Note{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", content='" + content + '\'' +
                ", createdTime=" + createdTime +
                ", reminderTime=" + reminderTime +
                '}';
    }
}
