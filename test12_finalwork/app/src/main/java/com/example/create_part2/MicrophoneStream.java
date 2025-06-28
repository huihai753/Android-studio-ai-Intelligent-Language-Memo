package com.example.create_part2;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.microsoft.cognitiveservices.speech.audio.PullAudioInputStreamCallback;
import com.microsoft.cognitiveservices.speech.audio.AudioStreamFormat;

/**
 * MicrophoneStream 类将Android麦克风作为PullAudioInputStreamCallback暴露给Speech SDK使用
 * 配置参数：16kHz采样率、16位采样深度、单声道
 * 该类负责初始化麦克风、读取音频数据以及释放资源
 */
public class MicrophoneStream extends PullAudioInputStreamCallback {

    private static final String TAG = "MicrophoneStream";
    private static final int SAMPLE_RATE = 16000;

    private final AudioStreamFormat format;
    private AudioRecord recorder;
    private final Context context;
    private boolean hasPermission;
    private boolean isRecording = false;

    public MicrophoneStream(Context context) {
        this.context = context;
        this.format = AudioStreamFormat.getWaveFormatPCM(SAMPLE_RATE, (short)16, (short)1);
        checkPermissionAndInitMic();
    }

    private void checkPermissionAndInitMic() {
        hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
        if (hasPermission) {
            initMic();
        } else {
            Log.e(TAG, "没有麦克风录音权限，请在应用设置中授予权限");
        }
    }

    private void initMic() {
        try {
            // 计算缓冲区大小
            int bufferSize = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
            );

            if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "无法获取有效的缓冲区大小");
                hasPermission = false;
                return;
            }

            // 使用推荐缓冲区大小的2倍
            bufferSize *= 2;

            AudioFormat audioFormat = new AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build();

            this.recorder = new AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(bufferSize)
                    .build();

            // 检查录音器状态
            if (this.recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord初始化失败");
                hasPermission = false;
                return;
            }

            // 开始录音
            this.recorder.startRecording();
            isRecording = true;
            Log.i(TAG, "麦克风初始化成功，开始录音");

        } catch (SecurityException e) {
            Log.e(TAG, "没有麦克风录音权限: " + e.getMessage());
            hasPermission = false;
        } catch (Exception e) {
            Log.e(TAG, "初始化麦克风时出错: " + e.getMessage());
            hasPermission = false;
        }
    }

    public AudioStreamFormat getFormat() {
        return this.format;
    }

    @Override
    public int read(byte[] bytes) {
        if (this.recorder != null && hasPermission && isRecording) {
            try {
                int bytesRead = this.recorder.read(bytes, 0, bytes.length);
                if (bytesRead > 0) {
                    return bytesRead;
                } else {
                    Log.w(TAG, "读取音频数据失败，返回值: " + bytesRead);
                    return 0;
                }
            } catch (SecurityException e) {
                Log.e(TAG, "读取麦克风数据时缺少权限: " + e.getMessage());
                return 0;
            } catch (Exception e) {
                Log.e(TAG, "读取音频数据时发生异常: " + e.getMessage());
                return 0;
            }
        }
        return 0;
    }

    @Override
    public void close() {
        Log.i(TAG, "关闭麦克风资源");
        if (this.recorder != null) {
            try {
                if (isRecording) {
                    this.recorder.stop();
                    isRecording = false;
                    Log.d(TAG, "停止录音");
                }
                this.recorder.release();
                Log.d(TAG, "释放录音器资源");
            } catch (Exception e) {
                Log.e(TAG, "关闭麦克风时出错: " + e.getMessage());
            } finally {
                this.recorder = null;
            }
        }
    }

    public boolean hasAudioPermission() {
        return hasPermission;
    }

    // 重新初始化麦克风（当权限状态改变时使用）
    public void reinitialize() {
        close();
        checkPermissionAndInitMic();
    }
}
