package com.example.create_part2.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * 备忘录数据库帮助类（单例模式）
 * 该类继承自 SQLiteOpenHelper，负责数据库的创建、升级和管理
 * 提供备忘录的增删查改操作方法
 */
public class NoteDbHelper extends SQLiteOpenHelper {

    private static final String TAG = "NoteDbHelper";

    // 数据库信息
    private static final String DATABASE_NAME = "note_database.db";
    private static final int DATABASE_VERSION = 1;

    // 表名
    private static final String TABLE_NOTES = "notes";

    // 列名
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_TITLE = "title";
    private static final String COLUMN_CONTENT = "content";
    private static final String COLUMN_CREATED_TIME = "created_time";
    private static final String COLUMN_REMINDER_TIME = "reminder_time";

    // 创建表的 SQL 语句
    private static final String CREATE_NOTES_TABLE =
            "CREATE TABLE " + TABLE_NOTES + " (" +
                    COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COLUMN_TITLE + " TEXT NOT NULL, " +
                    COLUMN_CONTENT + " TEXT, " +
                    COLUMN_CREATED_TIME + " INTEGER NOT NULL, " +
                    COLUMN_REMINDER_TIME + " INTEGER DEFAULT 0" +
                    ")";

    // 单例模式相关
    private static NoteDbHelper instance;
    private static final Object lock = new Object();

    // 私有构造函数，防止外部直接实例化
    private NoteDbHelper(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    /**
     * 获取单例实例
     */
    public static NoteDbHelper getInstance(Context context) {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new NoteDbHelper(context);
                    Log.i(TAG, "数据库单例实例创建成功");
                }
            }
        }
        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        try {
            db.execSQL(CREATE_NOTES_TABLE);
            Log.i(TAG, "数据库表创建成功");
        } catch (Exception e) {
            Log.e(TAG, "创建数据库表失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        try {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_NOTES);
            onCreate(db);
            Log.i(TAG, "数据库升级成功，从版本 " + oldVersion + " 到 " + newVersion);
        } catch (Exception e) {
            Log.e(TAG, "数据库升级失败: " + e.getMessage(), e);
        }
    }

    /**
     * 插入备忘录
     */
    public long insertNote(Note note) {
        SQLiteDatabase db = null;
        try {
            db = this.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(COLUMN_TITLE, note.getTitle());
            values.put(COLUMN_CONTENT, note.getContent());
            values.put(COLUMN_CREATED_TIME, note.getCreatedTime());
            values.put(COLUMN_REMINDER_TIME, note.getReminderTime());

            long result = db.insert(TABLE_NOTES, null, values);
            Log.i(TAG, "插入备忘录，返回ID: " + result);
            return result;
        } catch (Exception e) {
            Log.e(TAG, "插入备忘录失败: " + e.getMessage(), e);
            return -1;
        }
        // 注意：不手动关闭数据库，让SQLiteOpenHelper管理连接
    }

    /**
     * 获取所有备忘录
     */
    public List<Note> getAllNotes() {
        List<Note> notes = new ArrayList<>();
        SQLiteDatabase db = null;
        Cursor cursor = null;

        try {
            db = this.getReadableDatabase();
            cursor = db.query(TABLE_NOTES, null, null, null, null, null,
                    COLUMN_CREATED_TIME + " DESC");

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    Note note = cursorToNote(cursor);
                    notes.add(note);
                } while (cursor.moveToNext());
            }

            Log.i(TAG, "获取到 " + notes.size() + " 条备忘录");
        } catch (Exception e) {
            Log.e(TAG, "获取所有备忘录失败: " + e.getMessage(), e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            // 注意：不手动关闭数据库，让SQLiteOpenHelper管理连接
        }

        return notes;
    }

    /**
     * 根据ID获取备忘录
     */
    public Note getNoteById(int id) {
        SQLiteDatabase db = null;
        Cursor cursor = null;

        try {
            db = this.getReadableDatabase();
            cursor = db.query(TABLE_NOTES, null, COLUMN_ID + "=?",
                    new String[]{String.valueOf(id)}, null, null, null);

            if (cursor != null && cursor.moveToFirst()) {
                return cursorToNote(cursor);
            }
        } catch (Exception e) {
            Log.e(TAG, "根据ID获取备忘录失败: " + e.getMessage(), e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return null;
    }

    /**
     * 获取所有带提醒的备忘录
     */
    public List<Note> getNotesWithReminders() {
        List<Note> notes = new ArrayList<>();
        SQLiteDatabase db = null;
        Cursor cursor = null;

        try {
            db = this.getReadableDatabase();
            cursor = db.query(TABLE_NOTES, null, COLUMN_REMINDER_TIME + " > 0",
                    null, null, null, COLUMN_REMINDER_TIME + " ASC");

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    Note note = cursorToNote(cursor);
                    notes.add(note);
                } while (cursor.moveToNext());
            }

            Log.i(TAG, "获取到 " + notes.size() + " 条带提醒的备忘录");
        } catch (Exception e) {
            Log.e(TAG, "获取带提醒的备忘录失败: " + e.getMessage(), e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return notes;
    }

    /**
     * 更新备忘录
     */
    public int updateNote(Note note) {
        SQLiteDatabase db = null;
        try {
            db = this.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put(COLUMN_TITLE, note.getTitle());
            values.put(COLUMN_CONTENT, note.getContent());
            values.put(COLUMN_REMINDER_TIME, note.getReminderTime());

            int result = db.update(TABLE_NOTES, values, COLUMN_ID + "=?",
                    new String[]{String.valueOf(note.getId())});
            Log.i(TAG, "更新备忘录，受影响行数: " + result);
            return result;
        } catch (Exception e) {
            Log.e(TAG, "更新备忘录失败: " + e.getMessage(), e);
            return 0;
        }
    }

    /**
     * 删除备忘录
     */
    public int deleteNote(int id) {
        SQLiteDatabase db = null;
        try {
            db = this.getWritableDatabase();
            int result = db.delete(TABLE_NOTES, COLUMN_ID + "=?",
                    new String[]{String.valueOf(id)});
            Log.i(TAG, "删除备忘录，受影响行数: " + result);
            return result;
        } catch (Exception e) {
            Log.e(TAG, "删除备忘录失败: " + e.getMessage(), e);
            return 0;
        }
    }

    /**
     * 搜索备忘录
     */
    public List<Note> searchNotes(String keyword) {
        List<Note> notes = new ArrayList<>();
        SQLiteDatabase db = null;
        Cursor cursor = null;

        try {
            db = this.getReadableDatabase();
            String selection = COLUMN_TITLE + " LIKE ? OR " + COLUMN_CONTENT + " LIKE ?";
            String[] selectionArgs = {"%" + keyword + "%", "%" + keyword + "%"};

            cursor = db.query(TABLE_NOTES, null, selection, selectionArgs,
                    null, null, COLUMN_CREATED_TIME + " DESC");

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    Note note = cursorToNote(cursor);
                    notes.add(note);
                } while (cursor.moveToNext());
            }

            Log.i(TAG, "搜索关键词 '" + keyword + "' 找到 " + notes.size() + " 条备忘录");
        } catch (Exception e) {
            Log.e(TAG, "搜索备忘录失败: " + e.getMessage(), e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return notes;
    }

    /**
     * 将 Cursor 转换为 Note 对象
     */
    private Note cursorToNote(Cursor cursor) {
        Note note = new Note();

        int idIndex = cursor.getColumnIndex(COLUMN_ID);
        int titleIndex = cursor.getColumnIndex(COLUMN_TITLE);
        int contentIndex = cursor.getColumnIndex(COLUMN_CONTENT);
        int createdTimeIndex = cursor.getColumnIndex(COLUMN_CREATED_TIME);
        int reminderTimeIndex = cursor.getColumnIndex(COLUMN_REMINDER_TIME);

        if (idIndex != -1) {
            note.setId(cursor.getInt(idIndex));
        }
        if (titleIndex != -1) {
            note.setTitle(cursor.getString(titleIndex));
        }
        if (contentIndex != -1) {
            note.setContent(cursor.getString(contentIndex));
        }
        if (createdTimeIndex != -1) {
            note.setCreatedTime(cursor.getLong(createdTimeIndex));
        }
        if (reminderTimeIndex != -1) {
            note.setReminderTime(cursor.getLong(reminderTimeIndex));
        }

        return note;
    }

    /**
     * 获取备忘录总数
     */
    public int getNotesCount() {
        SQLiteDatabase db = null;
        Cursor cursor = null;

        try {
            db = this.getReadableDatabase();
            cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_NOTES, null);

            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "获取备忘录总数失败: " + e.getMessage(), e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return 0;
    }

    /**
     * 手动关闭数据库连接（在应用退出时调用）
     */
    public synchronized void closeDatabase() {
        if (instance != null) {
            instance.close();
            instance = null;
            Log.i(TAG, "数据库连接已关闭");
        }
    }

    /**
     * 清空所有备忘录
     */
    public int deleteAllNotes() {
        SQLiteDatabase db = null;
        try {
            db = this.getWritableDatabase();
            int result = db.delete(TABLE_NOTES, null, null);
            Log.i(TAG, "清空数据库，删除了 " + result + " 条备忘录");
            return result;
        } catch (Exception e) {
            Log.e(TAG, "清空数据库失败: " + e.getMessage(), e);
            return 0;
        }
    }
}