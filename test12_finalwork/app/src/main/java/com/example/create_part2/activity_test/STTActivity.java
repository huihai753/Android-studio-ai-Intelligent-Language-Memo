package com.example.create_part2.activity_test;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.create_part2.MicrophoneStream;
import com.example.create_part2.R;
import com.example.create_part2.activity.MainActivity;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;

import com.microsoft.cognitiveservices.speech.CancellationDetails;
import com.microsoft.cognitiveservices.speech.CancellationReason;
import com.microsoft.cognitiveservices.speech.ResultReason;
import com.microsoft.cognitiveservices.speech.SpeechConfig;
import com.microsoft.cognitiveservices.speech.SpeechRecognizer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.example.create_part2.BuildConfig;
import com.example.create_part2.ConfigManager;

public class STTActivity extends AppCompatActivity {

    private static final String LOG_TAG = "STT";
    
    // 语音识别相关 - 通过ConfigManager获取
    private static String getSpeechSubscriptionKey(Context context) {
        return ConfigManager.getInstance(context).getSpeechSubscriptionKey();
    }
    
    private static String getSpeechRegion(Context context) {
        return ConfigManager.getInstance(context).getSpeechRegion();
    }

    private ExecutorService executorService = Executors.newCachedThreadPool();

    private SpeechConfig speechConfig;
    private SpeechRecognizer recognizer;
    private AudioConfig audioInput;
    private MicrophoneStream microphoneStream;

    private TextView recognizedTextView;
    private TextView statusTextView;
    private TextView testResultText;
    private Button recognitionButton;
    private Button testConfigButton;
    private ScrollView scrollView;

    private boolean isRecognizing = false;
    private boolean isDestroying = false;
    private boolean isConfigTested = false;
    private final StringBuilder allRecognizedText = new StringBuilder();

    private ActivityResultLauncher<String[]> permissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_speech_recognition);

        Log.i(LOG_TAG, "=== STTActivity onCreate 开始 ===");

        initializeUI();
        setupPermissionLauncher();

        if (checkAndRequestPermissions()) {
            initializeSpeechService();
        }

        Log.i(LOG_TAG, "=== STTActivity onCreate 完成 ===");
    }

    private void initializeUI() {
        Log.d(LOG_TAG, "初始化UI组件");

        recognizedTextView = findViewById(R.id.recognizedText);
        statusTextView = findViewById(R.id.statusText);
        testResultText = findViewById(R.id.testResultText);
        recognitionButton = findViewById(R.id.startRecognitionButton);
        testConfigButton = findViewById(R.id.testConfigButton);
        scrollView = findViewById(R.id.scrollView);

        if (recognizedTextView == null || statusTextView == null ||
                recognitionButton == null || scrollView == null ||
                testResultText == null || testConfigButton == null) {
            Log.e(LOG_TAG, "UI组件初始化失败，某些视图为null");
            return;
        }

        // 测试配置按钮
        testConfigButton.setOnClickListener(v -> testSpeechServiceConfig());

        // 识别按钮
        recognitionButton.setOnClickListener(v -> {
            if (!isConfigTested) {
                showToast("请先测试配置是否正确");
                updateTestResult("请先测试配置", Color.parseColor("#ff6b6b"));
                return;
            }

            if (isRecognizing) {
                stopRecognition();
            } else {
                if (checkPermission(Manifest.permission.RECORD_AUDIO)) {
                    startContinuousRecognition();
                } else {
                    showToast("需要麦克风权限才能进行语音识别");
                    checkAndRequestPermissions();
                }
            }
        });

        // 返回按钮
        Button returnButton = findViewById(R.id.buttonreturn);
        if (returnButton != null) {
            returnButton.setOnClickListener(v -> forceStopAndReturn());
        }

        // 初始状态下禁用识别按钮
        recognitionButton.setEnabled(false);
        recognitionButton.setAlpha(0.6f);
    }

    private void setupPermissionLauncher() {
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean allGranted = true;
                    for (Boolean granted : result.values()) {
                        if (!granted) {
                            allGranted = false;
                            break;
                        }
                    }
                    if (allGranted) {
                        Log.i(LOG_TAG, "所有权限已授予，重新初始化语音服务");
                        initializeSpeechService();
                        // 重新初始化麦克风流
                        if (microphoneStream != null) {
                            microphoneStream.reinitialize();
                        }
                    } else {
                        showToast("缺少必要权限，语音识别功能将不可用");
                        setStatusText("权限被拒绝，无法启动语音识别");
                    }
                }
        );
    }

    private boolean checkAndRequestPermissions() {
        String[] permissions = {Manifest.permission.RECORD_AUDIO, Manifest.permission.INTERNET};
        boolean allGranted = true;

        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (!allGranted) {
            permissionLauncher.launch(permissions);
            return false;
        }
        return true;
    }

    private boolean checkPermission(String permission) {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    private void initializeSpeechService() {
        try {
            speechConfig = SpeechConfig.fromSubscription(getSpeechSubscriptionKey(this), getSpeechRegion(this));
            speechConfig.setSpeechRecognitionLanguage("zh-CN");

            // 初始化自定义麦克风流
            microphoneStream = new MicrophoneStream(this);

            setStatusText("语音服务初始化完成，请先测试配置");
            Log.i(LOG_TAG, "语音服务初始化完成");
        } catch (Exception ex) {
            Log.e(LOG_TAG, "初始化语音服务失败: " + ex.getMessage(), ex);
            displayException(ex);
        }
    }

    private void testSpeechServiceConfig() {
        Log.i(LOG_TAG, "开始测试语音服务配置");

        testConfigButton.setEnabled(false);
        testConfigButton.setText("测试中...");
        updateTestResult("正在测试配置...", Color.parseColor("#007bff"));

        executorService.submit(() -> {
            try {
                // 检查麦克风权限和状态
                if (microphoneStream != null && !microphoneStream.hasAudioPermission()) {
                    runOnUiThread(() -> {
                        updateTestResult("✗ 麦克风权限未授予", Color.parseColor("#dc3545"));
                        showToast("测试失败：缺少麦克风权限");
                        resetTestButton();
                    });
                    return;
                }

                // 使用默认麦克风输入进行测试 - 修复编译错误
                AudioConfig testAudioInput = AudioConfig.fromDefaultMicrophoneInput();
                SpeechRecognizer testRecognizer = new SpeechRecognizer(speechConfig, testAudioInput);

                boolean[] testCompleted = {false};

                testRecognizer.sessionStarted.addEventListener((o, args) -> {
                    Log.i(LOG_TAG, "测试会话开始");
                    if (!testCompleted[0]) {
                        testCompleted[0] = true;
                        runOnUiThread(() -> {
                            updateTestResult("✓ 测试成功", Color.parseColor("#28a745"));
                            isConfigTested = true;
                            enableRecognitionButton();
                            showToast("配置测试成功！");
                            resetTestButton();
                        });

                        // 停止测试
                        executorService.submit(() -> {
                            try {
                                testRecognizer.stopContinuousRecognitionAsync();
                                Thread.sleep(500);
                                testRecognizer.close();
                                testAudioInput.close();
                            } catch (Exception e) {
                                Log.w(LOG_TAG, "清理测试资源失败: " + e.getMessage());
                            }
                        });
                    }
                });

                testRecognizer.canceled.addEventListener((o, args) -> {
                    if (!testCompleted[0]) {
                        testCompleted[0] = true;
                        if (args.getReason() == CancellationReason.Error) {
                            CancellationDetails details = CancellationDetails.fromResult(args.getResult());
                            runOnUiThread(() -> {
                                updateTestResult("✗ " + getSimplifiedErrorMessage(details.getErrorCode().toString(), details.getErrorDetails()),
                                        Color.parseColor("#dc3545"));
                                showToast("配置测试失败");
                                resetTestButton();
                            });
                        }

                        // 清理资源
                        try {
                            testRecognizer.close();
                            testAudioInput.close();
                        } catch (Exception e) {
                            Log.w(LOG_TAG, "清理测试资源失败: " + e.getMessage());
                        }
                    }
                });

                // 开始测试
                testRecognizer.startContinuousRecognitionAsync();

                // 超时处理
                Thread.sleep(10000); // 10秒超时
                if (!testCompleted[0]) {
                    testCompleted[0] = true;
                    runOnUiThread(() -> {
                        updateTestResult("✗ 测试超时", Color.parseColor("#ffc107"));
                        showToast("测试超时，请检查网络连接");
                        resetTestButton();
                    });

                    try {
                        testRecognizer.stopContinuousRecognitionAsync();
                        testRecognizer.close();
                        testAudioInput.close();
                    } catch (Exception e) {
                        Log.w(LOG_TAG, "清理测试资源失败: " + e.getMessage());
                    }
                }

            } catch (Exception e) {
                Log.e(LOG_TAG, "测试配置异常: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    updateTestResult("✗ 配置错误", Color.parseColor("#dc3545"));
                    showToast("配置测试失败：" + e.getMessage());
                    resetTestButton();
                });
            }
        });
    }

    private void resetTestButton() {
        testConfigButton.setEnabled(true);
        testConfigButton.setText("测试配置");
    }

    private String getSimplifiedErrorMessage(String errorCode, String errorDetails) {
        if (errorCode.contains("AuthenticationFailure")) {
            return "认证失败，请检查密钥和区域";
        } else if (errorCode.contains("ConnectionFailure")) {
            return "网络连接失败";
        } else if (errorCode.contains("ServiceTimeout")) {
            return "服务超时";
        } else if (errorDetails.contains("401")) {
            return "密钥无效";
        } else if (errorDetails.contains("403")) {
            return "权限不足";
        } else if (errorDetails.contains("404")) {
            return "服务不存在";
        } else {
            return "配置错误";
        }
    }

    private void updateTestResult(String message, int color) {
        if (testResultText != null) {
            testResultText.setText(message);
            testResultText.setTextColor(color);
        }
    }

    private void enableRecognitionButton() {
        if (recognitionButton != null) {
            recognitionButton.setEnabled(true);
            recognitionButton.setAlpha(1.0f);
        }
    }

    private void startContinuousRecognition() {
        Log.i(LOG_TAG, "开始持续语音识别");

        try {
            // 使用默认麦克风输入 - 修复编译错误
            audioInput = AudioConfig.fromDefaultMicrophoneInput();
            recognizer = new SpeechRecognizer(speechConfig, audioInput);

            setupRecognitionEvents();

            recognizer.startContinuousRecognitionAsync();

            isRecognizing = true;
            runOnUiThread(() -> {
                recognitionButton.setText("停止识别");
                setStatusText("识别已启动，请开始说话...");
            });

        } catch (Exception ex) {
            Log.e(LOG_TAG, "启动持续识别异常: " + ex.getMessage(), ex);
            displayException(ex);
            isRecognizing = false;
        }
    }

    private void setupRecognitionEvents() {
        recognizer.recognizing.addEventListener((o, args) -> {
            if (!isDestroying && isRecognizing) {
                String intermediateText = args.getResult().getText();
                if (!TextUtils.isEmpty(intermediateText)) {
                    runOnUiThread(() -> setStatusText("识别中: " + intermediateText));
                }
            }
        });

        recognizer.recognized.addEventListener((o, args) -> {
            if (!isDestroying && isRecognizing) {
                if (args.getResult().getReason() == ResultReason.RecognizedSpeech) {
                    String recognizedText = args.getResult().getText();
                    if (!TextUtils.isEmpty(recognizedText)) {
                        addRecognizedText(recognizedText);
                        runOnUiThread(() -> setStatusText("识别完成，继续监听..."));
                    }
                }
            }
        });

        recognizer.canceled.addEventListener((o, args) -> {
            Log.e(LOG_TAG, "识别被取消/出错: " + args.getReason());
            if (args.getReason() == CancellationReason.Error) {
                CancellationDetails details = CancellationDetails.fromResult(args.getResult());
                runOnUiThread(() -> {
                    String errorMsg = "识别错误: " + details.getErrorDetails();
                    setStatusText(errorMsg);
                    showToast(errorMsg);
                    stopRecognition();
                });
            }
        });

        recognizer.sessionStarted.addEventListener((o, args) -> {
            runOnUiThread(() -> setStatusText("识别会话已开始"));
        });

        recognizer.sessionStopped.addEventListener((o, args) -> {
            runOnUiThread(() -> setStatusText("识别会话已结束"));
        });
    }

    private void stopRecognition() {
        Log.i(LOG_TAG, "停止持续语音识别");

        if (recognizer != null && isRecognizing) {
            try {
                isRecognizing = false;
                recognizer.stopContinuousRecognitionAsync();
                closeResources();

                runOnUiThread(() -> {
                    recognitionButton.setText("开始识别");
                    setStatusText("识别已停止，点击按钮重新开始");
                });
            } catch (Exception ex) {
                Log.e(LOG_TAG, "停止识别失败: " + ex.getMessage(), ex);
                closeResources();
            }
        }
    }

    private void closeResources() {
        try {
            if (recognizer != null) {
                recognizer.close();
                recognizer = null;
            }
            if (audioInput != null) {
                audioInput.close();
                audioInput = null;
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "关闭资源时出错: " + e.getMessage(), e);
        }
    }

    private void forceStopAndReturn() {
        Log.i(LOG_TAG, "强制停止识别并返回");
        if (isRecognizing) {
            stopRecognition();
        }
        navigateToMainActivity();
    }

    private void navigateToMainActivity() {
        try {
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            finish();
        } catch (Exception e) {
            Log.e(LOG_TAG, "导航到主界面失败: " + e.getMessage(), e);
            finish();
        }
    }

    private void displayException(Exception ex) {
        runOnUiThread(() -> {
            if (!isDestroying && recognizedTextView != null) {
                String errorMessage = "错误: " + ex.getMessage();
                recognizedTextView.setText(errorMessage);
                showToast("发生错误: " + ex.getMessage());
            }
        });
    }

    private void setStatusText(final String text) {
        if (!isDestroying) {
            runOnUiThread(() -> {
                if (statusTextView != null) {
                    statusTextView.setText(text);
                }
            });
        }
    }

    private void addRecognizedText(final String text) {
        if (!TextUtils.isEmpty(text) && !isDestroying) {
            runOnUiThread(() -> {
                if (recognizedTextView != null && scrollView != null) {
                    allRecognizedText.append(text).append("\n\n");
                    recognizedTextView.setText(allRecognizedText.toString());
                    scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
                }
            });
        }
    }

    private void showToast(String message) {
        if (!isDestroying) {
            runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
        }
    }

    @Override
    protected void onDestroy() {
        Log.i(LOG_TAG, "Activity onDestroy");
        isDestroying = true;

        if (isRecognizing) {
            stopRecognition();
        }

        closeResources();

        if (microphoneStream != null) {
            microphoneStream.close();
            microphoneStream = null;
        }

        if (speechConfig != null) {
            try {
                speechConfig.close();
                speechConfig = null;
            } catch (Exception e) {
                Log.e(LOG_TAG, "关闭语音配置失败: " + e.getMessage(), e);
            }
        }

        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }

        super.onDestroy();
    }

    @Override
    protected void onPause() {
        Log.i(LOG_TAG, "Activity onPause");
        super.onPause();
        if (isRecognizing) {
            stopRecognition();
        }
    }

    @Override
    protected void onResume() {
        Log.i(LOG_TAG, "Activity onResume");
        super.onResume();
        isDestroying = false;
    }
}
