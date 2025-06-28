package com.example.create_part2.activity_test;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.create_part2.MicrosoftTTS;
import com.example.create_part2.R;

/**
 * TTS测试页面
 */
public class TTSActivity extends AppCompatActivity implements MicrosoftTTS.TTSCallback {

    private EditText etInputText;
    private Button btnSpeak;
    private Button btnStop;
    private Button btnTest;
    private TextView tvStatus;

    private MicrosoftTTS ttsService;

    private static final int PERMISSION_REQUEST_CODE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 隐藏ActionBar
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        setContentView(R.layout.dialog_tts_test);

        initViews();
        checkPermissions();
    }

    /**
     * 检查权限
     */
    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.INTERNET}, PERMISSION_REQUEST_CODE);
        } else {
            initTTSService();
            setupClickListeners();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initTTSService();
                setupClickListeners();
            } else {
                Toast.makeText(this, "需要网络权限才能使用TTS功能", Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * 初始化视图
     */
    private void initViews() {
        etInputText = findViewById(R.id.etInputText);
        btnSpeak = findViewById(R.id.btnSpeak);
        btnStop = findViewById(R.id.btnStop);
        btnTest = findViewById(R.id.btnTest);
        tvStatus = findViewById(R.id.tvStatus);

        // 设置默认文本
        etInputText.setText("你好，我是老灯，您的车载语音备忘录助手！");
        tvStatus.setText("准备就绪");
    }

    /**
     * 初始化TTS服务
     */
    private void initTTSService() {
        ttsService = new MicrosoftTTS(this);
        ttsService.setCallback(this);
    }

    /**
     * 设置点击监听器
     */
    private void setupClickListeners() {
        // 开始语音合成按钮
        btnSpeak.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = etInputText.getText().toString().trim();
                if (text.isEmpty()) {
                    Toast.makeText(TTSActivity.this, "请输入要转换的文字", Toast.LENGTH_SHORT).show();
                    return;
                }

                ttsService.speak(text);
            }
        });

        // 停止语音合成按钮
        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ttsService.stopSynthesis();
            }
        });

        // 测试配置按钮
        btnTest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ttsService.testConfiguration();
            }
        });
    }

    // TTS回调方法实现
    @Override
    public void onSynthesisStarted() {
        runOnUiThread(() -> {
            tvStatus.setText("正在合成语音...");
            btnSpeak.setEnabled(false);
            btnStop.setEnabled(true);
        });
    }

    @Override
    public void onSynthesisCompleted() {
        runOnUiThread(() -> {
            tvStatus.setText("语音播放完成");
            btnSpeak.setEnabled(true);
            btnStop.setEnabled(false);
        });
    }

    @Override
    public void onSynthesisCanceled() {
        runOnUiThread(() -> {
            tvStatus.setText("语音合成已停止");
            btnSpeak.setEnabled(true);
            btnStop.setEnabled(false);
        });
    }

    @Override
    public void onError(String errorMessage) {
        runOnUiThread(() -> {
            tvStatus.setText("错误: " + errorMessage);
            btnSpeak.setEnabled(true);
            btnStop.setEnabled(false);
            Toast.makeText(TTSActivity.this, errorMessage, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onStatusUpdate(String status) {
        runOnUiThread(() -> {
            tvStatus.setText(status);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ttsService != null) {
            ttsService.destroy();
        }
    }
}