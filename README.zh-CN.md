> 本地 AI 检查卡住的修复和安装步骤见 [本地 AI 修复说明](docs/LOCAL_AI_FIX.zh-CN.md)。本次 APK 内置同签名组件，请勿混装官方组件。

# Orbit Assistant 简体中文初版

本版本基于 [lpnovi/Orbit-Assistant](https://github.com/lpnovi/Orbit-Assistant) 的稳定版 **v0.8.4.1**，基线提交为 `32655b8cd9dc6149b4264b476c8db0d6aab9cff2`。保留原作者署名和 MPL-2.0 许可证。

## 本次范围

- 首次设置、聊天首页、完整聊天与悬浮助手。
- AI 服务与账号、设置分类、中文设置搜索与搜索别名。
- 记忆、收藏库、智能收藏库、通知、权限、附件和文档查看。
- 自动流程、条件分支、时间与位置触发器、自定义指令、模板和运行历史。
- 快捷面板、主题、桌面小组件、本地 AI 管理、更新界面和诊断入口。
- 2,619 对中英文界面资源，以及平台显示名称和小组件说明。

英文保留为默认回退语言。界面文字通过 Android 字符串资源读取；不会对用户消息、AI 回答或导入内容进行全局文字替换。偏好设置键、操作类型、包名、网络协议和模型 ID 保留原值。

本次交付是界面汉化初版。上游更新日志与路线图正文、原始技术诊断字段、部分内置元数据名称，以及服务端或模型返回的文字仍可能显示英文。中国大陆网络环境下的服务可用性、中文本地指令识别和语音引擎能力需要单独验证。

## 使用中文

- Android 10–12：跟随系统语言，选择简体中文后重新打开应用。
- Android 13 及以上：已声明英语与简体中文语言列表；支持应用语言设置的系统可为 Orbit 单独选择简体中文。
- 设置搜索支持「震动」「深色」「电源键」「字号」「备份」等中文词，也保留已有英文搜索别名。

## 构建与检查

环境：JDK 17、Android SDK 36、Build Tools 35.0.0。其他说明见 [上游构建文档](docs/BUILDING.md)。

```bash
python3 tools/check_localization.py
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

针对汉化新增的 `ChineseLocalizationTest` 检查中文资源、搜索定位、运行时切换语言、附件菜单操作，以及用户文字不被翻译。`tools/check_localization.py` 检查资源配对、格式参数和代码引用。

Debug 构建用于开发测试，不使用上游作者的发行签名。此阶段保留上游包名、版本号和签名配置，尚未建立个人 Fork 的长期发行签名与更新渠道。

## 下一步

1. 建立 `Tx13758627780` 账号下的 Fork，并将本次修改同步到独立的中文分支。
2. 根据具体 OnePlus 机型、Android 版本及 ColorOS/OxygenOS 版本，适配默认助手、唤起入口和系统权限引导。
3. 完成 OnePlus 实机验证后再次同步；实机结果与仅通过编译的项目分开记录。

目前尚未完成 GitHub 上传或 OnePlus 实机适配。

## 许可证

原项目与本次修改遵循 [Mozilla Public License 2.0](LICENSE)。
