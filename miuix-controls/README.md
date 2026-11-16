# HyperMusic Miuix 控件

本模块保留 `miuix-ui-android 0.9.3` 和 `miuix-preference-android 0.9.3` 的公开接口。高斯模糊样式继续使用原组件；柔光玻璃菜单、按钮和 TabRow 直接调用本地 `E:/Code/miuix-main` 提供的新版官方组件。

- `upstream/miuix-glass/`：官方 `GlassDropdownPreference`、`GlassIconDropdownMenu`、`GlassDropdownPopup`、`GlassTransformPopup`、`GlassIconButton`、`GlassTabRow` 和 `GlassSegmentedTabRow`。菜单的尺寸、锚点、文字迁移、展开与收起动画由这些组件处理。
- `upstream/miuix-material/`：官方玻璃材质、形状及效果实现。
- `upstream/miuix-blur/`：新版玻璃组件需要的渲染引擎。包名隔离为 `top.yukonga.miuix.kmp.glass.backdrop`，与原有迷你播放器和高斯模糊使用的旧版 blur 共存。
- `material/OfficialGlassComponents.kt`：将应用现有入口、选项数据、图标插槽和回调转交给官方组件，不另行定义菜单几何或动画。图标菜单通过 `glassPopupAnchorContent` 记录图标自身的范围，避免把按钮范围误用为图标范围；延迟挂载的弹层先提供折叠初始帧，再交给官方动画展开。
- `material/SoftGlassMaterial.kt`：正文采样源、官方按钮的标题栏滚动上下文，以及现有弹窗和抽屉的材质适配。
- `material/ProgressiveBlurShaders.kt`：参考 NexioSchedule 的两趟渐进模糊标题栏。背景可延伸到栏下方，强度随滚动显现，不改变标题或栏的布局高度。
- `material/softglass/`：保留的 Softglass 0.1.1 光学着色器和参数，供现有表面适配使用；官方菜单和 TabRow 不使用这套自定义绘制路径。

正文与完整页面底色一起录制，标题栏和弹层作为独立兄弟图层采样，避免透明间隙变黑和 RenderNode 自引用。搜索结果和匹配歌词通过 `LocalSoftGlassTabsEnabled = false` 保留原 TabRow。

兼容性改动包括：合并 Android/common 的 `expect` / `actual` 声明；为官方菜单补充应用现有的图标插槽与选项信息；使用当前 Compose 支持的图层策略兼容新版 `LayerOutsets`。新版标题栏基础接口一并移入模块。暂未接入要求更新版基础 API 的官方 GlassDialog 和 GlassBottomSheet，现有弹窗和抽屉继续使用共享材质适配。

应用通过依赖排除规则确保最终 APK 中只包含一份 Miuix UI 实现。上游来源和许可证见 [NOTICE](NOTICE) 及 [LICENSE](LICENSE)。
