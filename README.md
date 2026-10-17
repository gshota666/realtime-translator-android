# Realtime Translator Android App

一个简单的安卓中英文实时翻译 App。

## 功能

- 中文 -> 英文 实时翻译
- 英文 -> 中文 实时翻译
- 输入内容后自动防抖请求翻译
- 支持方向切换和失败重试
- 使用 Jetpack Compose 编写 UI

## 技术栈

- Kotlin
- Jetpack Compose
- Android ViewModel + Coroutines
- MyMemory 免费翻译接口

## 说明

- 这是一个联网 App，需要网络权限。
- 当前版本为了简单，使用公开翻译接口，适合演示和基础项目使用。
## 打开方式

1. 用 Android Studio 打开当前项目目录。
2. 等待 Gradle Sync 完成。
3. 连接模拟器或真机后运行。

## GitHub 上传

当前目录已经初始化 Git 仓库，你可以直接执行：

```bash
git add .
git commit -m "Initial commit: Android realtime translator app"
```

然后创建 GitHub 仓库并执行：

```bash
git remote add origin <your-github-repo-url>
git branch -M main
git push -u origin main
```

## 后续可扩展

- 加入语音输入
- 增加历史记录
- 收藏常用翻译
- 接入更稳定的翻译服务
