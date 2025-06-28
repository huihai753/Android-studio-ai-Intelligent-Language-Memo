package com.example.create_part2.db;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 备忘录数据仓库 - 优化版本
 * 该类是 Repository 层，负责管理数据源，为 ViewModel 提供统一的数据访问接口
 * 通过调用 NoteDbHelper 的方法，实现数据的增删查改操作
 *
 * 主要优化：
 * 1. 添加缓存机制，提高性能
 * 2. 增强错误处理和日志记录
 * 3. 改进线程管理和资源控制
 * 4. 添加数据一致性保证
 */
public class NoteRepository {

    private static final String TAG = "NoteRepository";
    private static volatile NoteRepository INSTANCE;

    private NoteDbHelper dbHelper;
    private MutableLiveData<List<Note>> allNotesLiveData;
    private ExecutorService executor;
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private final AtomicBoolean isDestroyed = new AtomicBoolean(false);

    // 缓存最近查询的单个备忘录，避免重复数据库查询
    private volatile Note lastQueriedNote;
    private volatile int lastQueriedNoteId = -1;

    public NoteRepository(Application application) {
        initializeRepository(application);
    }

    /**
     * 获取单例实例
     */
    public static NoteRepository getInstance(Application application) {
        if (INSTANCE == null) {
            synchronized (NoteRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new NoteRepository(application);
                }
            }
        }
        return INSTANCE;
    }

    /**
     * 初始化仓库
     */
    private void initializeRepository(Application application) {
        try {
            // 确保使用 application context
            dbHelper = NoteDbHelper.getInstance(application.getApplicationContext());
            allNotesLiveData = new MutableLiveData<>();

            executor = Executors.newFixedThreadPool(
                    Math.max(2, Math.min(Runtime.getRuntime().availableProcessors(), 4))
            );

            // 立即加载数据，确保界面能显示
            loadAllNotesAsync();
            isInitialized.set(true);

            Log.i(TAG, "NoteRepository 初始化成功");
        } catch (Exception e) {
            Log.e(TAG, "NoteRepository 初始化失败: " + e.getMessage(), e);
            isInitialized.set(false);
        }
    }
    /**
     * 检查仓库是否可用
     */
    private boolean isRepositoryAvailable() {
        if (isDestroyed.get()) {
            Log.w(TAG, "Repository 已被销毁，操作被忽略");
            return false;
        }
        if (!isInitialized.get()) {
            Log.w(TAG, "Repository 尚未初始化完成");
            return false;
        }
        return true;
    }

    /**
     * 获取所有备忘录的 LiveData
     */
    public LiveData<List<Note>> getAllNotes() {
        if (!isRepositoryAvailable()) {
            // 返回空数据而不是null，避免UI层空指针异常
            MutableLiveData<List<Note>> emptyLiveData = new MutableLiveData<>();
            emptyLiveData.setValue(new ArrayList<>());
            return emptyLiveData;
        }
        return allNotesLiveData;
    }

    /**
     * 根据ID获取备忘录 - 优化版本
     */
    public LiveData<Note> getNoteById(int id) {
        MutableLiveData<Note> noteLiveData = new MutableLiveData<>();

        if (!isRepositoryAvailable()) {
            noteLiveData.setValue(null);
            return noteLiveData;
        }

        // 检查缓存
        if (lastQueriedNoteId == id && lastQueriedNote != null) {
            Log.d(TAG, "从缓存返回备忘录 ID: " + id);
            noteLiveData.setValue(lastQueriedNote);
            return noteLiveData;
        }

        executor.execute(() -> {
            try {
                Note note = dbHelper.getNoteById(id);

                // 更新缓存
                if (note != null) {
                    lastQueriedNote = note;
                    lastQueriedNoteId = id;
                    Log.d(TAG, "成功获取备忘录 ID: " + id + ", 标题: " + note.getTitle());
                } else {
                    Log.w(TAG, "未找到 ID 为 " + id + " 的备忘录");
                }

                noteLiveData.postValue(note);
            } catch (Exception e) {
                Log.e(TAG, "获取备忘录失败 ID: " + id + ", 错误: " + e.getMessage(), e);
                noteLiveData.postValue(null);
            }
        });
        return noteLiveData;
    }

    /**
     * 获取所有带提醒的备忘录
     */
    public LiveData<List<Note>> getNotesWithReminders() {
        MutableLiveData<List<Note>> reminderNotesLiveData = new MutableLiveData<>();

        if (!isRepositoryAvailable()) {
            reminderNotesLiveData.setValue(new ArrayList<>());
            return reminderNotesLiveData;
        }

        executor.execute(() -> {
            try {
                List<Note> reminderNotes = dbHelper.getNotesWithReminders();
                if (reminderNotes == null) {
                    reminderNotes = new ArrayList<>();
                }

                Log.d(TAG, "获取到 " + reminderNotes.size() + " 条带提醒的备忘录");
                reminderNotesLiveData.postValue(reminderNotes);
            } catch (Exception e) {
                Log.e(TAG, "获取提醒备忘录失败: " + e.getMessage(), e);
                reminderNotesLiveData.postValue(new ArrayList<>());
            }
        });
        return reminderNotesLiveData;
    }

    /**
     * 插入备忘录 - 优化版本
     */
    public Future<Boolean> insert(Note note) {
        if (!isRepositoryAvailable()) {
            Log.w(TAG, "Repository 不可用，插入操作被取消");
            return null;
        }

        if (note == null || note.getTitle() == null || note.getTitle().trim().isEmpty()) {
            Log.w(TAG, "备忘录数据无效，插入操作被取消");
            return null;
        }

        return executor.submit(() -> {
            try {
                long result = dbHelper.insertNote(note);
                if (result != -1) {
                    Log.i(TAG, "备忘录插入成功，ID: " + result + ", 标题: " + note.getTitle());

                    // 清除缓存，确保数据一致性
                    clearCache();

                    // 异步刷新数据
                    loadAllNotesAsync();
                    return true;
                } else {
                    Log.e(TAG, "备忘录插入失败，标题: " + note.getTitle());
                    return false;
                }
            } catch (Exception e) {
                Log.e(TAG, "插入备忘录时发生错误: " + e.getMessage(), e);
                return false;
            }
        });
    }

    /**
     * 更新备忘录 - 优化版本
     */
    public Future<Boolean> update(Note note) {
        if (!isRepositoryAvailable()) {
            Log.w(TAG, "Repository 不可用，更新操作被取消");
            return null;
        }

        if (note == null || note.getId() <= 0) {
            Log.w(TAG, "备忘录数据无效，更新操作被取消");
            return null;
        }

        return executor.submit(() -> {
            try {
                int result = dbHelper.updateNote(note);
                if (result > 0) {
                    Log.i(TAG, "备忘录更新成功，ID: " + note.getId() + ", 受影响行数: " + result);

                    // 更新缓存
                    if (lastQueriedNoteId == note.getId()) {
                        lastQueriedNote = note;
                    }

                    // 异步刷新数据
                    loadAllNotesAsync();
                    return true;
                } else {
                    Log.e(TAG, "备忘录更新失败，ID: " + note.getId());
                    return false;
                }
            } catch (Exception e) {
                Log.e(TAG, "更新备忘录时发生错误: " + e.getMessage(), e);
                return false;
            }
        });
    }

    /**
     * 删除备忘录 - 优化版本
     */
    /**
     * 删除备忘录 - 优化版本
     */
    public Future<Boolean> delete(Note note) {
        if (!isRepositoryAvailable()) {
            Log.w(TAG, "Repository 不可用，删除操作被取消");
            return null;
        }

        if (note == null || note.getId() <= 0) {
            Log.w(TAG, "备忘录数据无效，删除操作被取消");
            return null;
        }

        return executor.submit(() -> {
            try {
                // 如果有提醒，先取消提醒
                if (note.getReminderTime() > 0) {
                    // 注意：这里需要传入Context，可能需要修改Repository的构造函数来保存Context引用
                    // 或者在调用delete的地方处理提醒取消
                    Log.d(TAG, "备忘录有提醒，需要在调用方取消提醒");
                }

                int result = dbHelper.deleteNote(note.getId());
                if (result > 0) {
                    Log.i(TAG, "备忘录删除成功，ID: " + note.getId() + ", 受影响行数: " + result);

                    // 清除相关缓存
                    if (lastQueriedNoteId == note.getId()) {
                        clearCache();
                    }

                    // 异步刷新数据
                    loadAllNotesAsync();
                    return true;
                } else {
                    Log.e(TAG, "备忘录删除失败，ID: " + note.getId());
                    return false;
                }
            } catch (Exception e) {
                Log.e(TAG, "删除备忘录时发生错误: " + e.getMessage(), e);
                return false;
            }
        });
    }

    /**
     * 根据关键词搜索备忘录
     */
    public LiveData<List<Note>> searchNotes(String keyword) {
        MutableLiveData<List<Note>> searchResultLiveData = new MutableLiveData<>();

        if (!isRepositoryAvailable()) {
            searchResultLiveData.setValue(new ArrayList<>());
            return searchResultLiveData;
        }

        if (keyword == null || keyword.trim().isEmpty()) {
            Log.w(TAG, "搜索关键词为空，返回空结果");
            searchResultLiveData.setValue(new ArrayList<>());
            return searchResultLiveData;
        }

        executor.execute(() -> {
            try {
                List<Note> searchResults = dbHelper.searchNotes(keyword.trim());
                if (searchResults == null) {
                    searchResults = new ArrayList<>();
                }

                Log.d(TAG, "搜索关键词 '" + keyword + "' 找到 " + searchResults.size() + " 条结果");
                searchResultLiveData.postValue(searchResults);
            } catch (Exception e) {
                Log.e(TAG, "搜索备忘录失败，关键词: " + keyword + ", 错误: " + e.getMessage(), e);
                searchResultLiveData.postValue(new ArrayList<>());
            }
        });
        return searchResultLiveData;
    }

    /**
     * 异步加载所有备忘录数据
     */
    private void loadAllNotesAsync() {
        if (!isRepositoryAvailable()) {
            Log.w(TAG, "Repository 不可用，跳过数据加载");
            return;
        }

        Log.d(TAG, "开始异步加载所有备忘录");
        executor.execute(() -> {
            try {
                List<Note> notes = dbHelper.getAllNotes();
                if (notes == null) {
                    notes = new ArrayList<>();
                    Log.w(TAG, "数据库返回 null，使用空列表");
                }

                Log.d(TAG, "从数据库加载了 " + notes.size() + " 条备忘录");

                // 确保在主线程更新 LiveData
                allNotesLiveData.postValue(notes);
                Log.d(TAG, "LiveData 已更新");
            } catch (Exception e) {
                Log.e(TAG, "加载所有备忘录失败: " + e.getMessage(), e);
                allNotesLiveData.postValue(new ArrayList<>());
            }
        });
    }

    /**
     * 清除缓存
     */
    private void clearCache() {
        lastQueriedNote = null;
        lastQueriedNoteId = -1;
        Log.d(TAG, "缓存已清除");
    }

    /**
     * 获取备忘录总数
     */
    public Future<Integer> getNotesCount() {
        if (!isRepositoryAvailable()) {
            return null;
        }

        return executor.submit(() -> {
            try {
                int count = dbHelper.getNotesCount();
                Log.d(TAG, "当前备忘录总数: " + count);
                return count;
            } catch (Exception e) {
                Log.e(TAG, "获取备忘录总数失败: " + e.getMessage(), e);
                return 0;
            }
        });
    }

    /**
     * 强制刷新所有数据
     */
    public void refreshAllData() {
        if (!isRepositoryAvailable()) {
            return;
        }

        Log.d(TAG, "强制刷新所有数据");
        clearCache();
        loadAllNotesAsync();
    }

    /**
     * 关闭资源 - 增强版本
     */
    public void close() {
        Log.i(TAG, "开始关闭 NoteRepository 资源");

        isDestroyed.set(true);

        if (executor != null && !executor.isShutdown()) {
            try {
                executor.shutdown();
                Log.d(TAG, "线程池已关闭");
            } catch (Exception e) {
                Log.w(TAG, "关闭线程池时发生异常: " + e.getMessage());
            }
        }

        if (dbHelper != null) {
            try {
                dbHelper.close();
                Log.d(TAG, "数据库连接已关闭");
            } catch (Exception e) {
                Log.w(TAG, "关闭数据库连接时发生异常: " + e.getMessage());
            }
        }

        // 清理静态引用，避免内存泄漏
        if (INSTANCE == this) {
            INSTANCE = null;
        }

        clearCache();
        Log.i(TAG, "NoteRepository 资源关闭完成");
    }

    /**
     * 检查仓库健康状态
     */
    public boolean isHealthy() {
        return isInitialized.get() && !isDestroyed.get() &&
                executor != null && !executor.isShutdown() &&
                dbHelper != null;
    }
}
