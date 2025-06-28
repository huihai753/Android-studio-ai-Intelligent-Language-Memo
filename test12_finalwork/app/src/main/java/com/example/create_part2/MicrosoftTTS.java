package com.example.create_part2;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import com.microsoft.cognitiveservices.speech.AudioDataStream;
import com.microsoft.cognitiveservices.speech.Connection;
import com.microsoft.cognitiveservices.speech.PropertyId;
import com.microsoft.cognitiveservices.speech.SpeechConfig;
import com.microsoft.cognitiveservices.speech.SpeechSynthesisOutputFormat;
import com.microsoft.cognitiveservices.speech.SpeechSynthesisResult;
import com.microsoft.cognitiveservices.speech.SpeechSynthesizer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Microsoft Azure文字转语音服务封装类
 * 负责处理所有与Microsoft TTS相关的功能
 */
public class MicrosoftTTS {
    private static final String TAG = "MicrosoftTTS";

    // Microsoft语音服务配置 - 通过ConfigManager获取
    private static String getSpeechSubscriptionKey(Context context) {
        return ConfigManager.getInstance(context).getSpeechSubscriptionKey();
    }
    
    private static String getSpeechRegion(Context context) {
        return ConfigManager.getInstance(context).getSpeechRegion();
    }

    // 语音合成组件
    private SpeechConfig speechConfig;
    private SpeechSynthesizer synthesizer;
    private Connection connection;
    private Context context;

    // 音频播放组件
    private AudioTrack audioTrack;
    private ExecutorService singleThreadExecutor;

    // 控制变量
    private boolean isSpeaking = false;
    private boolean stopped = false;
    private final Object synchronizedObj = new Object();

    // 回调接口
    private TTSCallback callback;

    /**
     * TTS回调接口
     */
    public interface TTSCallback {
        void onSynthesisStarted();
        void onSynthesisCompleted();
        void onSynthesisCanceled();
        void onError(String errorMessage);
        void onStatusUpdate(String status);
    }

    /**
     * 构造函数
     */
    public MicrosoftTTS(Context context) {
        this.context = context;
        initializeSpeechService();
    }

    /**
     * 设置回调接口
     */
    public void setCallback(TTSCallback callback) {
        this.callback = callback;
    }

    /**
     * 初始化语音服务组件
     */
    private void initializeSpeechService() {
        try {
            Log.d(TAG, "初始化Microsoft TTS配置");

            // 创建语音配置
            speechConfig = SpeechConfig.fromSubscription(getSpeechSubscriptionKey(context), getSpeechRegion(context));

            // 配置音频播放
            setupAudioTrack();

            // 配置语音合成
            setupSpeechSynthesizer();

            // 初始化线程执行器
            singleThreadExecutor = Executors.newSingleThreadExecutor();

            Log.d(TAG, "TTS配置初始化完成");
        } catch (Exception e) {
            Log.e(TAG, "初始化TTS配置失败: " + e.getMessage(), e);
            if (callback != null) {
                callback.onError("TTS配置初始化失败: " + e.getMessage());
            }
        }
    }

    /**
     * 配置音频播放组件
     */
    private void setupAudioTrack() {
        audioTrack = new AudioTrack(
                new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(24000)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                AudioTrack.getMinBufferSize(
                        24000,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT) * 2,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE);
    }

    /**
     * 配置语音合成器
     */
    private void setupSpeechSynthesizer() {
        // 设置语音合成参数
        speechConfig.setSpeechSynthesisOutputFormat(SpeechSynthesisOutputFormat.Raw24Khz16BitMonoPcm);
        speechConfig.setSpeechSynthesisLanguage("zh-CN");
        speechConfig.setSpeechSynthesisVoiceName("zh-CN-XiaoxiaoMultilingualNeural");

        // 创建语音合成器
        synthesizer = new SpeechSynthesizer(speechConfig, null);
        connection = Connection.fromSpeechSynthesizer(synthesizer);
        connection.openConnection(true);

        // 注册合成完成事件
        synthesizer.SynthesisCompleted.addEventListener((o, e) -> {
            Log.i(TAG, "语音合成完成");
            Log.i(TAG, "首字节延迟: " +
                    e.getResult().getProperties().getProperty(PropertyId.SpeechServiceResponse_SynthesisFirstByteLatencyMs) + " ms");
            Log.i(TAG, "完成延迟: " +
                    e.getResult().getProperties().getProperty(PropertyId.SpeechServiceResponse_SynthesisFinishLatencyMs) + " ms");
            e.close();
        });
    }

    /**
     * 简单的文字转语音方法 - 只需要输入文本即可播放
     * @param text 要转换为语音的文本
     */
    public void speak(String text) {
        if (text == null || text.trim().isEmpty()) {
            Log.w(TAG, "文本为空，无法合成语音");
            if (callback != null) {
                callback.onError("文本不能为空");
            }
            return;
        }

        if (isSpeaking) {
            Log.w(TAG, "语音合成正在进行中，停止当前合成");
            stopSynthesis();
        }

        Log.d(TAG, "开始合成语音: " + text);
        isSpeaking = true;

        if (callback != null) {
            callback.onSynthesisStarted();
            callback.onStatusUpdate("正在合成语音...");
        }

        SpeakingRunnable speakingRunnable = new SpeakingRunnable();
        speakingRunnable.setContent(text);
        speakingRunnable.setCallback(() -> {
            isSpeaking = false;
            if (callback != null) {
                callback.onSynthesisCompleted();
                callback.onStatusUpdate("语音播放完成");
            }
        });

        singleThreadExecutor.execute(speakingRunnable);
    }

    /**
     * 停止语音合成
     */
    public void stopSynthesis() {
        if (!isSpeaking) {
            Log.w(TAG, "没有正在进行的语音合成");
            return;
        }

        Log.d(TAG, "停止语音合成");
        stopped = true;
        isSpeaking = false;

        if (callback != null) {
            callback.onSynthesisCanceled();
            callback.onStatusUpdate("语音合成已停止");
        }
    }

    /**
     * 测试TTS服务配置
     */
    public void testConfiguration() {
        Log.d(TAG, "开始测试TTS服务配置");

        if (callback != null) {
            callback.onStatusUpdate("正在测试TTS配置...");
        }

        // 测试简单的文本合成
        speak("TTS配置测试成功，语音合成功能正常");
    }

    /**
     * 检查是否正在合成语音
     */
    public boolean isSpeaking() {
        return isSpeaking;
    }

    /**
     * 销毁服务
     */
    public void destroy() {
        Log.d(TAG, "销毁Microsoft TTS服务");

        if (isSpeaking) {
            stopSynthesis();
        }

        // 释放语音合成资源
        if (synthesizer != null) {
            synthesizer.close();
            synthesizer = null;
        }

        if (connection != null) {
            connection.close();
            connection = null;
        }

        if (speechConfig != null) {
            speechConfig.close();
            speechConfig = null;
        }

        // 释放音频播放资源
        if (audioTrack != null) {
            if (singleThreadExecutor != null) {
                singleThreadExecutor.shutdownNow();
            }
            audioTrack.flush();
            audioTrack.stop();
            audioTrack.release();
            audioTrack = null;
        }
    }

    /**
     * 回调接口
     */
    interface ICallback {
        void Callback();
    }

    /**
     * 语音合成运行线程
     */
    class SpeakingRunnable implements Runnable {
        private String content;
        private ICallback _callback;

        public void setContent(String content) {
            this.content = content;
        }

        public void setCallback(ICallback callback) {
            _callback = callback;
        }

        @Override
        public void run() {
            try {
                // 开始播放
                audioTrack.play();

                synchronized (synchronizedObj) {
                    stopped = false;
                }

                // 使用SSML格式设置语音参数
                String ssmlContent =
                        "<speak xmlns=\"http://www.w3.org/2001/10/synthesis\" " +
                                "xmlns:mstts=\"http://www.w3.org/2001/mstts\" " +
                                "xmlns:emo=\"http://www.w3.org/2009/10/emotionml\" " +
                                "version=\"1.0\" xml:lang=\"zh-CN\">" +
                                "<voice name=\"zh-CN-XiaoxiaoMultilingualNeural\">" +
                                "<lang xml:lang=\"zh-CN\">" +
                                "<prosody rate=\"17%\">" + content.replace("*", " ") + "</prosody>" +
                                "</lang></voice></speak>";

                // 合成语音
                SpeechSynthesisResult result = synthesizer.StartSpeakingSsmlAsync(ssmlContent).get();
                AudioDataStream audioDataStream = AudioDataStream.fromResult(result);

                // 设置缓冲区大小(50ms)
                byte[] buffer = new byte[2400];  // 24000 * 16 * 0.05 / 8 = 2400

                // 播放语音流
                while (!stopped) {
                    long len = audioDataStream.readData(buffer);
                    if (len == 0) {
                        break;
                    }
                    audioTrack.write(buffer, 0, (int) len);
                }

                // 关闭音频流
                audioDataStream.close();
                result.close();

                // 处理完成回调
                if (_callback != null && !stopped) {
                    _callback.Callback();
                }
            } catch (Exception ex) {
                Log.e(TAG, "语音合成出错: " + ex.getMessage());
                ex.printStackTrace();
                isSpeaking = false;
                if (callback != null) {
                    callback.onError("语音合成失败: " + ex.getMessage());
                }
            }
        }
    }
}