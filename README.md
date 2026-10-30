<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" width="120" alt="MusicHub for Miuix" />
</p>

<h1 align="center">MusicHub for Miuix</h1>

<p align="center">
  基于 Miuix 设计语言打造的第三方网易云音乐 Android 客户端
</p>

<p align="center">
  <code>Kotlin</code> · <code>Jetpack Compose</code> · <code>Miuix</code> · <code>Media3</code>
</p>

---

## 简介

**MusicHub for Miuix**（以下简称 MusicHub）是一款使用 Kotlin 与 Jetpack Compose 开发的第三方网易云音乐客户端。界面完全基于 [Miuix](https://compose-miuix-ui.github.io/miuix/) 组件库构建，力求在 Android 上带来接近 MIUI / HyperOS 系统级音乐应用的体验。

项目支持手机、折叠屏与平板布局，内置完整的播放、歌词、歌单、音乐云盘、多账号与音效能力。

> 当前版本：**1.2.2**（versionCode 54）  
> 更新日志见 [`updatelog.json`](updatelog.json)

> ⚠️ 本项目为个人学习与研究项目，与网易云音乐官方无任何隶属、合作或授权关系。

## 功能特性

### 音乐发现

- 推荐首页：专属推荐、雷达歌单、每日推荐、新碟上架、歌手推荐
- 每日推荐、随机播放、私人 FM、心动模式
- 歌曲 / 歌单 / 歌手 / 专辑搜索
- 歌单、专辑、歌手详情与评论

### 播放

- 基于 Media3 ExoPlayer 的在线播放
- 后台播放、通知栏控制、耳机按键、音频焦点处理
- 播放速度、渐入渐出、音质选择
- 音效预设：默认、流行、摇滚、爵士、古典、音乐厅、低音增强、人声、夜间
- 播放队列、收藏、分享、下载

### 歌词

- 逐字歌词、翻译歌词、日语罗马音副行
- 网易云 / QQ 音乐 / 酷狗音乐多来源歌词，支持自动升级逐字歌词
- 歌词排除规则：默认规则 + 自定义关键词 / 歌曲信息 / 正则 / 时长
- 单曲升级 / 恢复原歌词管理
- Lyricon 词幕适配

### 账号与云盘

- 扫码登录、多账号管理
- 播放账号与数据账号分离
- 音乐云盘浏览与播放（云盘歌曲自动使用数据账号 Cookie）

### 界面与个性化

- Miuix 组件：大标题、交互卡片、胶囊按钮、偏好设置、液态玻璃底栏
- 深色 / 浅色 / 跟随系统 / 莫奈取色
- 可切换应用图标（音符 / 网易云 / 酷狗）
- 播放页封面与歌词样式、悬浮底栏
- 手机 / 折叠屏 / 平板自适应布局
- 用户协议、开发者选项、自动检查更新

## 技术栈

| 分类 | 方案 |
| --- | --- |
| 语言 | Kotlin |
| UI | Jetpack Compose + Miuix |
| 播放 | AndroidX Media3 / ExoPlayer |
| 网络 | OkHttp |
| 序列化 | Gson |
| 图片 | Coil |
| 歌词 | accompanist-lyrics + Lyricon |

## 环境要求

- Android 13（API 33）及以上
- JDK 17
- Android SDK 37
- Gradle Wrapper（仓库自带）

## 构建

```bash
# Debug
./gradlew :app:assembleDebug

# Release
./gradlew :app:assembleRelease
```

Windows PowerShell：

```powershell
.\gradlew.bat :app:assembleRelease
```

产物路径：

```text
app/build/outputs/apk/release/app-release.apk
```

按项目约定重命名后的产物：

```text
app/build/outputs/apk/release/CloudMusicMiuix-v<versionName>.apk
```

> Release 构建使用仓库内置的签名配置。如需自行分发，请替换 `app/release-key.jks` 并在 `app/build.gradle.kts` 中修改签名信息。

## 项目结构

```text
app/src/main/java/com/musichub/miuix/
├── data/
│   ├── api/          # 网易云等接口与加密
│   ├── lyrics/       # 歌词解析、来源选择、排除规则
│   └── model/        # 数据模型
├── service/          # 播放服务、音效、Lyricon 词幕
├── ui/
│   ├── components/   # 通用组件（播放条、歌词视图等）
│   ├── pages/        # 页面
│   ├── theme/        # 主题与设置
│   └── effects/      # 液体玻璃等视觉效果
├── viewmodel/        # 播放状态与业务逻辑
└── MainActivity.kt   # 入口与页面导航
```

## 免责声明

- 本项目是第三方客户端，与网易云音乐、QQ 音乐、酷狗音乐均无关联。
- 本项目仅供个人学习、研究使用，请勿用于任何商业用途。
- 使用非官方客户端登录第三方平台可能触发平台风控，账号存在被限制的风险；请在 App 内阅读并确认完整《用户协议与免责声明》。
- 通过本项目访问的音乐、歌词、封面等内容的版权归原权利人所有，请支持正版。

## 致谢

- [Miuix](https://github.com/compose-miuix-ui/miuix) — 优秀的 Compose 设计语言组件库
- NeteaseCloudMusicApi / Listen1 — 接口与加密参考
- SPlayer-Next — 歌词来源与实现思路参考
- accompanist-lyrics / Lyricon — 歌词渲染与词幕支持
- 所有开源依赖的贡献者

## License

[MIT](LICENSE) © 2026 yyckn
