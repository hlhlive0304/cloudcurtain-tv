# 0.1.17-preview 公开发布复核

2026-10-11。仅记录本版发布后完成的检查。

- 源码提交：`f747bd5`，标签：`v0.1.17-preview`。
- Release：<https://github.com/hlhlive0304/cloudcurtain-tv/releases/tag/v0.1.17-preview>，公开预览版。
- 五项公开资产的大小、上传状态及服务器 SHA256 均与本地发布文件一致：APK、源码 ZIP、SHA256SUMS.txt、update.json、release-notes.txt。
- 无认证重新下载公开 APK，得到 11,703,827 字节；SHA256 为 `5efdee142adb7ab249c5aba426bf797d3ed7dc32feee84320d4e68e614fb20e0`，与实际安装并测试的 APK 一致。
- 更新根目录清单后，读取公开的 `main/update.json`，确认其完整内容与已测试版本清单一致：包名 `cn.cloudcurtain.tv`、versionCode 18、versionName `0.1.17-preview`、下载地址、大小和哈希均相符。
- Android TV 11 模拟器中的最终生产包，在“应用更新 → 检查更新”读取上述真实公开清单，显示“当前版本：0.1.17-preview”和“当前已是最新版本。”；测试时系统代理为 `null`。

本版没有在系统安装器内重复执行 0.1.16 → 0.1.17 在线覆盖安装。因此不将公开下载、清单检查或此前版本的安装测试表述为本版已完成系统升级验收。当前本地生产包已通过 adb 覆盖安装，应用签名与 0.1.16 相同。

公开发布后未替换或重新生成 APK、源码 ZIP 或 Release 资产。个人配置、网盘凭据和签名私钥不在公开文件中。

机器下载结果见 `public-download-0.1.17.json`，原生更新界面结果见 `update-latest-0.1.17.xml`；功能回归及解码限制见 `folder-baidu-0.1.17.md`。
