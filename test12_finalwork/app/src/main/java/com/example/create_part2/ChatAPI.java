package com.example.create_part2;

import android.content.Context;
import android.util.Log;

import com.example.create_part2.db.Note;
import com.example.create_part2.db.NoteDbHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

// 时间模块
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

//json提取
import org.json.JSONException;
import org.json.JSONObject;

//SQL处理
// 在文件顶部的导入部分添加这些
import java.text.ParseException;

/**
 * ChatAPI类负责与DeepSeek AI聊天API的通信和消息处理
 * 提供与大语言模型交互的核心功能
 */
public class ChatAPI {
    private static final String PROMPT_NAME_MISSION = "ai_prompt_missions20250623_0";
    private static final String PROMPT_NAME_CHAT = "ai_prompt_chat20250623_0";
    private static final String PROMPT_NAME_MEMO = "ai_prompt_memo20250623_0";
    private static final String PROMPT_NAME_FIXING = "ai_prompt_fixing20250620_0"; // 新增fixing prompt

    private static final String LOG_TAG = "ChatAPI";

    // Jackson对象映射器，用于JSON序列化和反序列化
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // 聊天历史记录，存储所有对话消息
    private static List<JsonNode> messages = new ArrayList<>();
    private static List<JsonNode> messages_missions = new ArrayList<>();

    // 添加状态管理
    private static boolean isInFixingMode = false; // 当前是否处于fixing追问状态
    private static String currentMode = ""; // 当前模式，用于状态管理
    private static List<MemoData> pendingMemos = new ArrayList<>(); // 存储待完善的备忘录

    // API密钥和端点 - 通过ConfigManager获取
    private static String getApiUrl(Context context) {
        return ConfigManager.getInstance(context).getDeepSeekApiUrl();
    }
    
    private static String getApiKey(Context context) {
        return "Bearer " + ConfigManager.getInstance(context).getDeepSeekApiKey();
    }

    // 模型配置参数
    private static String getModelName(Context context) {
        return ConfigManager.getInstance(context).getDeepSeekModelName();
    }
    
    private static final double TEMPERATURE = 0.6;
    private static final int MAX_TOKENS = 500;

    // 获取当前时间
    // static 静态方法，不需要创建实例对象就可以使用
    private static String getCurrentTimeFormatted() {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
        return formatter.format(new Date());
    }

    /**
     * 根据需求获取Prompt
     * @param context 上下文
     * @param name 字符串资源名称（如 "ai_prompt_test"），为null或空时用默认
     * @return 对应字符串内容
     * 用法
     * String prompt = getPromptText(context, "dsada"); // 尝试读取R.string.dsada
     * String promptDefault = getPromptText(context, null); // 读取R.string.ai_prompt_test
     */
    private static String getPromptText(Context context, String name) {
        String defaultName = "ai_prompt_test";
        String key = (name == null || name.isEmpty()) ? defaultName : name;
        int resId = context.getResources().getIdentifier(key, "string", context.getPackageName());
        if (resId == 0) { // 未找到资源，使用默认
            Log.i("ChatAPI_Prompt", "Prompt出现了问题，使用默认: " + key);
            resId = context.getResources().getIdentifier(defaultName, "string", context.getPackageName());
        }
        return context.getString(resId);
    }

    /**
     * 获取大语言模型对消息的响应
     * @param messages 对话消息历史
     * @param messageLimit 取多少条历史信息
     * @param context 上下文
     * @return AI模型返回的响应消息
     */
    public static JsonNode getLLMResponse(List<JsonNode> messages, Integer messageLimit, Context context) throws IOException {
        // 默认获取最近的20条消息
        if (messageLimit == null){
            messageLimit = 20;
        }

        // 获取最近的消息子列表  左闭右开区间
        List<JsonNode> messagesAi = messages.subList(
                Math.max(messages.size() - messageLimit, 0),
                messages.size()
        );

        // 创建消息数组节点
        ArrayNode messagesArray = objectMapper.createArrayNode();
        messagesArray.addAll(messagesAi);

        Log.i("ChatAPI", "messages: " + messagesArray);

        // 构建请求体
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", getModelName(context));
        requestBody.set("messages", messagesArray);
        requestBody.put("temperature", TEMPERATURE);
        requestBody.put("max_tokens", MAX_TOKENS);
        requestBody.put("stream", false);

        // 创建HTTP连接
        URL url = new URL(getApiUrl(context));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Authorization", getApiKey(context));
        connection.setDoOutput(true);

        // 发送请求
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream()))) {
            writer.write(objectMapper.writeValueAsString(requestBody));
            writer.flush();
            Log.i("ChatAPI", "Request sent");
        }

        // 读取响应
        StringBuilder responseBuilder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                responseBuilder.append(line);
                Log.i("ChatAPI", "Response received: " + line);
            }
        }

        // 解析和返回响应
        return objectMapper.readTree(responseBuilder.toString())
                .get("choices")
                .get(0)
                .get("message");
    }

    // 获取大模型任务
    public static String get_missions(String user_input, Context context) throws IOException {
        // 获取Prompt
        String AI_Prompt = getPromptText(context, PROMPT_NAME_MISSION);

        // 创建 用户消息 并添加到历史
        ObjectNode userMessage = objectMapper.createObjectNode();
        userMessage.put("role", "user");
        userMessage.put("content", AI_Prompt + user_input);
        messages_missions.add(userMessage);

        // 获取AI响应
        JsonNode response = getLLMResponse(messages_missions, 1, context);

        // 删除Prompt内容
        replaceLastPrompt(messages_missions, user_input);

        // 提取响应内容
        String responseText = response.get("content").asText();
        Log.i("ChatAPI_missions_output", "AI 回复: " + responseText);

        // 创建 AI消息 并添加到历史
        ObjectNode assistantMessage = objectMapper.createObjectNode();
        assistantMessage.put("role", "assistant");
        assistantMessage.put("content", responseText);
        messages_missions.add(assistantMessage);

        Log.i("ChatAPI_missions_history", messages_missions.toString());
        return responseText;
    }

    /**
     * 生成AI回复文本
     *
     * @param user_input 用户输入的提示文本
     * @param context  this
     * @param prompt_name  使用的提示词，默认是 null
     * @return ChatResult对象，包含AI生成的回复文本和ends状态
     */
    public static ChatResult generateText(String user_input, Context context, String prompt_name, Integer messageLimit) throws IOException {
        Log.i("ChatAPI_user_input", "用户输入: " + user_input);
        Log.i("ChatAPI_state", "当前状态 - isInFixingMode: " + isInFixingMode + ", currentMode: " + currentMode);

        String missions_name;

        // 状态判断：如果当前处于fixing追问状态，直接使用fixing模式
        if (isInFixingMode) {
            Log.i("ChatAPI_state", "处于fixing追问状态，使用fixing模式");
            prompt_name = PROMPT_NAME_FIXING;
        } else {
            // 初始状态，需要进行Mission分类
            Log.i("ChatAPI_state", "初始状态，进行Mission分类");
            missions_name = get_missions(user_input, context);   // LLM获取任务
            if (missions_name.equals("闲聊") || missions_name.equals("**闲聊**")) {
                prompt_name = PROMPT_NAME_CHAT;
                currentMode = "闲聊";
            } else if (missions_name.equals("备忘录")|| missions_name.equals("**备忘录**")) {
                prompt_name = PROMPT_NAME_MEMO;
                currentMode = "备忘录";
            }
        }

        // 获取Prompt
        String AI_Prompt = getPromptText(context, prompt_name);

        // 获取当前时间
        String currentTime = getCurrentTimeFormatted();

        // 创建用户消息内容
        String userContent;
        if (isInFixingMode) {
            // fixing模式：提供待完善的备忘录和用户回答
            String pendingMemosJson = getPendingMemosAsJson();
            userContent = AI_Prompt + "当前时间: " + currentTime +
                    "\n待完善的备忘录:\n" + pendingMemosJson +
                    "\n用户回答: " + user_input;
        } else {
            // 正常模式
            userContent = AI_Prompt + "当前时间: " + currentTime + "\n用户输入: " + user_input;
        }

        // 创建 用户消息 并添加到历史
        ObjectNode userMessage = objectMapper.createObjectNode();
        userMessage.put("role", "user");
        userMessage.put("content", userContent);
        messages.add(userMessage);

        // 获取AI响应
        JsonNode response = getLLMResponse(messages, messageLimit, context);

        // 删除Prompt内容
        if (isInFixingMode) {
            replaceLastPrompt(messages, "用户回答: " + user_input);
        } else {
            replaceLastPrompt(messages, user_input);
        }

        // 提取响应内容
        String responseText = response.get("content").asText();
        Log.i("ChatAPI_output", "AI 回复: " + responseText);

        // 创建 AI消息 并添加到历史
        ObjectNode assistantMessage = objectMapper.createObjectNode();
        assistantMessage.put("role", "assistant");
        assistantMessage.put("content", responseText);
        messages.add(assistantMessage);

        // 解析响应并处理
        String userReplyContent = "";
        boolean ends = true; // 默认为true

        if (currentMode.equals("闲聊")) {
            // 闲聊模式：解析JSON中的response字段
            try {
                JSONObject jsonResponse = new JSONObject(responseText);
                if (jsonResponse.has("response")) {
                    userReplyContent = jsonResponse.getString("response");
                } else {
                    userReplyContent = "抱歉，我未能理解您的需求。";
                    Log.e("ChatAPI11", "111");

                }
            } catch (JSONException e) {
                Log.e("ChatAPI11", "解析闲聊模式的JSON出错", e);
                userReplyContent = "抱歉，我未能理解您的需求。";
            }

            // 闲聊模式：ends始终为true
            ends = true;

            // 确保退出fixing状态
            isInFixingMode = false;
            currentMode = "";
            pendingMemos.clear();
        } else {
            // 备忘录模式：解析JSON并处理
            ParsedResponse parsedResponse = parseResponseWithReply(responseText);
            userReplyContent = parsedResponse.userReplyContent;

            // 状态管理：检查是否需要进入或退出fixing追问状态
            if (currentMode.equals("备忘录") || isInFixingMode) {
                boolean hasIncomplete = false;

                // 更新待完善的备忘录列表
                pendingMemos.clear();
                for (MemoData memo : parsedResponse.memoList) {
                    if (!memo.end) {
                        hasIncomplete = true;
                        pendingMemos.add(memo);
                    }
                }

                if (hasIncomplete) {
                    // 存在end为false的JSON，进入fixing状态
                    isInFixingMode = true;
                    currentMode = "fixing";
                    ends = false; // 备忘录模式有追问时ends为false
                    Log.i("ChatAPI_state", "发现未完成的备忘录，进入fixing状态");
                } else {
                    // 所有JSON的end都为true，退出fixing状态
                    isInFixingMode = false;
                    currentMode = "";
                    pendingMemos.clear();
                    ends = true; // 备忘录模式无追问时ends为true
                    Log.i("ChatAPI_state", "所有备忘录已完成，退出fixing状态");
                }
            }

            // 自动保存完整的备忘录到数据库
            int savedCount = 0;
            for (MemoData memo : parsedResponse.memoList) {
                if (memo.mode.equals("备忘录") && memo.end) {  // 只保存end为true的备忘录
                    boolean currentSaved = saveMemoToDatabase(context, memo);
                    if (currentSaved) {
                        savedCount++;
                        Log.i(LOG_TAG, "备忘录 '" + memo.title + "' 已自动保存到数据库");
                    } else {
                        Log.e(LOG_TAG, "备忘录 '" + memo.title + "' 自动保存失败");
                    }
                }
            }

            if (savedCount > 0) {
                Log.i(LOG_TAG, "已保存 " + savedCount + " 条备忘录到数据库");
            }
        }

        Log.i("ChatAPI_history", messages.toString());
        // 测试用：返回完整AI回复（注释掉以使用正式版本）
//         return responseText;

        // 正式版本：返回ChatResult对象
        return new ChatResult(userReplyContent, ends);
    }




    public static class ChatResult {
        public String text;
        public boolean ends;

        public ChatResult(String text, boolean ends) {
            this.text = text;
            this.ends = ends;
        }
    }

    /**
     * 解析AI响应，提取备忘录JSON和用户回复内容
     */
    private static ParsedResponse parseResponseWithReply(String responseText) {
        ParsedResponse result = new ParsedResponse();
        result.memoList = new ArrayList<>();
        result.userReplyContent = "抱歉，我没能理解您的需求，请重新描述。"; // 默认回复

        try {
            List<String> jsonStrings = extractAllJsonFromText(responseText);

            for (String jsonStr : jsonStrings) {
                try {
                    JSONObject jsonObject = new JSONObject(jsonStr);
                    String mode = jsonObject.optString("mode", "");

                    if ("备忘录".equals(mode)) {
                        // 这是备忘录JSON
                        MemoData memoData = new MemoData();
                        memoData.mode = mode;
                        memoData.title = jsonObject.optString("title", "");
                        memoData.content = jsonObject.optString("content", "");
                        memoData.created_time = jsonObject.optString("created_time", "");
                        memoData.reminder_time = jsonObject.optString("reminder_time", "");
                        memoData.end = jsonObject.optBoolean("end", false);
                        result.memoList.add(memoData);
                    } else if ("回复".equals(mode)) {
                        // 这是用户回复JSON
                        result.userReplyContent = jsonObject.optString("content", result.userReplyContent);
                    }
                } catch (JSONException e) {
                    Log.w(LOG_TAG, "单个JSON解析失败，跳过: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "解析响应失败: " + e.getMessage(), e);
        }

        return result;
    }

    /**
     * 解析响应结果类
     */
    private static class ParsedResponse {
        List<MemoData> memoList;
        String userReplyContent;
    }

    /**
     * 将待完善的备忘录转换为JSON字符串
     * @return 待完善备忘录的JSON字符串
     */
    private static String getPendingMemosAsJson() {
        StringBuilder jsonBuilder = new StringBuilder();
        for (MemoData memo : pendingMemos) {
            jsonBuilder.append("{\n");
            jsonBuilder.append("  \"mode\": \"").append(memo.mode).append("\",\n");
            jsonBuilder.append("  \"title\": \"").append(memo.title).append("\",\n");
            jsonBuilder.append("  \"content\": \"").append(memo.content).append("\",\n");
            jsonBuilder.append("  \"created_time\": \"").append(memo.created_time).append("\",\n");
            jsonBuilder.append("  \"reminder_time\": \"").append(memo.reminder_time).append("\",\n");
            jsonBuilder.append("  \"end\": ").append(memo.end).append("\n");
            jsonBuilder.append("}\n");
        }
        return jsonBuilder.toString();
    }

    // messages处理
    /**
     * 删除messages列表中每个ObjectNode的"role"键，除了最后一个
     * @param messages 消息列表，每个元素是ObjectNode
     */
    public static void removePromptKeyExceptLast(List<JsonNode> messages) {
        int size = messages.size();
        for (int i = 0; i < size - 1; i++) {
            JsonNode message = messages.get(i);
            // 只处理ObjectNode类型
            if (message instanceof ObjectNode) {
                ObjectNode obj = (ObjectNode) message;
                if (obj.has("prompt")) {
                    obj.remove("prompt");
                }
            }
        }
        // 最后一个元素不处理
    }

    /**
     * 替换 messages 列表最后一个 JsonNode 的 "role" 字段为指定文本
     * 兼容 List<JsonNode> 或 List<ObjectNode>
     */
    public static void replaceLastPrompt(List<JsonNode> messages, String newRole) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        JsonNode lastNode = messages.get(messages.size() - 1);
        // 判断并强转类型
        if (lastNode instanceof ObjectNode) {
            ((ObjectNode) lastNode).put("content", newRole);
        }
    }

    /**
     * 从文本中提取所有JSON字符串
     * @param text 包含JSON的文本
     * @return JSON字符串列表
     */
    private static List<String> extractAllJsonFromText(String text) {
        List<String> jsonStrings = new ArrayList<>();

        int searchStart = 0;
        while (searchStart < text.length()) {
            // 查找下一个JSON开始位置
            int startIndex = text.indexOf('{', searchStart);
            if (startIndex == -1) {
                break; // 没有更多的JSON了
            }

            // 寻找匹配的结束括号
            int braceCount = 0;
            int endIndex = -1;

            for (int i = startIndex; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '{') {
                    braceCount++;
                } else if (c == '}') {
                    braceCount--;
                    if (braceCount == 0) {
                        endIndex = i;
                        break;
                    }
                }
            }

            if (endIndex != -1) {
                String jsonStr = text.substring(startIndex, endIndex + 1);
                jsonStrings.add(jsonStr);
                searchStart = endIndex + 1;
            } else {
                break; // 没有找到匹配的结束括号
            }
        }

        return jsonStrings;
    }

    /**
     * 备忘录数据类
     */
    public static class MemoData {
        public String mode;
        public String title;
        public String content;
        public String created_time;
        public String reminder_time;
        public boolean end;

        @Override
        public String toString() {
            return "MemoData{" +
                    "mode='" + mode + '\'' +
                    ", title='" + title + '\'' +
                    ", content='" + content + '\'' +
                    ", created_time='" + created_time + '\'' +
                    ", reminder_time='" + reminder_time + '\'' +
                    ", end=" + end +
                    '}';
        }
    }

    // SQL处理
    /**
     * 将AI生成的备忘录数据自动保存到数据库
     * @param context 上下文对象
     * @param memo 提取的备忘录数据
     * @return 是否保存成功
     */
    private static boolean saveMemoToDatabase(Context context, MemoData memo) {
        if (memo == null) {
            Log.w(LOG_TAG, "备忘录数据为空，无法保存");
            return false;
        }

        NoteDbHelper dbHelper = null;
        try {
            // 创建数据库帮助类实例
//            dbHelper = new NoteDbHelper(context);
            dbHelper = NoteDbHelper.getInstance(context);

            // 创建Note对象
            Note note = new Note();
            note.setTitle(memo.title);
            note.setContent(memo.content);

            // 解析并设置创建时间
            if (!memo.created_time.isEmpty()) {
                try {
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
                    Date createdDate = dateFormat.parse(memo.created_time);
                    note.setCreatedTime(createdDate.getTime());
                } catch (ParseException e) {
                    Log.w(LOG_TAG, "创建时间解析失败，使用当前时间: " + e.getMessage());
                    note.setCreatedTime(System.currentTimeMillis());
                }
            } else {
                note.setCreatedTime(System.currentTimeMillis());
            }

            // 解析并设置提醒时间
            if (!memo.reminder_time.isEmpty()) {
                try {
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
                    Date reminderDate = dateFormat.parse(memo.reminder_time);
                    note.setReminderTime(reminderDate.getTime());
                } catch (ParseException e) {
                    Log.w(LOG_TAG, "提醒时间解析失败: " + e.getMessage());
                    note.setReminderTime(0);
                }
            } else {
                note.setReminderTime(0);
            }

            // 插入数据库
            long result = dbHelper.insertNote(note);

            if (result != -1) {
                Log.i(LOG_TAG, "备忘录保存成功，数据库ID: " + result);
                return true;
            } else {
                Log.e(LOG_TAG, "备忘录保存失败");
                return false;
            }

        } catch (Exception e) {
            Log.e(LOG_TAG, "保存备忘录时发生错误: " + e.getMessage(), e);
            return false;
        } finally {
            if (dbHelper != null) {
                dbHelper.close();
            }
        }
    }
}