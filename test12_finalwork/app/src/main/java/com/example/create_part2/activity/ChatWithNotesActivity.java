package com.example.create_part2.activity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.create_part2.ChatAPI;
import com.example.create_part2.MicrosoftTTS;
import com.example.create_part2.db.Note;
import com.example.create_part2.db.NoteAdapter;
import com.example.create_part2.db.NoteDbHelper;
import com.example.create_part2.db.NoteDetailActivity;
import com.example.create_part2.db.NoteViewModel;
import com.example.create_part2.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.microsoft.cognitiveservices.speech.*;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;
import com.example.create_part2.BuildConfig;
import com.example.create_part2.ConfigManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


//管理主页面点击的ai助手
public class ChatWithNotesActivity extends AppCompatActivity {

    // 建议的延迟时间
    private static final int INIT_DELAY = 500;           // 初始化延迟
    private static final int SWITCH_DELAY = 200;         // 语音识别切换延迟
    private static final int RESTART_DELAY = 300;        // 重启延迟
    private static final int TTS_FINISH_DELAY = 800;     // TTS完成后延迟

    private static final String TAG = "ChatWithNotesActivity1";
    private static final int REQUEST_RECORD_AUDIO_PERMISSION_CODE = 1;

    // 上半部分：备忘录相关
    private NoteViewModel noteViewModel;
    private RecyclerView recyclerView;
    private View textViewEmpty;
    private NoteDbHelper dbHelper;

    // 下半部分：聊天相关
    private TextView chatTextView;
    private ScrollView scrollView;
    private ProgressBar progressBar;
    private FloatingActionButton voiceButton;

    // 语音识别相关
    private static String getSpeechSubscriptionKey(Context context) {
        return ConfigManager.getInstance(context).getSpeechSubscriptionKey();
    }
    
    private static String getSpeechRegion(Context context) {
        return ConfigManager.getInstance(context).getSpeechRegion();
    }
    private SpeechRecognizer speechRecognizer;
    private boolean isRecognizing = false;

    // TTS相关
    private MicrosoftTTS ttsService;
    private boolean autoPlayTTS = true;

    // 线程管理
    private ExecutorService executorService;
    private Handler mainHandler;

    // 添加成员变量来存储ends状态
    private boolean conversationEnds = true;


    // 唤醒词相关
    private SpeechRecognizer wakeupRecognizer;
    private boolean isWakeupListening = true; // 默认为true，表示要开启唤醒
    private Button wakeupToggleButton;
    private static final String WAKEUP_WORD = "老登老登";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            setContentView(R.layout.layout_chat_bottom_sheet);
            Log.d(TAG, "布局设置成功");

            // 初始化线程管理
            executorService = Executors.newSingleThreadExecutor();
            mainHandler = new Handler(Looper.getMainLooper());

            // 初始化数据库
            initDatabase();

            // 初始化视图
            initializeViews();

            // 初始化TTS服务
            initializeTTSService();

            // 设置上半部分（备忘录列表）
            setupNotesSection();

            // 设置下半部分（AI交互聊天区域）
            setupInteractiveChatSection();

            // 在最后添加：自动启动唤醒词监听
            mainHandler.postDelayed(() -> {
                startWakeupListening();
            }, INIT_DELAY ); // 延迟1秒启动，确保所有初始化完成

        } catch (Exception e) {
            Log.e(TAG, "初始化失败: " + e.getMessage(), e);
            Toast.makeText(this, "页面加载失败", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    /**
     * 初始化所有视图组件
     */
    private void initializeViews() {
        // 上半部分：备忘录
        recyclerView = findViewById(R.id.recycler_view);
        textViewEmpty = findViewById(R.id.text_view_empty);

        // 下半部分：聊天
        chatTextView = findViewById(R.id.chatTextView);
        scrollView = findViewById(R.id.scrollView);
        progressBar = findViewById(R.id.progressBar);
        voiceButton = findViewById(R.id.voiceButton);

        // 返回按钮
        Button returnButton = findViewById(R.id.button_test);
        if (returnButton != null) {
            returnButton.setOnClickListener(v -> finish());
        }

        // 初始化聊天界面
        if (chatTextView != null) {
            chatTextView.setText("📚AI助手克莱登: 请说 '莱登莱登' 来唤醒对话\n" +
                    "💡 您可以询问关于备忘录管理、日程安排或其他任何问题。\n\n");

        }

        // 唤醒词切换按钮 - 默认显示关闭选项
        wakeupToggleButton = findViewById(R.id.wakeup_toggle_button); // 需要在布局中添加这个按钮
        if (wakeupToggleButton != null) {
            wakeupToggleButton.setText("关闭语音唤醒");
//            wakeupToggleButton.setBackgroundColor(getResources().getColor(android.R.color.holo_red_light));
            wakeupToggleButton.setBackgroundColor(getResources().getColor(android.R.color.holo_green_light));
            wakeupToggleButton.setOnClickListener(v -> stopWakeupListening());
        }



        Log.d(TAG, "视图初始化完成");
    }

    /**
     * 设置上半部分：备忘录列表
     */
    private void setupNotesSection() {
        if (recyclerView == null) {
            Log.w(TAG, "未找到RecyclerView");
            return;
        }
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setHasFixedSize(true);
        final NoteAdapter adapter = new NoteAdapter();
        recyclerView.setAdapter(adapter);
        // 设置ViewModel和观察数据变化
        noteViewModel = new ViewModelProvider(this).get(NoteViewModel.class);


        noteViewModel.getAllNotes().observe(this, notes -> {
            Log.d(TAG, "观察到数据变化: " + (notes != null ? notes.size() : 0) + " 条备忘录");

            if (recyclerView.getAdapter() != null) {
                NoteAdapter noteAdapter = (NoteAdapter) recyclerView.getAdapter();
                noteAdapter.submitList(notes);
            }

            // 更新空视图
            if (textViewEmpty != null) {
                boolean isEmpty = notes == null || notes.isEmpty();
                textViewEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            }
        });

        // 点击备忘录项，进入编辑
        adapter.setOnItemClickListener(note -> {
            Intent intent = new Intent(ChatWithNotesActivity.this, NoteDetailActivity.class);
            intent.putExtra(NoteDetailActivity.EXTRA_NOTE_ID, note.getId());
            startActivity(intent);
        });
    }

    /**
     * 设置交互式聊天区域
     */
    private void setupInteractiveChatSection() {


        // 语音按钮 - 启动语音识别进行AI交互
        if (voiceButton != null) {
            voiceButton.setOnClickListener(v -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.RECORD_AUDIO},
                            REQUEST_RECORD_AUDIO_PERMISSION_CODE);
                } else {
                    startSpeechRecognitionForChat();
                }
            });

            // 长按停止TTS播放
            voiceButton.setOnLongClickListener(v -> {
                stopTTSPlayback();
                Toast.makeText(this, "已停止语音播放", Toast.LENGTH_SHORT).show();
                return true;
            });
        }

        // 点击聊天区域给出提示
        if (chatTextView != null) {
            chatTextView.setOnClickListener(v -> {
                Toast.makeText(this, "点击下方麦克风按钮开始语音对话", Toast.LENGTH_SHORT).show();
            });
        }
    }




    /**
     * 启动语音识别进行聊天
     */
    private void startSpeechRecognitionForChat() {
        Log.d(TAG, "========== 启动语音交互 ==========");
        Log.d(TAG, "当前识别状态: " + isRecognizing);
        if (isRecognizing) {
            Log.w(TAG, "⚠️ 语音识别已在进行中，跳过启动");
            return;
        }

        try {
            Log.d(TAG, "🎙️ 开始初始化语音识别器...");

            SpeechConfig speechConfig = SpeechConfig.fromSubscription(getSpeechSubscriptionKey(this), getSpeechRegion(this));
            speechConfig.setSpeechRecognitionLanguage("zh-CN");
            AudioConfig audioConfig = AudioConfig.fromDefaultMicrophoneInput();

            speechRecognizer = new SpeechRecognizer(speechConfig, audioConfig);
            isRecognizing = true;
            setVoiceButtonEnabled(false);

            Log.d(TAG, "✅ 语音识别器初始化完成");

            // 显示识别状态
            appendChatMessage("🎤 正在听取您的语音...");

            // 识别完成
            speechRecognizer.recognized.addEventListener((s, e) -> {
                if (e.getResult().getReason() == ResultReason.RecognizedSpeech) {
                    String recognizedText = e.getResult().getText();
                    if (recognizedText != null && !recognizedText.isEmpty()) {
                        mainHandler.post(() -> {
                            // 显示用户说的话
                            appendChatMessage("你: " + recognizedText);
                            // 发送给AI处理
                            sendMessageToAI(recognizedText);
                        });
                    }
                } else if (e.getResult().getReason() == ResultReason.NoMatch) {
                    mainHandler.post(() -> {
                        appendChatMessage("❌ 未识别到语音内容，请重试");
                        setVoiceButtonEnabled(true);
                    });
                }
                stopSpeechRecognition();
            });

            // 取消事件
            speechRecognizer.canceled.addEventListener((s, e) -> {
                stopSpeechRecognition();
                mainHandler.post(() -> {
                    appendChatMessage("❌ 语音识别被取消");
                    setVoiceButtonEnabled(true);
                });
            });

            // 会话停止
            speechRecognizer.sessionStopped.addEventListener((s, e) -> stopSpeechRecognition());

            speechRecognizer.startContinuousRecognitionAsync();

        } catch (Exception e) {
            Log.e(TAG, "启动语音识别失败", e);
            appendChatMessage("❌ 语音识别启动失败: " + e.getMessage());
            setVoiceButtonEnabled(true);
            isRecognizing = false;
        }
    }

    /**
     * 停止语音识别
     */
    private void stopSpeechRecognition() {
        if (speechRecognizer != null && isRecognizing) {
            try {
                speechRecognizer.stopContinuousRecognitionAsync();
                speechRecognizer.close();
                speechRecognizer = null;
            } catch (Exception ignored) {
            }
        }
        isRecognizing = false;
    }

    /**
     * 发送消息给AI
     */
    private void sendMessageToAI(String userMessage) {
        Log.d(TAG, "发送消息给AI: " + userMessage);

        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }

        executorService.execute(() -> {
            try {
                // 获取当前备忘录数量，用于检测是否有新增
                int beforeCount = dbHelper.getNotesCount();

                ChatAPI.ChatResult chatResult = ChatAPI.generateText(userMessage, this, null, null);
                String response = chatResult.text;
                this.conversationEnds = chatResult.ends;  // 对话是否结束


                if (response == null || response.trim().isEmpty()) {
                    response = "抱歉，我暂时无法回应，请稍后重试。";
                }


                // 检查是否有新增备忘录
                int afterCount = dbHelper.getNotesCount();
                boolean hasNewNote = afterCount > beforeCount;

                final String finalResponse = response;
                final boolean shouldRefresh = hasNewNote;

                mainHandler.post(() -> {
                    appendChatMessage("📚AI助手克莱登: " + finalResponse);

                    // 如果有新增备忘录，强制刷新列表
                    if (shouldRefresh) {
                        Log.d(TAG, "检测到新增备忘录，强制刷新列表");
                        forceRefreshNotesList();
                    }

                    // 自动播放AI回复
                    if (autoPlayTTS && ttsService != null) {
                        playAIResponseWithTTS(finalResponse);
                    } else {
                        // 如果不播放TTS，直接处理对话结束逻辑
                        if (conversationEnds && isWakeupListening) {
                            mainHandler.postDelayed(() -> startWakeupListening(), 1000);
                        }
                    }

                    if (progressBar != null) {
                        progressBar.setVisibility(View.GONE);
                    }
                    setVoiceButtonEnabled(true);
                });

            } catch (Exception e) {
                Log.e(TAG, "AI响应失败", e);
                mainHandler.post(() -> {
                    appendChatMessage("❌ AI服务暂时不可用，请稍后重试");
                    if (progressBar != null) {
                        progressBar.setVisibility(View.GONE);
                    }
                    setVoiceButtonEnabled(true);
                });
            }
        });
    }





    /**
     * 强制刷新备忘录列表
     */
    private void forceRefreshNotesList() {
        if (noteViewModel != null) {
            // 方法1：通过数据库直接刷新
            executorService.execute(() -> {
                try {
                    List<Note> notes = dbHelper.getAllNotes();
                    Log.d(TAG, "强制刷新：从数据库获取到 " + notes.size() + " 条备忘录");

                    mainHandler.post(() -> {
                        if (recyclerView != null && recyclerView.getAdapter() != null) {
                            NoteAdapter adapter = (NoteAdapter) recyclerView.getAdapter();
                            // 创建新的列表实例以触发DiffUtil
                            adapter.submitList(null);
                            adapter.submitList(new ArrayList<>(notes));
                            Log.d(TAG, "备忘录列表强制刷新完成");
                        }

                        // 更新空视图
                        if (textViewEmpty != null) {
                            textViewEmpty.setVisibility(notes.isEmpty() ? View.VISIBLE : View.GONE);
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "强制刷新失败: " + e.getMessage(), e);
                }
            });
        }
    }
    /**
     * 添加聊天消息
     */
    private void appendChatMessage(String message) {
        if (chatTextView != null && message != null) {
            chatTextView.append(message + "\n\n");

            // 滚动到底部
            if (scrollView != null) {
                scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
            }
        }
    }

    /**
     * 设置语音按钮状态
     */
    private void setVoiceButtonEnabled(boolean enabled) {
        if (voiceButton != null) {
            voiceButton.setEnabled(enabled);
            voiceButton.setAlpha(enabled ? 1f : 0.5f);
        }
    }

    /**
     * 初始化TTS服务
     */
    private void initializeTTSService() {
        try {
            Log.d(TAG, "初始化TTS服务");
            ttsService = new MicrosoftTTS(this);
            ttsService.setCallback(new MicrosoftTTS.TTSCallback() {
                @Override
                public void onSynthesisStarted() {
                    mainHandler.post(() -> {
                        if (voiceButton != null) {
                            voiceButton.setAlpha(0.5f);
                        }
                    });
                }

                @Override
                public void onSynthesisCompleted() {
                    mainHandler.post(() -> {
                        if (voiceButton != null) {
                            voiceButton.setAlpha(1.0f);
                        }

                        // 如果对话没有结束，继续语音识别
                        if (!conversationEnds) {
                            startSpeechRecognitionForChat();
                        } else {
                            // 对话结束，如果唤醒功能还开启，则重新启动唤醒监听
                            if (isWakeupListening) {
                                mainHandler.postDelayed(() -> startWakeupListening(), INIT_DELAY );
                            }
                        }
                    });
                }

                @Override
                public void onSynthesisCanceled() {
                    mainHandler.post(() -> {
                        if (voiceButton != null) {
                            voiceButton.setAlpha(1.0f);
                        }
                    });
                }

                @Override
                public void onError(String errorMessage) {
                    Log.e(TAG, "TTS错误: " + errorMessage);
                    mainHandler.post(() -> {
                        if (voiceButton != null) {
                            voiceButton.setAlpha(1.0f);
                        }
                    });
                }

                @Override
                public void onStatusUpdate(String status) {
                    Log.d(TAG, "TTS状态: " + status);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "TTS初始化失败", e);
            ttsService = null;
        }
    }

    /**
     * 播放AI回复
     */
    private void playAIResponseWithTTS(String response) {
        try {
            if (ttsService != null && response != null) {
                String cleanResponse = cleanTextForTTS(response);
                if (!cleanResponse.trim().isEmpty()) {
                    ttsService.speak(cleanResponse);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "TTS播放失败", e);
        }
    }

    /**
     * 清理文本用于TTS
     */
    private String cleanTextForTTS(String text) {
        if (text == null) return "";

        return text
                .replaceAll("[\\*\\#\\`\\~\\|]", "")
                .replaceAll("\\s+", " ")
                .replaceAll("🤖\\s*AI助手[:：]?\\s*", "")
                .replaceAll("❌\\s*", "")
                .trim();
    }

    /**
     * 停止TTS播放
     */
    private void stopTTSPlayback() {
        if (ttsService != null && ttsService.isSpeaking()) {
            ttsService.stopSynthesis();
        }
    }

    /**
     * 初始化数据库
     */
    private void initDatabase() {
        try {
            dbHelper = NoteDbHelper.getInstance(this);
            int noteCount = dbHelper.getNotesCount();
            Log.i(TAG, "✅ 数据库初始化成功，当前有 " + noteCount + " 条备忘录");

        } catch (Exception e) {
            Log.e(TAG, "❌ 数据库初始化失败: " + e.getMessage(), e);
            Toast.makeText(this, "数据库初始化失败", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startSpeechRecognitionForChat();
            } else {
                Toast.makeText(this, "需要录音权限才能使用语音功能", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "ChatWithNotesActivity onResume，刷新数据");
        refreshData();
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
    protected void onDestroy() {
        super.onDestroy();

        // 停止唤醒词监听
        stopWakeupListening();

        // 停止语音识别
        stopSpeechRecognition();

        // 释放TTS资源
        if (ttsService != null) {
            ttsService.destroy();
            ttsService = null;
        }

        // 关闭线程池
        if (executorService != null) {
            executorService.shutdown();
        }

        Log.d(TAG, "🔄 ChatWithNotesActivity 销毁");
    }


    // 重写唤醒词相关方法
    /**
     * 开始唤醒词监听
     */
    private void startWakeupListening() {
        if (isWakeupListening && wakeupRecognizer != null) {
            return; // 已经在监听中
        }

        // 检查权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_RECORD_AUDIO_PERMISSION_CODE);
            return;
        }

        try {
            // 配置唤醒词识别器
            SpeechConfig wakeupConfig = SpeechConfig.fromSubscription(getSpeechSubscriptionKey(this), getSpeechRegion(this));
            wakeupConfig.setSpeechRecognitionLanguage("zh-CN");
            AudioConfig wakeupAudioConfig = AudioConfig.fromDefaultMicrophoneInput();

            wakeupRecognizer = new SpeechRecognizer(wakeupConfig, wakeupAudioConfig);
            isWakeupListening = true;

            // 更新按钮状态
            updateWakeupButtonState();
//            appendChatMessage("🔊 语音唤醒已开启，请说 \"" + "莱登莱登" + "\" 来唤醒对话");


            // 监听唤醒词
            wakeupRecognizer.recognized.addEventListener((s, e) -> {
                if (e.getResult().getReason() == ResultReason.RecognizedSpeech) {
                    String recognizedText = e.getResult().getText();
                    Log.d(TAG, "🎤 唤醒监听识别到文本: [" + recognizedText + "]");

                    if (recognizedText != null && containsWakeupWord(recognizedText)) {
                        Log.d(TAG, "✅ 唤醒词匹配成功，准备启动对话");
                        mainHandler.post(() -> {
                            // 显示唤醒成功信息
//                            appendChatMessage("✅ 检测到唤醒词: " + recognizedText);
                            appendChatMessage("✅ 检测到唤醒词: 莱登莱登" );
                            appendChatMessage("正在为您启动语音助手克莱登...");
//                            ttsService.speak("您好，我是蔡徐坤！请问小黑子有什么吩咐！");
                            ttsService.speak("您好，我是克莱登！请问什么需要我帮您？");


//                            appendChatMessage("是否为OI");


                            // 1. 先关闭唤醒监听
                            stopWakeupListeningForChat();
                            Log.d(TAG, "🔇 唤醒监听已关闭");

                            // 2. 延迟一点时间后启动语音交互，确保唤醒监听完全关闭
                            mainHandler.postDelayed(() -> {
                                Log.d(TAG, "🎙️ 准备启动语音交互");
                                startSpeechRecognitionForChat();
                            }, 300);
                        });
                    } else {
                        // 记录未匹配的情况
                        Log.d(TAG, "❌ 文本不包含唤醒词，继续监听");
                    }
                } else if (e.getResult().getReason() == ResultReason.NoMatch) {
                    Log.d(TAG, "🔍 唤醒监听: 无匹配结果");
                } else {
                    Log.d(TAG, "🔍 唤醒监听其他结果: " + e.getResult().getReason());
                }
            });

            // 唤醒词识别错误处理
            wakeupRecognizer.canceled.addEventListener((s, e) -> {
                Log.w(TAG, "唤醒词识别被取消: " + e.getReason());
                mainHandler.post(() -> {
                    if (isWakeupListening) {
                        // 自动重启唤醒词监听
                        restartWakeupListening();
                    }
                });
            });

            // 会话停止时重启
            wakeupRecognizer.sessionStopped.addEventListener((s, e) -> {
                Log.d(TAG, "唤醒词会话停止");
                mainHandler.post(() -> {
                    if (isWakeupListening) {
                        restartWakeupListening();
                    }
                });
            });

            // 开始连续监听
            wakeupRecognizer.startContinuousRecognitionAsync();
            Log.d(TAG, "唤醒词监听已启动");

        } catch (Exception e) {
            Log.e(TAG, "启动唤醒词监听失败", e);
            appendChatMessage("❌ 语音唤醒启动失败: " + e.getMessage());
            isWakeupListening = false;
            updateWakeupButtonState();
        }
    }

    /**
     * 永久停止唤醒词监听（用户主动关闭）
     */
    private void stopWakeupListening() {
        if (wakeupRecognizer != null) {
            try {
                wakeupRecognizer.stopContinuousRecognitionAsync();
                wakeupRecognizer.close();
                wakeupRecognizer = null;
            } catch (Exception e) {
                Log.e(TAG, "停止唤醒词监听失败", e);
            }
        }
        isWakeupListening = false;
        updateWakeupButtonState();
        appendChatMessage("🔇 语音唤醒已关闭");
        Log.d(TAG, "唤醒词监听已永久停止");
    }

    /**
     * 为了启动对话而临时停止唤醒词监听
     */
    private void stopWakeupListeningForChat() {
        Log.d(TAG, "========== 停止唤醒监听 ==========");
        Log.d(TAG, "当前唤醒状态: " + isWakeupListening);
        Log.d(TAG, "唤醒识别器状态: " + (wakeupRecognizer != null ? "存在" : "不存在"));

        if (wakeupRecognizer != null) {
            try {
                Log.d(TAG, "正在停止唤醒识别器...");
                wakeupRecognizer.stopContinuousRecognitionAsync();
                wakeupRecognizer.close();
                wakeupRecognizer = null;
                Log.d(TAG, "✅ 唤醒识别器已成功停止和关闭");
            } catch (Exception e) {
                Log.e(TAG, "❌ 停止唤醒词监听失败: " + e.getMessage(), e);
            }
        }

        // 注意：这里不改变 isWakeupListening 状态，保持为true
        Log.d(TAG, "唤醒监听已临时停止，isWakeupListening保持为: " + isWakeupListening);
        Log.d(TAG, "===============================");
    }

    /**
     * 重启唤醒词监听（用于错误恢复）
     */
    private void restartWakeupListening() {
        if (!isWakeupListening) return;

        Log.d(TAG, "重启唤醒词监听");
        stopWakeupRecognizer();

        // 延迟500ms后重启
        mainHandler.postDelayed(() -> {
            if (isWakeupListening) {
                startWakeupListening();
            }
        }, 500);
    }

    /**
     * 只停止识别器，不改变状态
     */
    private void stopWakeupRecognizer() {
        if (wakeupRecognizer != null) {
            try {
                wakeupRecognizer.stopContinuousRecognitionAsync();
                wakeupRecognizer.close();
                wakeupRecognizer = null;
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 检查是否包含唤醒词
     */
    /**
     * 检查是否包含唤醒词
     */
    private boolean containsWakeupWord(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        // 记录原始识别文本
        Log.d(TAG, "========== 唤醒词检测 ==========");
        Log.d(TAG, "原始识别文本: [" + text + "]");

        // 移除标点符号和空格进行比较
        String cleanText = text.replaceAll("[\\p{Punct}\\s]", "").toLowerCase();
        Log.d(TAG, "清理后文本: [" + cleanText + "]");

        // 定义可能的唤醒词变体
        String[] wakeupVariants = {
                "老登", "老灯", "老凳", "老蹬", "老瞪", "莱登", // 可能的同音字
                "laodeng", "laodeng", // 拼音
                "老登老登", "老灯老灯", "老登老灯", "老灯老登",  // 重复形式
                "莱登莱登"
        };

        // 检查是否包含任何一个唤醒词变体
        boolean isWakeup = false;
        String matchedWord = "";

        for (String variant : wakeupVariants) {
            String cleanVariant = variant.toLowerCase();
            if (cleanText.contains(cleanVariant)) {
                isWakeup = true;
                matchedWord = variant;
                break;
            }
        }

        Log.d(TAG, "是否匹配唤醒词: " + isWakeup);
        if (isWakeup) {
            Log.d(TAG, "匹配的唤醒词: [" + matchedWord + "]");
        }
        Log.d(TAG, "============================");

        return isWakeup;
    }

    /**
     * 更新唤醒词按钮状态
     */
    private void updateWakeupButtonState() {
        if (wakeupToggleButton != null) {
            if (isWakeupListening) {
                wakeupToggleButton.setText("关闭语音唤醒");
                wakeupToggleButton.setBackgroundColor(getResources().getColor(android.R.color.holo_red_light));
            } else {
                wakeupToggleButton.setText("语音唤醒已关闭");
                wakeupToggleButton.setBackgroundColor(getResources().getColor(android.R.color.darker_gray));
                wakeupToggleButton.setEnabled(false); // 一旦关闭就不能再开启
            }
        }
    }
}