package com.example.create_part2.db;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.lifecycle.ViewModelProvider;

import com.example.create_part2.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 该类是笔记详情页面的 Activity，负责新建、编辑、保存和设置提醒等功能。
 * 用户可以在此页面输入笔记内容、设置提醒时间，并进行保存或取消操作。
 */
public class NoteDetailActivity extends AppCompatActivity {

    public static final String EXTRA_NOTE_ID = "com.example.create_part2.EXTRA_NOTE_ID";
    private static final String TAG = "NoteDetailActivity";
    private static final int DEFAULT_NOTE_ID = -1;

    // UI 组件
    private EditText editTextTitle;
    private EditText editTextContent;
    private Switch switchReminder;
    private LinearLayout reminderTimeContainer;
    private TextView textViewReminderTime;
    private TextView textViewSaveStatus;
    private AppCompatButton buttonSave;
    private AppCompatButton buttonCancel;

    // 数据和状态
    private NoteViewModel noteViewModel;
    private ExecutorService executor;
    private Calendar reminderCalendar;
    private int noteId = DEFAULT_NOTE_ID;
    private Note currentNote;
    private boolean isNoteLoaded = false;
    private boolean isReminderSet = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_detail);

        // 初始化视图
        initializeViews();

        // 初始化ViewModel
        noteViewModel = new ViewModelProvider(this).get(NoteViewModel.class);
        executor = Executors.newSingleThreadExecutor();

        // 获取传递的笔记ID
        handleIntent();

        // 设置事件监听器
        setupEventListeners();

        // 初始化状态
        initializeState();
    }

    private void initializeViews() {
        editTextTitle = findViewById(R.id.edit_text_title);
        editTextContent = findViewById(R.id.edit_text_content);
        switchReminder = findViewById(R.id.switch_reminder);
        reminderTimeContainer = findViewById(R.id.reminder_time_container);
        textViewReminderTime = findViewById(R.id.text_view_reminder_time);
        textViewSaveStatus = findViewById(R.id.text_view_save_status);
        buttonSave = findViewById(R.id.button_save);
        buttonCancel = findViewById(R.id.button_cancel);

        // 确保 UI 组件可用
        setupEditTextBehavior();
    }

    private void setupEditTextBehavior() {
        // 确保输入框可以正常工作
        editTextTitle.setFocusable(true);
        editTextTitle.setFocusableInTouchMode(true);
        editTextTitle.setClickable(true);
        editTextTitle.setCursorVisible(true);

        editTextContent.setFocusable(true);
        editTextContent.setFocusableInTouchMode(true);
        editTextContent.setClickable(true);
        editTextContent.setCursorVisible(true);

        // 添加调试日志
        editTextTitle.setOnFocusChangeListener((v, hasFocus) -> {
            Log.d(TAG, "Title EditText focus changed: " + hasFocus);
            if (hasFocus) {
                editTextTitle.setCursorVisible(true);
            }
        });

        editTextContent.setOnFocusChangeListener((v, hasFocus) -> {
            Log.d(TAG, "Content EditText focus changed: " + hasFocus);
            if (hasFocus) {
                editTextContent.setCursorVisible(true);
            }
        });
    }

    private void initializeState() {
        // 初始化提醒日历
        reminderCalendar = Calendar.getInstance();
        reminderCalendar.add(Calendar.HOUR_OF_DAY, 1); // 默认1小时后
    }

    private void handleIntent() {
        Intent intent = getIntent();
        if (intent.hasExtra(EXTRA_NOTE_ID)) {
            noteId = intent.getIntExtra(EXTRA_NOTE_ID, DEFAULT_NOTE_ID);
            Log.d(TAG, "Editing note with ID: " + noteId);

            if (noteId != DEFAULT_NOTE_ID) {
                // 观察笔记数据变化
                noteViewModel.getNoteById(noteId).observe(this, note -> {
                    if (note != null && !isNoteLoaded) {
                        Log.d(TAG, "Note loaded: " + note.getTitle());
                        currentNote = note;
                        isNoteLoaded = true;
                        loadNoteData(note);
                    } else if (note == null && !isNoteLoaded) {
                        Log.e(TAG, "Note with ID " + noteId + " not found or deleted.");
                        isNoteLoaded = true;
                        Toast.makeText(NoteDetailActivity.this, "备忘录未找到", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
            }
        } else {
            Log.d(TAG, "Creating new note");
            setupForNewNote();
        }
    }

    private void loadNoteData(Note note) {
        editTextTitle.setText(note.getTitle());
        editTextContent.setText(note.getContent());

        // 设置提醒
        if (note.getReminderTime() > 0) {
            isReminderSet = true;
            switchReminder.setChecked(true);
            reminderTimeContainer.setVisibility(View.VISIBLE);
            reminderCalendar = Calendar.getInstance();
            reminderCalendar.setTimeInMillis(note.getReminderTime());
            updateReminderTimeText();
        } else {
            isReminderSet = false;
            switchReminder.setChecked(false);
            reminderTimeContainer.setVisibility(View.GONE);
        }
    }

    private void setupForNewNote() {
        isNoteLoaded = true;
        isReminderSet = false;
        switchReminder.setChecked(false);
        reminderTimeContainer.setVisibility(View.GONE);
    }

    private void setupEventListeners() {
        // 设置提醒开关监听
        switchReminder.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Log.d(TAG, "Reminder switch changed: " + isChecked);

                if (isChecked) {
                    reminderTimeContainer.setVisibility(View.VISIBLE);
                    if (!isReminderSet) {
                        // 如果还没有设置提醒时间，显示时间选择器
                        showDateTimePicker();
                    } else {
                        // 如果已经设置过，显示当前时间
                        updateReminderTimeText();
                    }
                } else {
                    reminderTimeContainer.setVisibility(View.GONE);
                    isReminderSet = false;
                }
            }
        });

        // 设置提醒时间容器点击事件
        reminderTimeContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (switchReminder.isChecked()) {
                    showDateTimePicker();
                }
            }
        });

        // 设置提醒时间文本点击事件
        textViewReminderTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (switchReminder.isChecked()) {
                    showDateTimePicker();
                }
            }
        });

        // 文本变化监听
        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                hideStatusMessage();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };

        editTextTitle.addTextChangedListener(textWatcher);
        editTextContent.addTextChangedListener(textWatcher);

        // 设置保存按钮点击事件
        buttonSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "Save button clicked");
                if (noteId == DEFAULT_NOTE_ID || isNoteLoaded) {
                    saveNote();
                } else {
                    Toast.makeText(NoteDetailActivity.this, "加载中...", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // 设置取消按钮点击事件
        buttonCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    /**
     * 显示日期时间选择器
     */
    private void showDateTimePicker() {
        Calendar currentCalendar = Calendar.getInstance();

        // 确保 reminderCalendar 不为空
        if (reminderCalendar == null) {
            reminderCalendar = Calendar.getInstance();
            reminderCalendar.add(Calendar.HOUR_OF_DAY, 1);
        }

        // 显示日期选择器
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    reminderCalendar.set(Calendar.YEAR, year);
                    reminderCalendar.set(Calendar.MONTH, month);
                    reminderCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);

                    // 显示时间选择器
                    showTimePicker();
                },
                reminderCalendar.get(Calendar.YEAR),
                reminderCalendar.get(Calendar.MONTH),
                reminderCalendar.get(Calendar.DAY_OF_MONTH)
        );

        // 设置最小日期为今天
        datePickerDialog.getDatePicker().setMinDate(currentCalendar.getTimeInMillis());
        datePickerDialog.show();
    }

    /**
     * 显示时间选择器
     */
    private void showTimePicker() {
        TimePickerDialog timePickerDialog = new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    reminderCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    reminderCalendar.set(Calendar.MINUTE, minute);
                    reminderCalendar.set(Calendar.SECOND, 0);
                    reminderCalendar.set(Calendar.MILLISECOND, 0);

                    // 检查时间是否有效
                    if (reminderCalendar.getTimeInMillis() <= System.currentTimeMillis()) {
                        Toast.makeText(this, "提醒时间不能早于当前时间", Toast.LENGTH_SHORT).show();
                        // 重置到1小时后
                        reminderCalendar = Calendar.getInstance();
                        reminderCalendar.add(Calendar.HOUR_OF_DAY, 1);
                    }

                    isReminderSet = true;
                    updateReminderTimeText();
                },
                reminderCalendar.get(Calendar.HOUR_OF_DAY),
                reminderCalendar.get(Calendar.MINUTE),
                true // 使用24小时格式
        );

        timePickerDialog.show();
    }

    /**
     * 更新提醒时间文本
     */
    private void updateReminderTimeText() {
        if (reminderCalendar != null && isReminderSet) {
            SimpleDateFormat sdf = new SimpleDateFormat("提醒时间: yyyy年MM月dd日 HH:mm", Locale.getDefault());
            String formattedDate = sdf.format(new Date(reminderCalendar.getTimeInMillis()));
            textViewReminderTime.setText(formattedDate);
        } else {
            textViewReminderTime.setText("选择提醒时间");
        }
    }

    /**
     * 显示状态消息
     */
    private void showStatusMessage(String message) {
        if (textViewSaveStatus != null) {
            textViewSaveStatus.setText(message);
            textViewSaveStatus.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 隐藏状态消息
     */
    private void hideStatusMessage() {
        if (textViewSaveStatus != null) {
            textViewSaveStatus.setVisibility(View.GONE);
        }
    }


    /**
     * 保存备忘录
     */
    private void saveNote() {
        Log.d(TAG, "saveNote() called");
        String title = editTextTitle.getText().toString().trim();
        String content = editTextContent.getText().toString().trim();

        if (TextUtils.isEmpty(title)) {
            Toast.makeText(this, "请输入标题", Toast.LENGTH_SHORT).show();
            editTextTitle.requestFocus();
            return;
        }

        // 将reminderTime声明为final
        final long reminderTime;
        if (switchReminder.isChecked() && isReminderSet && reminderCalendar != null) {
            reminderTime = reminderCalendar.getTimeInMillis();
            if (reminderTime <= System.currentTimeMillis()) {
                Toast.makeText(this, "提醒时间不能早于当前时间", Toast.LENGTH_SHORT).show();
                return;
            }
        } else {
            reminderTime = 0;
        }

        // 创建提醒管理器
        ReminderManager reminderManager = new ReminderManager(this);

        Note note;
        if (noteId != DEFAULT_NOTE_ID) {
            // 更新备忘录
            note = new Note(title, content);
            note.setId(noteId);
            note.setCreatedTime(currentNote != null ? currentNote.getCreatedTime() : System.currentTimeMillis());

            // 如果之前有提醒，先取消
            if (currentNote != null && currentNote.getReminderTime() > 0) {
                reminderManager.cancelReminder(noteId);
            }

            note.setReminderTime(reminderTime);
            Log.d(TAG, "Updating note with ID: " + noteId);
            noteViewModel.update(note);

            // 设置新的提醒
            if (reminderTime > 0) {
                reminderManager.setReminder(note);
            }
        } else {
            // 新增备忘录
            note = new Note(title, content);
            note.setReminderTime(reminderTime);
            Log.d(TAG, "Inserting new note");

            // 插入备忘录后设置提醒
            executor.execute(() -> {
                try {
                    // 插入备忘录并获取返回的ID
                    NoteDbHelper dbHelper = NoteDbHelper.getInstance(getApplicationContext());
                    long insertedId = dbHelper.insertNote(note);

                    if (insertedId != -1 && reminderTime > 0) {
                        note.setId((int) insertedId);
                        reminderManager.setReminder(note);
                        Log.d(TAG, "为新备忘录设置提醒，ID: " + insertedId);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "设置新备忘录提醒失败: " + e.getMessage(), e);
                }
            });

        }

        // 显示保存成功消息
        String message = "笔记已保存";
        if (reminderTime > 0) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault());
            message += "，提醒时间: " + dateFormat.format(new Date(reminderTime));
        }

        showStatusMessage("已保存");
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();

        setResult(RESULT_OK);
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_note_detail, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.save_note) {
            saveNote();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}