package com.example.create_part2;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * 配置管理类
 * 用于安全地管理API密钥和配置信息
 */
public class ConfigManager {
    private static final String TAG = "ConfigManager";
    private static final String PREF_NAME = "api_config";
    
    // 配置键名
    private static final String KEY_DEEPSEEK_API_KEY = "deepseek_api_key";
    private static final String KEY_SPEECH_SUBSCRIPTION_KEY = "speech_subscription_key";
    private static final String KEY_SPEECH_REGION = "speech_region";
    
    // 默认值
    private static final String DEFAULT_DEEPSEEK_API_URL = "https://api.deepseek.com/chat/completions";
    private static final String DEFAULT_DEEPSEEK_MODEL_NAME = "deepseek-chat";
    private static final String DEFAULT_SPEECH_REGION = "eastasia";
    
    private static ConfigManager instance;
    private SharedPreferences preferences;
    
    private ConfigManager(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
    
    public static ConfigManager getInstance(Context context) {
        if (instance == null) {
            synchronized (ConfigManager.class) {
                if (instance == null) {
                    instance = new ConfigManager(context);
                }
            }
        }
        return instance;
    }
    
    /**
     * 设置DeepSeek API密钥
     */
    public void setDeepSeekApiKey(String apiKey) {
        preferences.edit().putString(KEY_DEEPSEEK_API_KEY, apiKey).apply();
        Log.d(TAG, "DeepSeek API密钥已设置");
    }
    
    /**
     * 获取DeepSeek API密钥
     */
    public String getDeepSeekApiKey() {
        return preferences.getString(KEY_DEEPSEEK_API_KEY, "");
    }
    
    /**
     * 设置语音服务密钥
     */
    public void setSpeechSubscriptionKey(String key) {
        preferences.edit().putString(KEY_SPEECH_SUBSCRIPTION_KEY, key).apply();
        Log.d(TAG, "语音服务密钥已设置");
    }
    
    /**
     * 获取语音服务密钥
     */
    public String getSpeechSubscriptionKey() {
        return preferences.getString(KEY_SPEECH_SUBSCRIPTION_KEY, "");
    }
    
    /**
     * 设置语音服务区域
     */
    public void setSpeechRegion(String region) {
        preferences.edit().putString(KEY_SPEECH_REGION, region).apply();
        Log.d(TAG, "语音服务区域已设置: " + region);
    }
    
    /**
     * 获取语音服务区域
     */
    public String getSpeechRegion() {
        return preferences.getString(KEY_SPEECH_REGION, DEFAULT_SPEECH_REGION);
    }
    
    /**
     * 获取DeepSeek API URL
     */
    public String getDeepSeekApiUrl() {
        return DEFAULT_DEEPSEEK_API_URL;
    }
    
    /**
     * 获取DeepSeek模型名称
     */
    public String getDeepSeekModelName() {
        return DEFAULT_DEEPSEEK_MODEL_NAME;
    }
    
    /**
     * 检查配置是否完整
     */
    public boolean isConfigComplete() {
        boolean hasDeepSeek = !getDeepSeekApiKey().isEmpty();
        boolean hasSpeech = !getSpeechSubscriptionKey().isEmpty();
        
        Log.d(TAG, "配置检查 - DeepSeek: " + hasDeepSeek + ", Speech: " + hasSpeech);
        return hasDeepSeek && hasSpeech;
    }
    
    /**
     * 清除所有配置
     */
    public void clearAllConfig() {
        preferences.edit().clear().apply();
        Log.d(TAG, "所有配置已清除");
    }
} 