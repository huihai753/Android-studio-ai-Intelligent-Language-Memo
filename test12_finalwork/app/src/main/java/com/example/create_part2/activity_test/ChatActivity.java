package com.example.create_part2.activity_test;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.create_part2.ChatAPI;
import com.example.create_part2.MicrosoftTTS;
import com.example.create_part2.R;
import com.microsoft.cognitiveservices.speech.*;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;
import com.example.create_part2.BuildConfig;
import com.example.create_part2.ConfigManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatActivity extends AppCompatActivity {

    private static final int REQUEST_RECORD_AUDIO_PERMISSION_CODE = 1;

    private TextView chatTextView;
    private EditText messageInput;
    private Button sendButton;
    private ImageButton backButton;
    private ImageButton voiceButton;
    private ProgressBar progressBar;
    private ScrollView scrollView;
    private ExecutorService executorService;
    private Handler mainHandler;

    // Azure Speech SDK 相关 (STT)
    private static String getSpeechSubscriptionKey(Context context) {
        return ConfigManager.getInstance(context).getSpeechSubscriptionKey();
    }
    
    private static String getSpeechRegion(Context context) {
        return ConfigManager.getInstance(context).getSpeechRegion();
    }
    private SpeechRecognizer speechRecognizer;
    private boolean isRecognizing = false;

    // TTS 相关
    private MicrosoftTTS ttsService;
    private boolean autoPlayTTS = true; // 控制是否自动播放AI回复

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.chat_activity);

        // 初始化视图
        chatTextView = findViewById(R.id.chatTextView);
        messageInput = findViewById(R.id.messageInput);
        sendButton = findViewById(R.id.sendButton);
        backButton = findViewById(R.id.backButton);
        progressBar = findViewById(R.id.progressBar);
        scrollView = findViewById(R.id.scrollView);
        voiceButton = findViewById(R.id.voiceButton);

        executorService = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());

        // 初始化TTS服务
        initializeTTSService();

        chatTextView.setText("AI助手: 你好！我是AI助手，有什么可以帮到你的吗？\n\n");

        backButton.setOnClickListener(v -> finish());

        sendButton.setOnClickListener(v -> {
            String userMessage = messageInput.getText().toString().trim();
            if (!userMessage.isEmpty()) {
                appendMessage("你: " + userMessage);
                messageInput.setText("");
                progressBar.setVisibility(View.VISIBLE);
                sendButton.setEnabled(false);

                executorService.execute(() -> {
                    try {


                        ChatAPI.ChatResult chatResult = ChatAPI.generateText(userMessage, this, null, null);
                        String response = chatResult.text;
                        boolean ends = chatResult.ends;

                        // 现在你可以使用ends变量了
                        // 现在你可以使用ends变量了
                        if (ends) {
                            // 对话结束的处理逻辑
                            Log.i("ChatAPI", "对话已结束");
                        } else {
                            // 还有追问的处理逻辑
                            Log.i("ChatAPI", "等待用户回答追问");
                        }



                        if (response == null) {
                            response = "AI助手暂时无法响应，请稍后重试。";
                        }
                        final String finalResponse = response;
                        mainHandler.post(() -> {
                            appendMessage("AI助手: " + finalResponse);

                            // 自动播放AI回复
                            if (autoPlayTTS && ttsService != null) {
                                playAIResponseWithTTS(finalResponse);
                            }

                            progressBar.setVisibility(View.GONE);
                            sendButton.setEnabled(true);
                        });
                    } catch (Exception e) {
                        Log.e("ChatActivity", "发送消息出错", e);
                        String errorMessage = e.getMessage() == null ? "未知错误" : e.getMessage();
                        final String finalErrorMessage = errorMessage;
                        mainHandler.post(() -> {
                            Toast.makeText(ChatActivity.this, "发生错误: " + finalErrorMessage, Toast.LENGTH_LONG).show();
                            progressBar.setVisibility(View.GONE);
                            sendButton.setEnabled(true);
                        });
                    }
                });
            }
        });

        // 语音按钮增加长按功能：长按停止TTS播放
        voiceButton.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        REQUEST_RECORD_AUDIO_PERMISSION_CODE);
            } else {
                startAzureSpeechRecognition();
            }
        });

        // 长按语音按钮停止TTS播放
        voiceButton.setOnLongClickListener(v -> {
            stopTTSPlayback();
            Toast.makeText(this, "已停止语音播放", Toast.LENGTH_SHORT).show();
            return true;
        });
    }

    /**
     * 初始化TTS服务
     */
    private void initializeTTSService() {
        try {
            Log.d("ChatActivity", "初始化TTS服务");
            ttsService = new MicrosoftTTS(this);
            ttsService.setCallback(new MicrosoftTTS.TTSCallback() {
                @Override
                public void onSynthesisStarted() {
                    Log.d("ChatActivity", "TTS合成开始");
                    mainHandler.post(() -> {
                        // 播放时语音按钮变为半透明
                        if (voiceButton != null) {
                            voiceButton.setAlpha(0.7f);
                        }
                    });
                }

                @Override
                public void onSynthesisCompleted() {
                    Log.d("ChatActivity", "TTS合成完成");
                    mainHandler.post(() -> {
                        // 恢复语音按钮正常状态
                        if (voiceButton != null) {
                            voiceButton.setAlpha(1.0f);
                        }
                    });
                }

                @Override
                public void onSynthesisCanceled() {
                    Log.d("ChatActivity", "TTS合成取消");
                    mainHandler.post(() -> {
                        if (voiceButton != null) {
                            voiceButton.setAlpha(1.0f);
                        }
                    });
                }

                @Override
                public void onError(String errorMessage) {
                    Log.e("ChatActivity", "TTS合成错误: " + errorMessage);
                    mainHandler.post(() -> {
                        if (voiceButton != null) {
                            voiceButton.setAlpha(1.0f);
                        }
                        // 可选：显示错误提示
                        // Toast.makeText(ChatActivity.this, "语音播放失败", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onStatusUpdate(String status) {
                    Log.d("ChatActivity", "TTS状态更新: " + status);
                }
            });

            Log.d("ChatActivity", "TTS服务初始化完成");
        } catch (Exception e) {
            Log.e("ChatActivity", "TTS服务初始化失败: " + e.getMessage(), e);
            ttsService = null;
        }
    }

    /**
     * 使用TTS播放AI回复
     * @param response AI回复文本
     */
    private void playAIResponseWithTTS(String response) {
        try {
            Log.d("ChatActivity", "准备播放AI回复: " + response);

            // 检查TTS服务是否可用
            if (ttsService != null) {
                // 清理响应文本，移除特殊字符和格式
                String cleanResponse = cleanTextForTTS(response);

                // 如果文本不为空且不太长，则播放
                if (!cleanResponse.trim().isEmpty() && cleanResponse.length() <= 300) {
                    Log.d("ChatActivity", "开始TTS播放: " + cleanResponse);
                    ttsService.speak(cleanResponse);
                } else if (cleanResponse.length() > 300) {
                    // 如果文本太长，只播放前面部分
                    String shortResponse = cleanResponse.substring(0, 300) + "...";
                    Log.d("ChatActivity", "文本过长，播放简化版本: " + shortResponse);
                    ttsService.speak(shortResponse);
                }
            } else {
                Log.w("ChatActivity", "TTS服务未初始化，跳过语音播放");
            }
        } catch (Exception e) {
            Log.e("ChatActivity", "TTS播放失败: " + e.getMessage(), e);
            // 不影响主要功能，只记录错误
        }
    }

    /**
     * 清理文本用于TTS播放
     * @param text 原始文本
     * @return 清理后的文本
     */
    private String cleanTextForTTS(String text) {
        if (text == null) {
            return "";
        }

        // 移除常见的文本格式符号
        String cleanText = text
                .replaceAll("[\\*\\#\\`\\~\\|]", "") // 移除markdown符号
                .replaceAll("\\s+", " ") // 多个空白字符替换为单个空格
                .replaceAll("[\\r\\n]+", "。") // 换行符替换为句号
                .replaceAll("AI助手[:：]?\\s*", "") // 移除"AI助手:"前缀
                .replaceAll("系统[:：]?\\s*", "") // 移除"系统:"前缀
                .trim();

        // 确保以标点符号结尾
        if (!cleanText.isEmpty() && !cleanText.matches(".*[。！？\\.]$")) {
            cleanText += "。";
        }

        return cleanText;
    }

    /**
     * 停止TTS播放
     */
    private void stopTTSPlayback() {
        if (ttsService != null && ttsService.isSpeaking()) {
            Log.d("ChatActivity", "停止TTS播放");
            ttsService.stopSynthesis();
        }
    }

    /**
     * 切换TTS自动播放模式
     */
    public void toggleAutoPlayTTS() {
        autoPlayTTS = !autoPlayTTS;
        String status = autoPlayTTS ? "已开启" : "已关闭";
        Toast.makeText(this, "自动语音播放" + status, Toast.LENGTH_SHORT).show();
    }

    private void startAzureSpeechRecognition() {
        if (isRecognizing) return;

        try {
            SpeechConfig speechConfig = SpeechConfig.fromSubscription(getSpeechSubscriptionKey(this), getSpeechRegion(this));
            speechConfig.setSpeechRecognitionLanguage("zh-CN");
            AudioConfig audioConfig = AudioConfig.fromDefaultMicrophoneInput();

            speechRecognizer = new SpeechRecognizer(speechConfig, audioConfig);
            isRecognizing = true;
            setVoiceButtonEnabled(false);

            // 识别中实时结果（可选）
            speechRecognizer.recognizing.addEventListener((s, e) -> {
                String partial = e.getResult().getText();
                if (partial != null && !partial.isEmpty()) {
                    mainHandler.post(() -> messageInput.setText(partial));
                }
            });

            // 识别完成
            speechRecognizer.recognized.addEventListener((s, e) -> {
                if (e.getResult().getReason() == ResultReason.RecognizedSpeech) {
                    String recognizedText = e.getResult().getText();
                    if (recognizedText != null && !recognizedText.isEmpty()) {
                        mainHandler.post(() -> {
                            messageInput.setText(recognizedText);
                            sendButton.performClick();
                        });
                    }
                } else if (e.getResult().getReason() == ResultReason.NoMatch) {
                    mainHandler.post(() -> Toast.makeText(this, "未识别到语音内容", Toast.LENGTH_SHORT).show());
                }
                stopAzureSpeechRecognition();
            });

            // 取消事件
            speechRecognizer.canceled.addEventListener((s, e) -> {
                stopAzureSpeechRecognition();
                mainHandler.post(() -> Toast.makeText(this, "语音识别取消或出错: " + e.getErrorDetails(), Toast.LENGTH_SHORT).show());
            });

            // 会话停止
            speechRecognizer.sessionStopped.addEventListener((s, e) -> stopAzureSpeechRecognition());

            speechRecognizer.startContinuousRecognitionAsync();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "启动语音识别失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            setVoiceButtonEnabled(true);
            isRecognizing = false;
        }
    }

    private void stopAzureSpeechRecognition() {
        if (speechRecognizer != null && isRecognizing) {
            try {
                speechRecognizer.stopContinuousRecognitionAsync();
                speechRecognizer.close();
                speechRecognizer = null;
            } catch (Exception ignored) {
            }
        }
        isRecognizing = false;
        mainHandler.post(() -> setVoiceButtonEnabled(true));
    }

    private void setVoiceButtonEnabled(boolean enabled) {
        voiceButton.setEnabled(enabled);
        voiceButton.setAlpha(enabled ? 1f : 0.5f);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startAzureSpeechRecognition();
            } else {
                Toast.makeText(this, "录音权限被拒绝，无法使用语音输入", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void appendMessage(String message) {
        if (message == null) message = "";
        chatTextView.append(message + "\n\n");
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
        stopAzureSpeechRecognition();

        // 释放TTS资源
        if (ttsService != null) {
            ttsService.destroy();
            ttsService = null;
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}