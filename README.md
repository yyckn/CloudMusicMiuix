<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" width="112" alt="HyperMusic" />
</p>

<h1 align="center">HyperMusic</h1>

<p align="center">使用 Kotlin、Jetpack Compose 与 Miuix 构建的多音源 Android 音乐播放器</p>
<p align="center">网易云音乐 · QQ 音乐 · 波点音乐 · 酷狗音乐 · 汽水音乐</p>
<p align="center">
  <a href="https://github.com/yyckn/CloudMusicMiuix/releases">下载版本</a> ·
  <a href="app/src/main/java/com/hypermusic/miuix/data/model/VersionHistory.kt">历史更新日志</a> ·
  <a href="LICENSE">AGPL-3.0</a>
</p>

## 简介

**HyperMusic** 是一款采用 Miuix 设计语言的第三方音乐客户端，支持手机、折叠屏和平板，提供音乐发现、分类搜索、后台播放、多来源歌词、多账号、缓存与内置下载器。

各平台的账号、收藏、播放队列与音质设置独立管理，主题、歌词渲染和通用界面组件共用。切换平台时保留各自的歌曲与队列，恢复后默认暂停。

- 当前开发版本：`2.1.0-dev2`，以 [构建配置](app/build.gradle.kts) 为准。
- 应用包名：`com.hypermusic.miuix`。
- 运行要求：Android 13（API 33）及以上；当前 APK 仅包含 `arm64-v8a` 架构。

## 功能

### 多音源与音乐发现

| 平台 | 推荐内容 |
| --- | --- |
| 网易云音乐 | 每日推荐、私人 FM、心动模式、随机播放、专属歌单、雷达歌单、新碟上架 |
| QQ 音乐 | 雷达推荐、每日 30 首、推荐新歌、猜你喜欢、推荐歌单与分类榜单 |
| 波点音乐 | 私人 FM、潮趣日推、新歌打赏及推荐歌单 |
| 酷狗音乐 | 每日推荐、私人 FM、VIP 专属推荐、私人专属好歌及推荐歌单 |
| 汽水音乐 | 私人 FM、专注模式及驾车、通勤、健身等场景推荐 |

- 独立搜索页，按单曲、歌单、专辑、歌手分类展示结果，保留搜索状态并共用搜索历史。
- 歌单、专辑、歌手、排行榜详情，支持页面内搜索和下拉刷新。
- 收藏音乐、歌单、专辑，以及播放、随机播放、评论等操作。
- 网易云音乐云盘浏览与播放。
- 手机与大屏自适应布局、封面过渡动画和滚动位置保留。

不同平台的接口能力、登录方式和可用内容存在差异；推荐结果、音质、收藏与播放权限以平台及当前账号实际返回为准。

### 播放与音频

- AndroidX Media3 / ExoPlayer 播放引擎，支持后台播放、媒体通知、耳机按键与音频焦点管理。
- 各平台独立设置播放音质，依据歌曲和账号权限回退，并显示实际取得的音质。
- 播放速度、渐入渐出；关闭、智能平衡、单曲、专辑四种响度平衡模式。
- 音效预设、10 段均衡器与 9 项高级音频设置，支持保存用户预设。
- 卡片封面、卡片封面（车机布局）、渐变封面、黑胶唱片四种封面样式。
- 圆形封面、播放时旋转、黑胶滑动切歌和封面／歌词切换。
- 媒体通知收藏与关闭按钮、系统音频投放入口、可自定义组合键快捷键。
- 桌面快捷菜单“播放每日推荐”，根据当前音源播放对应推荐。

### 歌词

- 网易云音乐、QQ 音乐、酷狗音乐与 Apple Music（AMLL TTML DB）多来源匹配。
- 智能选择或指定来源偏好，支持自动升级逐字歌词，并按设置优先保留罗马音。
- 原文、翻译、罗马音与逐字高亮；AMLL 歌词支持衬词动画和对唱左右对齐。
- 手动匹配歌词、查看候选歌曲的逐字／罗马音／对唱标签，并保存单曲匹配结果。
- 内置与自定义歌词排除规则，支持包含、开头、末尾、完全匹配、正则表达式及时长条件。
- 歌手行处理、纯音乐提示，以及词幕接口适配。

### 账号

- 各平台独立登录与多账号管理，可分别选择播放账号和数据账号。
- 网易云 PC 端扫码登录；QQ 音乐统一二维码支持 QQ 手机版、微信、QQ 音乐扫码。
- QQ 与微信登录属于不同账号体系，在账号页面及切换选项中区分展示。
- 登录凭据导入、二维码分享与扫描，支持通过 `hypermusic://` 链接唤起导入。
- 首次启动引导（OOBE），完成协议确认、音乐平台选择与账号登录。

凭据二维码包含登录信息，请仅在自己的可信设备之间传递。

### 缓存与下载

- 歌曲文件、封面、封面缩略图和歌手头像分类缓存，支持查看占用、设置容量和分别清理。
- 音频缓存区分平台、歌曲与实际音质，复用完整或部分缓存，并按最近使用情况淘汰。
- 全局下载抽屉，区分正在下载与历史下载，显示进度、大小、实际音质和失败原因。
- 后台下载、任务持久化、重试、取消、分享、目录导出与删除。
- 独立的下载音质设置，支持跟随播放设置、非 Wi-Fi 提醒及并发数量设置。
- 下载完成后尝试写入歌曲信息、封面、原文与翻译歌词；不支持或获取失败的项目会提示。

下载文件与播放缓存分开保存，清理缓存不会删除下载文件。下载默认存放在应用内部，卸载会移除；需要长期保留的文件可导出至系统目录。

### 主题与界面

- Miuix 大小标题、卡片、搜索组件、抽屉、菜单与下拉刷新。
- 深色、浅色、跟随系统，Monet 取色、自选强调色与跟随音乐来源主题色。
- 色彩风格、色彩标准、主题预览、界面缩放与预测性返回手势。
- 标准底栏、可选高斯模糊、悬浮底栏与液态玻璃迷你播放条。
- 自定义应用图标和名称，支持预览、暂存修改及保存后重启。
- 开发者选项提供硬件信息、歌词原始数据及最终处理结果等调试工具。

## 构建

准备 Android Studio、**JDK 21**、Android SDK 37，以及项目自带的 Gradle Wrapper。SDK 路径通过本地 `local.properties` 的 `sdk.dir` 配置，该文件不提交到仓库。

Windows PowerShell：

```powershell
# Debug
.\gradlew.bat :app:assembleDebug

# Release
.\gradlew.bat :app:assembleRelease
```

macOS / Linux：

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

产物分别位于：

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release.apk
```

Release 按项目约定命名为 `HyperMusic-v<versionName>.apk`。Windows 构建后可执行：

```powershell
$versionName = (Select-String -Path app/build.gradle.kts -Pattern 'versionName = "([^"]+)"').Matches[0].Groups[1].Value
Copy-Item app/build/outputs/apk/release/app-release.apk "app/build/outputs/apk/release/HyperMusic-v$versionName.apk" -Force
```

自行分发时请配置自己的 Release 签名。当前默认只打包 `arm64-v8a`，其他架构设备或模拟器需要自行调整构建配置。

单元测试与已连接设备测试：

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

## 项目结构

```text
app/src/main/
├── java/com/hypermusic/miuix/
│   ├── data/
│   │   ├── api/          # 各平台接口、登录与账号
│   │   ├── cache/        # 分类缓存
│   │   ├── lyrics/       # 歌词获取、匹配、解析与处理
│   │   └── model/        # 数据模型及本地版本记录
│   ├── service/          # 播放服务、媒体通知与词幕
│   ├── ui/               # 页面、组件、主题、导航与扫码
│   ├── viewmodel/        # 播放状态与业务逻辑
│   └── MainActivity.kt   # 应用入口
├── assets/licenses/      # 第三方许可证与署名
└── res/                  # 图标、图片及其他资源
```

构建和调试日志属于可重新生成的临时文件，不随源码维护；`.gitignore` 已忽略 `*.log`。

## 致谢

| 项目 | 用途 |
| --- | --- |
| [Miuix](https://github.com/compose-miuix-ui/miuix) | UI 框架、组件与交互效果 |
| [SukiSU Ultra](https://github.com/SukiSU-Ultra/SukiSU-Ultra/) | 部分 Miuix 界面与主题设置样式参考 |
| [BuJuan](https://github.com/2697a/bujuan) | 网易云登录、推荐与播放实现参考 |
| [SPlayer-Next](https://github.com/SPlayer-Dev/SPlayer-Next) | 平台接口、缓存与歌词处理参考 |
| [词幕](https://github.com/tomakino/lyricon) | 系统状态栏歌词接口 |
| [音理音理!](https://github.com/cwuom/NeriPlayer) | 词幕适配等实现参考 |
| [LyricInfo](https://github.com/limczhh/LyricInfo) | 歌词信息接口 |
| [Accompanist Lyrics UI](https://github.com/6xingyv/accompanist-lyrics-ui) | 歌词渲染组件 |
| [Folia](https://github.com/chthollyphile/folia-major) | 响度平衡、音效及多平台接口参考 |
| [HyperCeiler](https://github.com/ReChronoRain/HyperCeiler) | OOBE 交互及部分图标 |

感谢 AndroidX、OkHttp、Coil、Gson，以及所有开源依赖的维护者与贡献者。仓库中保留的第三方源码、资源、许可证与署名仍遵循各自的许可条款，详见 [第三方许可证目录](app/src/main/assets/licenses/) 及对应源码说明。

## 内容与平台说明

HyperMusic 是独立的第三方客户端，与所接入的音乐平台无隶属、合作或授权关系。平台接口可能变化，部分功能依赖账号、会员权限和网络环境。

音乐、歌词、封面和平台商标的权利归各自权利人所有，仓库的开源许可证不授予这些内容的使用权。请遵守对应平台的服务条款，并在应用内阅读《用户协议与免责声明》。

## 许可证

Copyright © 2026 王尧。

本项目采用 **GNU Affero General Public License v3.0（SPDX：`AGPL-3.0-only`）**，完整条款见 [LICENSE](LICENSE)。第三方组件和资源保留其原有许可证及署名。
