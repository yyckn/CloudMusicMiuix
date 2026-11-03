# AGENTS.md

## Packaging

- 构建 release APK：`.\gradlew.bat :app:assembleRelease`
- 每次打包后，把生成的 `app/build/outputs/apk/release/app-release.apk` 复制为
  `app/build/outputs/apk/release/HyperMusic-<versionName>.apk`
  （`<versionName>` 取自 `app/build.gradle.kts`）。
- 示例（当前 2.0.0-dev3）：
  `Copy-Item app\build\outputs\apk\release\app-release.apk app\build\outputs\apk\release\HyperMusic-2.0.0-dev3.apk -Force`
