# 车载语音备忘录 - 智能备忘录应用

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Android](https://img.shields.io/badge/Android-7.0+-green.svg)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.8+-blue.svg)](https://kotlinlang.org/)
[![API](https://img.shields.io/badge/API-24+-orange.svg)](https://developer.android.com/guide/topics/manifest/uses-sdk-element)

一个功能丰富的Android智能备忘录应用，专为车载环境设计，集成了语音识别、AI智能聊天、语音播报和智能提醒功能。

## 🚗 项目特色

### 核心功能
- 📝 **智能备忘录管理** - 创建、编辑、删除和查看备忘录
- 🎤 **语音识别转文字** - 集成Microsoft Cognitive Services语音识别
- 🤖 **AI智能助手** - 基于DeepSeek大语言模型的智能对话
- 🔊 **语音播报** - TTS语音合成，支持备忘录内容播报
- ⏰ **智能提醒系统** - 定时提醒功能，支持开机自启动
- 📱 **分屏聊天** - 创新的分屏聊天界面，边看备忘录边聊天

### 技术亮点
- 🏗️ **MVVM架构** - 采用现代Android架构模式
- 💾 **本地数据库** - SQLite数据库存储，支持数据导出
- 🎨 **苹果风格UI** - 现代化的用户界面设计
- 🔄 **实时刷新** - 智能提醒状态实时更新
- 🛡️ **权限管理** - 完善的权限申请和管理机制

## 📱 系统要求

- **Android版本**: Android 7.0 (API 24) 或更高版本
- **网络连接**: 需要网络连接（用于语音识别和AI聊天功能）
- **权限要求**: 
  - 麦克风权限（语音识别）
  - 网络权限（API调用）
  - 提醒权限（定时提醒）
  - 开机自启动权限（提醒服务）

## 🚀 快速开始

### 1. 环境准备
```bash
# 克隆项目
git clone [项目地址]
cd test12_finalwork

# 确保已安装Android Studio和JDK 11
```

### 2. 配置API密钥

**重要**: 本项目采用安全的配置方式，API密钥不会硬编码在代码中。

#### 方式一：通过应用内配置（推荐）
1. 运行应用后，在侧边菜单中点击"API配置"
2. 输入您的API密钥：
   - **DeepSeek API密钥**: 从 https://platform.deepseek.com/ 获取
   - **Microsoft语音服务密钥**: 从 https://portal.azure.com/ 获取
   - **语音服务区域**: 通常为 `eastasia`
3. 点击"保存配置"

#### 方式二：通过local.properties配置（开发者）
在 `local.properties` 文件中配置：
```properties
# DeepSeek AI API配置
DEEPSEEK_API_KEY=your_deepseek_api_key_here

# Microsoft Cognitive Services 语音识别
SPEECH_SUBSCRIPTION_KEY=your_speech_key_here
SPEECH_REGION=your_region_here
```

**安全说明**: 
- API密钥存储在本地SharedPreferences中，使用Android系统加密
- 密钥不会上传到任何服务器
- 应用内配置界面会掩码显示密钥，保护隐私
- `local.properties` 文件不会被提交到版本控制系统

### 3. 构建运行
```bash
# 使用Gradle构建
./gradlew assembleDebug

# 或使用Android Studio直接运行
```

## 📖 使用指南

### 主界面功能
1. **备忘录列表** - 显示所有备忘录，支持滑动删除
2. **添加备忘录** - 点击右下角"+"按钮创建新备忘录
3. **AI助手** - 点击底部中间AI按钮进入分屏聊天
4. **侧边菜单** - 点击右上角菜单按钮打开功能面板

### 语音识别功能
1. 在侧边菜单中点击"语音识别"
2. 首次使用需要授予麦克风权限
3. 点击"测试配置"验证语音服务
4. 点击"开始识别"进行语音转文字
5. 识别结果可直接用于备忘录内容

### AI智能聊天
1. **普通聊天** - 在侧边菜单中点击"AI聊天助手"
2. **分屏聊天** - 点击主界面AI助手按钮
3. **备忘录关联聊天** - 在分屏模式下可同时查看备忘录和聊天
4. **智能任务生成** - AI可自动生成备忘录任务

### 提醒功能
1. 在编辑备忘录时设置提醒时间
2. 系统会在指定时间发送通知
3. 支持语音播报提醒内容
4. 提醒状态实时更新

### 数据管理
- **查看数据库** - 在侧边菜单中查看所有数据
- **导出数据** - 支持备忘录数据导出
- **清空数据** - 一键清空所有备忘录

## 🏗️ 项目架构

### 目录结构
```
app/src/main/java/com/example/create_part2/
├── activity/                    # 主要活动类
│   ├── MainActivity.java       # 主界面活动
│   └── ChatWithNotesActivity.java # 分屏聊天活动
├── activity_test/              # 测试功能活动
│   ├── STTActivity.java        # 语音识别活动
│   ├── TTSActivity.java        # 语音合成活动
│   └── ChatActivity.java       # 聊天活动
├── db/                         # 数据库相关
│   ├── Note.java              # 备忘录数据模型
│   ├── NoteDbHelper.java      # 数据库操作类
│   ├── NoteRepository.java    # 数据仓库
│   ├── NoteViewModel.java     # ViewModel
│   ├── NoteAdapter.java       # RecyclerView适配器
│   ├── NoteDetailActivity.java # 备忘录详情活动
│   ├── ReminderManager.java   # 提醒管理器
│   └── ReminderReceiver.java  # 提醒广播接收器
├── ChatAPI.java               # AI聊天API接口
├── MicrosoftTTS.java          # 微软TTS语音合成
└── MicrophoneStream.java      # 麦克风流处理
```

### 核心组件

#### 1. 数据层 (Database Layer)
- **Note**: 备忘录数据模型
- **NoteDbHelper**: SQLite数据库操作，采用单例模式
- **NoteRepository**: 数据访问抽象层
- **NoteViewModel**: MVVM架构的ViewModel

#### 2. 业务层 (Business Layer)
- **ChatAPI**: DeepSeek AI API集成，支持多种对话模式
- **ReminderManager**: 提醒系统管理
- **MicrosoftTTS**: 微软语音合成服务

#### 3. 表现层 (Presentation Layer)
- **MainActivity**: 主界面，备忘录列表管理
- **ChatWithNotesActivity**: 创新的分屏聊天界面
- **STTActivity**: 语音识别功能界面

### 技术栈
- **架构模式**: MVVM + Repository
- **数据库**: SQLite (Room兼容)
- **UI框架**: AndroidX + Material Design
- **语音服务**: Microsoft Cognitive Services
- **AI服务**: DeepSeek Chat API
- **JSON处理**: Jackson
- **权限管理**: AndroidX Activity Result API

## 🔧 开发指南

### 添加新功能
1. 在对应包下创建新的Activity或Fragment
2. 在AndroidManifest.xml中注册新组件
3. 创建对应的布局文件
4. 更新侧边菜单（如需要）

### 数据库操作
```java
// 获取数据库实例
NoteDbHelper dbHelper = NoteDbHelper.getInstance(context);

// 插入备忘录
Note note = new Note("标题", "内容");
long id = dbHelper.insertNote(note);

// 查询所有备忘录
List<Note> notes = dbHelper.getAllNotes();
```

### API调用
```java
// AI聊天
ChatAPI.ChatResult result = ChatAPI.generateText(userInput, context, null, 20);

// 语音识别
// 参考STTActivity中的实现
```

## 🐛 故障排除

### 常见问题

1. **语音识别失败**
   - 检查网络连接
   - 验证API密钥配置
   - 确认麦克风权限已授予

2. **AI聊天无响应**
   - 检查网络连接
   - 验证DeepSeek API密钥
   - 查看日志输出

3. **提醒不工作**
   - 检查提醒权限设置
   - 确认系统允许应用自启动
   - 验证时间设置是否正确

### 调试模式
- 在侧边菜单中可查看数据库内容
- 支持数据导出功能
- 详细的日志输出

## 📄 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件

## 🤝 贡献指南

1. Fork 项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

## 📞 联系方式

如有问题或建议，请提交 Issue 或 Pull Request。

---

**注意**: 本项目专为车载环境设计，建议在驾驶时使用语音功能，确保行车安全。

## 📄 开源协议

本项目采用 [MIT 许可证](LICENSE) - 详见 [LICENSE](LICENSE) 文件

## 🤝 贡献

我们欢迎所有形式的贡献！请查看 [贡献指南](CONTRIBUTING.md) 了解如何参与项目开发。

## 🔒 安全

如果您发现了安全漏洞，请查看 [安全政策](SECURITY.md) 了解如何报告。

## 📝 更新日志

查看 [CHANGELOG.md](CHANGELOG.md) 了解项目的更新历史。

## 🌟 Star History

如果这个项目对您有帮助，请给我们一个 ⭐️ Star！

---

**免责声明**: 本应用仅供学习和研究使用。在驾驶时使用语音功能时，请确保安全第一，遵守当地交通法规。 