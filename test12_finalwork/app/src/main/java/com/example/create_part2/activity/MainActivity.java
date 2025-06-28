package com.example.create_part2.activity;

import android.os.Handler;
import android.os.Looper;
import java.util.Set;
import java.util.HashSet;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.core.view.GravityCompat;

import android.content.BroadcastReceiver;
import android.content.Context;

import android.content.IntentFilter;

import com.example.create_part2.MicrosoftTTS;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.create_part2.db.Note;
import com.example.create_part2.db.NoteAdapter;
import com.example.create_part2.db.NoteDbHelper;
import com.example.create_part2.db.NoteDetailActivity;
import com.example.create_part2.db.NoteViewModel;
import com.example.create_part2.R;
import com.example.create_part2.activity_test.STTActivity;
import com.example.create_part2.activity_test.TTSActivity;
import com.example.create_part2.activity_test.ChatActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import com.example.create_part2.MicrosoftTTS;
import android.view.Gravity;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.content.Context;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import com.example.create_part2.db.ReminderManager;
public class MainActivity extends AppCompatActivity {

    public static final int ADD_NOTE_REQUEST = 1;
    public static final int EDIT_NOTE_REQUEST = 2;
    private static final String TAG = "MainActivity";

    private NoteViewModel noteViewModel;
    private RecyclerView recyclerView;
    private View textViewEmpty;
    private DrawerLayout drawerLayout;
    private NoteDbHelper dbHelper;
    private ReminderManager reminderManager; // 添加提醒管理器
    private BroadcastReceiver reminderBroadcastReceiver;
    private Handler refreshHandler;
    private Runnable refreshRunnable;
    private static final int REFRESH_INTERVAL = 5000; // 30秒检查一次
    // 记录已经播报过TTS的备忘录ID，避免重复播报
    private Set<Integer> playedTTSNotes = new HashSet<>();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);
        // 在onCreate方法中添加这一行
        reminderManager = new ReminderManager(this);
        // 初始化数据库
        initDatabase();
        // 注册提醒广播接收器
        registerReminderReceiver();
        // 启动实时刷新检查
        startRealTimeRefresh();
        drawerLayout = findViewById(R.id.drawer_layout);

        // 初始化空视图
        textViewEmpty = findViewById(R.id.text_view_empty);

        // 添加备忘录按钮
        FloatingActionButton buttonAddNote = findViewById(R.id.button_add_note);
        buttonAddNote.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, NoteDetailActivity.class);
            startActivityForResult(intent, ADD_NOTE_REQUEST);
        });

        // AI助手按钮 - 跳转到分屏聊天页面
        FloatingActionButton aiAssistantButton = findViewById(R.id.button_ai_assistant);
        if (aiAssistantButton != null) {
            aiAssistantButton.setOnClickListener(v -> {
                Log.d(TAG, "AI助手按钮被点击，跳转到分屏聊天页面");
                Intent intent = new Intent(MainActivity.this, ChatWithNotesActivity.class);
                startActivity(intent);
            });
        }

        // 右上角菜单按钮，点击打开侧边栏
        findViewById(R.id.button_test).setOnClickListener(v -> {
            drawerLayout.openDrawer(GravityCompat.START);
        });

        // 侧边栏按钮事件处理
        setupSideMenuClickListeners();

        // 设置RecyclerView
        setupRecyclerView();

        // 获取ViewModel并观察数据变化
        setupViewModel();
    }

    /**
     * 设置侧边栏按钮点击事件
     */
    private void setupSideMenuClickListeners() {
        if (drawerLayout == null) {
            return;
        }

        // 查看数据库
        LinearLayout debugButton = findViewById(R.id.debug_button);
        if (debugButton != null) {
            debugButton.setOnClickListener(v -> {
                showDatabaseContent();
                drawerLayout.closeDrawer(GravityCompat.START);
            });
        }

        // AI聊天助手
        LinearLayout chatButton = findViewById(R.id.button_chat);
        if (chatButton != null) {
            chatButton.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, ChatActivity.class);
                startActivity(intent);
                drawerLayout.closeDrawer(GravityCompat.START);
            });
        }

        // TTS测试按钮
        LinearLayout ttsTestButton = findViewById(R.id.button_tts_test);
        if (ttsTestButton != null) {
            ttsTestButton.setOnClickListener(v -> {
                showTTSTestDialog();
                drawerLayout.closeDrawer(GravityCompat.START);
            });
        }

        // 语音识别
        LinearLayout speechButton = findViewById(R.id.button_speech);
        if (speechButton != null) {
            speechButton.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, STTActivity.class);
                startActivity(intent);
                drawerLayout.closeDrawer(GravityCompat.START);
            });
        }

        // 清空数据库
        LinearLayout clearDatabaseButton = findViewById(R.id.button_clear_database);
        if (clearDatabaseButton != null) {
            clearDatabaseButton.setOnClickListener(v -> {
                showClearDatabaseDialog();
            });
        }

        // 导出数据
        LinearLayout exportDataButton = findViewById(R.id.button_export_data);
        if (exportDataButton != null) {
            exportDataButton.setOnClickListener(v -> {
                showExportDataDialog();
                drawerLayout.closeDrawer(GravityCompat.START);
            });
        }

        // API配置
        LinearLayout configButton = findViewById(R.id.button_config);
        if (configButton != null) {
            configButton.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, ConfigActivity.class);
                startActivity(intent);
                drawerLayout.closeDrawer(GravityCompat.START);
            });
        }
    }

    /**
     * 显示数据库内容
     */
    private void showDatabaseContent() {
        Log.d(TAG, "查看数据库按钮被点击");

        new Thread(() -> {
            try {
                List<Note> notes = dbHelper.getAllNotes();
                int totalCount = dbHelper.getNotesCount();

                Log.d(TAG, "从数据库获取到 " + notes.size() + " 条记录");

                runOnUiThread(() -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("📊 数据库详情\n\n");

                    if (notes == null || notes.isEmpty()) {
                        sb.append("📝 暂无备忘录");
                    } else {
                        for (Note note : notes) {
                            sb.append("🆔 ID: ").append(note.getId())
                                    .append("\n📌 标题: ").append(note.getTitle())
                                    .append("\n📄 内容: ").append(note.getContent())
                                    .append("\n📅 创建: ").append(new Date(note.getCreatedTime()))
                                    .append("\n⏰ 提醒: ").append(note.getReminderTime() > 0 ?
                                            new Date(note.getReminderTime()) : "无")
                                    .append("\n\n");
                        }
                    }

                    sb.append("━━━━━━━━━━━━━━━━━━━━\n");
                    sb.append("📊 总计: ").append(totalCount).append(" 条记录");

                    new AlertDialog.Builder(this)
                            .setTitle("数据库内容")
                            .setMessage(sb.toString())
                            .setPositiveButton("确定", null)
                            .show();

                    Log.d(TAG, "数据库内容对话框已显示");
                });

            } catch (Exception e) {
                Log.e(TAG, "获取数据库内容失败: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "获取数据库内容失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    /**
     * 显示清空数据库确认对话框
     */
    private void showClearDatabaseDialog() {
        new AlertDialog.Builder(this)
                .setTitle("⚠️ 确认清空")
                .setMessage("此操作将永久删除所有备忘录数据，且无法恢复。\n\n确定要继续吗？")
                .setPositiveButton("确定清空", (dialog, which) -> {
                    clearDatabase();
                })
                .setNegativeButton("取消", (dialog, which) -> {
                    if (drawerLayout != null) {
                        drawerLayout.closeDrawer(GravityCompat.START);
                    }
                })
                .setOnCancelListener(dialog -> {
                    if (drawerLayout != null) {
                        drawerLayout.closeDrawer(GravityCompat.START);
                    }
                })
                .show();
    }

    /**
     * 显示导出数据对话框
     */
    private void showExportDataDialog() {
        new AlertDialog.Builder(this)
                .setTitle("📤 导出数据")
                .setMessage("数据导出功能正在开发中...\n\n将支持以下格式：\n• JSON 格式\n• CSV 表格\n• 纯文本")
                .setPositiveButton("了解", null)
                .show();
    }

    /**
     * 设置RecyclerView
     */
    private void setupRecyclerView() {
        recyclerView = findViewById(R.id.recycler_view);
        if (recyclerView == null) {
            Log.w(TAG, "未找到RecyclerView");
            return;
        }

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setHasFixedSize(true);

        final NoteAdapter adapter = new NoteAdapter();
        recyclerView.setAdapter(adapter);

        // 设置滑动删除
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                0,
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(RecyclerView recyclerView,
                                  RecyclerView.ViewHolder viewHolder,
                                  RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                Note noteToDelete = adapter.getNoteAt(viewHolder.getAdapterPosition());
                deleteNoteWithReminder(noteToDelete);
                Toast.makeText(MainActivity.this, "✅ " + getString(R.string.delete), Toast.LENGTH_SHORT).show();
            }
        }).attachToRecyclerView(recyclerView);

        // 点击笔记项，进入编辑
        adapter.setOnItemClickListener(note -> {
            Intent intent = new Intent(MainActivity.this, NoteDetailActivity.class);
            intent.putExtra(NoteDetailActivity.EXTRA_NOTE_ID, note.getId());
            Log.d(TAG, "Opening note for edit, ID: " + note.getId());
            startActivityForResult(intent, EDIT_NOTE_REQUEST);
        });

        recyclerView.setItemAnimator(null);
    }



    private void deleteNoteWithReminder(Note note) {
        if (note == null) return;

        // 如果有提醒，先取消提醒
        if (note.getReminderTime() > 0) {
            reminderManager.cancelReminder(note.getId());
            Log.d(TAG, "已取消备忘录提醒，ID: " + note.getId());
        }

        // 删除备忘录
        noteViewModel.delete(note);
    }



    /**
     * 设置ViewModel
     */
    private void setupViewModel() {
        noteViewModel = new ViewModelProvider(this).get(NoteViewModel.class);

        // 观察数据变化，更新列表和空视图显示
        noteViewModel.getAllNotes().observe(this, notes -> {
            Log.d(TAG, "LiveData 触发，接收到 " + (notes != null ? notes.size() : 0) + " 条备忘录");

            if (notes != null) {
                for (Note note : notes) {
                    Log.d(TAG, "备忘录: ID=" + note.getId() + ", 标题=" + note.getTitle());
                }
            }

            if (recyclerView != null) {
                NoteAdapter adapter = (NoteAdapter) recyclerView.getAdapter();
                if (adapter != null) {
                    adapter.submitList(notes);
                    Log.d(TAG, "适配器数据已更新");
                }
            }

            if (textViewEmpty != null) {
                boolean isEmpty = notes == null || notes.isEmpty();
                textViewEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
                Log.d(TAG, "空视图显示状态: " + (isEmpty ? "显示" : "隐藏"));
            }

            Log.d(TAG, "📝 备忘录列表更新，当前数量: " + (notes != null ? notes.size() : 0));
        });
    }

    /**
     * 手动刷新数据
     */
    private void refreshData() {
        Log.d(TAG, "手动刷新数据");
        if (noteViewModel != null) {
            new Thread(() -> {
                try {
                    List<Note> notes = dbHelper.getAllNotes();
                    Log.d(TAG, "直接从数据库获取到 " + notes.size() + " 条备忘录");

                    runOnUiThread(() -> {
                        if (recyclerView != null) {
                            NoteAdapter adapter = (NoteAdapter) recyclerView.getAdapter();
                            if (adapter != null) {
                                adapter.submitList(new ArrayList<>(notes));
                                Log.d(TAG, "手动更新适配器完成");
                            }
                        }

                        if (textViewEmpty != null) {
                            textViewEmpty.setVisibility(notes.isEmpty() ? View.VISIBLE : View.GONE);
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "手动刷新失败: " + e.getMessage(), e);
                }
            }).start();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "MainActivity onResume，刷新数据");
        refreshData();
        // 恢复实时刷新
        startRealTimeRefresh();
    }
    @Override
    protected void onPause() {
        super.onPause();
        // 暂停实时刷新以节省资源
        stopRealTimeRefresh();
    }
    /**
     * 初始化数据库
     */
    private void initDatabase() {
        try {
            dbHelper = NoteDbHelper.getInstance(this);
            int noteCount = dbHelper.getNotesCount();
            Log.i(TAG, "✅ 数据库初始化成功，当前有 " + noteCount + " 条备忘录");

            if (noteCount == 0) {
                insertSampleData();
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ 数据库初始化失败: " + e.getMessage(), e);
            Toast.makeText(this, "数据库初始化失败", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 清空数据库
     */
    /**
     * 清空数据库
     */
    private void clearDatabase() {
        try {
            // 在清空数据库前，先取消所有提醒
            new Thread(() -> {
                try {
                    List<Note> notesWithReminders = dbHelper.getNotesWithReminders();
                    if (notesWithReminders != null) {
                        for (Note note : notesWithReminders) {
                            reminderManager.cancelReminder(note.getId());
                        }
                        Log.d(TAG, "已取消 " + notesWithReminders.size() + " 个提醒");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "取消提醒失败: " + e.getMessage(), e);
                }
            }).start();

            int deletedCount = dbHelper.deleteAllNotes();
            if (deletedCount >= 0) {
                String message = deletedCount > 0 ?
                        "✅ 成功清空 " + deletedCount + " 条备忘录" :
                        "📝 数据库已为空";
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                Log.i(TAG, "🗑️ 数据库清空成功，删除了 " + deletedCount + " 条记录");
            } else {
                Toast.makeText(this, "❌ 清空数据库失败", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "❌ 清空数据库失败");
            }
        } catch (Exception e) {
            Toast.makeText(this, "❌ 清空数据库时发生错误", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "❌ 清空数据库时发生异常: " + e.getMessage(), e);
        } finally {
            if (drawerLayout != null) {
                drawerLayout.closeDrawer(GravityCompat.START);
            }
        }
    }

    /**
     * 插入示例数据
     */
    private void insertSampleData() {
        try {
            Note sampleNote = new Note();
            sampleNote.setTitle("🎉 欢迎使用备忘录");
            sampleNote.setContent("这是您的第一条备忘录！\n\n✨ 功能特色：\n• 智能AI助手\n• 语音识别输入\n• 云端同步备份\n• 智能提醒通知\n\n点击右下角➕按钮创建更多备忘录吧！");
            sampleNote.setCreatedTime(System.currentTimeMillis());
            sampleNote.setReminderTime(0);

            long result = dbHelper.insertNote(sampleNote);
            if (result > 0) {
                Log.i(TAG, "✅ 示例数据插入成功");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ 插入示例数据失败: " + e.getMessage(), e);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            String message = "";
            if (requestCode == ADD_NOTE_REQUEST) {
                message = "✅ 备忘录已添加";
            } else if (requestCode == EDIT_NOTE_REQUEST) {
                message = "✅ 备忘录已更新";
            }
            if (!message.isEmpty()) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // 停止实时刷新
        stopRealTimeRefresh();

        // 注销广播接收器
        if (reminderBroadcastReceiver != null) {
            unregisterReceiver(reminderBroadcastReceiver);
        }
        Log.d(TAG, "🔄 MainActivity 销毁");
    }

    private void showTTSTestDialog() {
        Intent intent = new Intent(MainActivity.this, TTSActivity.class);
        startActivity(intent);
    }
    /**
     * 注册提醒广播接收器
     */
    private void registerReminderReceiver() {
        reminderBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("com.example.create_part2.REMINDER_TRIGGERED".equals(intent.getAction())) {
                    int noteId = intent.getIntExtra("noteId", -1);
                    String title = intent.getStringExtra("title");
                    String content = intent.getStringExtra("content");

                    handleReminderTriggered(noteId, title, content);
                }
            }
        };

        IntentFilter filter = new IntentFilter("com.example.create_part2.REMINDER_TRIGGERED");

        // 针对不同Android版本使用不同的注册方式
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            // Android 13 (API 33) 及以上版本需要明确指定 RECEIVER_NOT_EXPORTED
            registerReceiver(reminderBroadcastReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            // Android 8.0 (API 26) 到 Android 12 (API 32)
            registerReceiver(reminderBroadcastReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        }
    }

    /**
     * 处理提醒触发
     */
    private void handleReminderTriggered(int noteId, String title, String content) {
        Log.d(TAG, "处理提醒触发: noteId=" + noteId + ", title=" + title);

        // 更新备忘录状态
        updateNoteReminderStatus(noteId, true);

        // 播报语音
        playReminderTTS(title, content);

        // 刷新UI
        refreshData();
    }

    /**
     * 更新备忘录提醒状态
     */
    private void updateNoteReminderStatus(int noteId, boolean isTriggered) {
        new Thread(() -> {
            try {
                Note note = dbHelper.getNoteById(noteId);
                if (note != null) {
                    note.setReminderTriggered(isTriggered);
                    // 这里需要在数据库中添加字段或者用其他方式标记状态
                    Log.d(TAG, "更新备忘录提醒状态: " + noteId + " -> " + isTriggered);
                }
            } catch (Exception e) {
                Log.e(TAG, "更新提醒状态失败: " + e.getMessage(), e);
            }
        }).start();
    }

    /**
     * 播报提醒语音
     */
    private void playReminderTTS(String title, String content) {
        try {
            MicrosoftTTS ttsService = new MicrosoftTTS(this);
            String reminderText = "提醒：" + title + "。内容：" + content;
            ttsService.speak(reminderText);
            Log.d(TAG, "开始播报提醒语音: " + title);
        } catch (Exception e) {
            Log.e(TAG, "播报提醒语音失败: " + e.getMessage(), e);
        }
    }
    /**
     * 启动实时刷新检查
     */
    private void startRealTimeRefresh() {
        if (refreshHandler == null) {
            refreshHandler = new Handler(Looper.getMainLooper());
        }

        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                // 检查是否有提醒时间到达
                checkAndUpdateReminderStatus();

                // 继续下一次检查
                if (refreshHandler != null) {
                    refreshHandler.postDelayed(this, REFRESH_INTERVAL);
                }
            }
        };

        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL);
        Log.d(TAG, "实时刷新检查已启动");
    }

    /**
     * 停止实时刷新检查
     */
    private void stopRealTimeRefresh() {
        if (refreshHandler != null && refreshRunnable != null) {
            refreshHandler.removeCallbacks(refreshRunnable);
            Log.d(TAG, "实时刷新检查已停止");
        }
    }

    /**
     * 检查并更新提醒状态
     */

    private void checkAndUpdateReminderStatus() {
        new Thread(() -> {
            try {
                List<Note> notesWithReminders = dbHelper.getNotesWithReminders();
                boolean needRefresh = false;
                long currentTime = System.currentTimeMillis();

                for (Note note : notesWithReminders) {
                    // 检查提醒时间是否已到
                    if (note.getReminderTime() > 0 && currentTime >= note.getReminderTime()) {
                        // 使用Set来记录已经处理过的备忘录，避免重复播报
                        if (!playedTTSNotes.contains(note.getId())) {
                            playedTTSNotes.add(note.getId());

                            // 标记为已触发
                            note.setReminderTriggered(true);
                            needRefresh = true;

                            // 在主线程中播报提醒
                            runOnUiThread(() -> {
                                playReminderTTSOnce(note.getTitle(), note.getContent());
                            });

                            Log.d(TAG, "检测到提醒时间到达，开始播报: " + note.getTitle());
                        }
                    }
                }

                // 如果需要刷新UI
                if (needRefresh) {
                    runOnUiThread(() -> {
                        forceRefreshAdapter();
                    });
                }

            } catch (Exception e) {
                Log.e(TAG, "检查提醒状态失败: " + e.getMessage(), e);
            }
        }).start();
    }

    /**
     * 强制刷新适配器
     */
    private void forceRefreshAdapter() {
        if (recyclerView != null && recyclerView.getAdapter() != null) {
            new Thread(() -> {
                try {
                    List<Note> notes = dbHelper.getAllNotes();
                    runOnUiThread(() -> {
                        NoteAdapter adapter = (NoteAdapter) recyclerView.getAdapter();
                        // 先清空列表，再重新设置，强制刷新
                        adapter.submitList(null);
                        adapter.submitList(new ArrayList<>(notes));
                        Log.d(TAG, "强制刷新适配器完成");
                    });
                } catch (Exception e) {
                    Log.e(TAG, "强制刷新适配器失败: " + e.getMessage(), e);
                }
            }).start();
        }
    }
    /**
     * 播放提醒系统提示音（替代TTS）
     */
    private void playReminderTTSOnce(String title, String content) {
        try {
            Log.d(TAG, "播放提醒系统提示音: " + title);

            // 播放系统通知提示音
            Uri notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            if (notificationUri != null) {
                Ringtone ringtone = RingtoneManager.getRingtone(this, notificationUri);
                if (ringtone != null) {
                    ringtone.play();
                    Log.d(TAG, "系统提示音播放成功: " + title);
                }
            } else {
                Log.w(TAG, "未找到系统通知音，尝试播放按键音");
                AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                if (audioManager != null) {
                    audioManager.playSoundEffect(AudioManager.FX_KEY_CLICK);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "播放系统提示音失败: " + e.getMessage(), e);
        }
    }


}