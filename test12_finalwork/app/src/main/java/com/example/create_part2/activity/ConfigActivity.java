package com.example.create_part2.activity;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.create_part2.ConfigManager;
import com.example.create_part2.R;

/**
 * API配置设置界面
 * 用于安全地配置DeepSeek和Microsoft语音服务的API密钥
 */
public class ConfigActivity extends AppCompatActivity {
    
    private static final String TAG = "ConfigActivity";
    
    private EditText deepSeekApiKeyInput;
    private EditText speechSubscriptionKeyInput;
    private EditText speechRegionInput;
    private Button saveButton;
    private Button clearButton;
    private Button testButton;
    
    private ConfigManager configManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config);
        
        configManager = ConfigManager.getInstance(this);
        
        initializeViews();
        loadCurrentConfig();
        setupListeners();
    }
    
    private void initializeViews() {
        deepSeekApiKeyInput = findViewById(R.id.deepseek_api_key_input);
        speechSubscriptionKeyInput = findViewById(R.id.speech_subscription_key_input);
        speechRegionInput = findViewById(R.id.speech_region_input);
        saveButton = findViewById(R.id.save_button);
        clearButton = findViewById(R.id.clear_button);
        testButton = findViewById(R.id.test_button);
        
        // 返回按钮
        Button backButton = findViewById(R.id.back_button);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }
    }
    
    private void loadCurrentConfig() {
        // 加载当前配置（不显示完整密钥，只显示前几位和后几位）
        String deepSeekKey = configManager.getDeepSeekApiKey();
        if (!TextUtils.isEmpty(deepSeekKey)) {
            String maskedKey = maskApiKey(deepSeekKey);
            deepSeekApiKeyInput.setText(maskedKey);
            deepSeekApiKeyInput.setHint("已配置（点击重新输入）");
        } else {
            deepSeekApiKeyInput.setHint("请输入DeepSeek API密钥");
        }
        
        String speechKey = configManager.getSpeechSubscriptionKey();
        if (!TextUtils.isEmpty(speechKey)) {
            String maskedKey = maskApiKey(speechKey);
            speechSubscriptionKeyInput.setText(maskedKey);
            speechSubscriptionKeyInput.setHint("已配置（点击重新输入）");
        } else {
            speechSubscriptionKeyInput.setHint("请输入Microsoft语音服务密钥");
        }
        
        speechRegionInput.setText(configManager.getSpeechRegion());
    }
    
    private void setupListeners() {
        saveButton.setOnClickListener(v -> saveConfig());
        clearButton.setOnClickListener(v -> clearConfig());
        testButton.setOnClickListener(v -> testConfig());
        
        // 点击输入框时清空内容，方便重新输入
        deepSeekApiKeyInput.setOnClickListener(v -> {
            if (deepSeekApiKeyInput.getText().toString().contains("***")) {
                deepSeekApiKeyInput.setText("");
                deepSeekApiKeyInput.setHint("请输入DeepSeek API密钥");
            }
        });
        
        speechSubscriptionKeyInput.setOnClickListener(v -> {
            if (speechSubscriptionKeyInput.getText().toString().contains("***")) {
                speechSubscriptionKeyInput.setText("");
                speechSubscriptionKeyInput.setHint("请输入Microsoft语音服务密钥");
            }
        });
    }
    
    private void saveConfig() {
        String deepSeekKey = deepSeekApiKeyInput.getText().toString().trim();
        String speechKey = speechSubscriptionKeyInput.getText().toString().trim();
        String speechRegion = speechRegionInput.getText().toString().trim();
        
        // 验证输入
        if (TextUtils.isEmpty(deepSeekKey) || deepSeekKey.contains("***")) {
            Toast.makeText(this, "请输入有效的DeepSeek API密钥", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (TextUtils.isEmpty(speechKey) || speechKey.contains("***")) {
            Toast.makeText(this, "请输入有效的Microsoft语音服务密钥", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (TextUtils.isEmpty(speechRegion)) {
            speechRegion = "eastasia"; // 默认区域
        }
        
        // 保存配置
        configManager.setDeepSeekApiKey(deepSeekKey);
        configManager.setSpeechSubscriptionKey(speechKey);
        configManager.setSpeechRegion(speechRegion);
        
        Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show();
        Log.i(TAG, "API配置已保存");
        
        // 重新加载显示
        loadCurrentConfig();
    }
    
    private void clearConfig() {
        configManager.clearAllConfig();
        Toast.makeText(this, "配置已清除", Toast.LENGTH_SHORT).show();
        Log.i(TAG, "API配置已清除");
        loadCurrentConfig();
    }
    
    private void testConfig() {
        if (!configManager.isConfigComplete()) {
            Toast.makeText(this, "请先配置完整的API密钥", Toast.LENGTH_SHORT).show();
            return;
        }
        
        Toast.makeText(this, "配置测试功能开发中...", Toast.LENGTH_SHORT).show();
        // TODO: 实现配置测试功能
    }
    
    /**
     * 掩码API密钥，只显示前几位和后几位
     */
    private String maskApiKey(String apiKey) {
        if (TextUtils.isEmpty(apiKey) || apiKey.length() < 8) {
            return apiKey;
        }
        
        int visibleLength = 4;
        String prefix = apiKey.substring(0, visibleLength);
        String suffix = apiKey.substring(apiKey.length() - visibleLength);
        return prefix + "***" + suffix;
    }
} 