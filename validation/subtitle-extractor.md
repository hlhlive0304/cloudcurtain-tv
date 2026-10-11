# MKV 字幕读取器与测试素材

版本：0.1.14-preview，Media3 1.6.1。

原视频播放器继续使用 Media3 的标准提取器。只在兼容视频缺少原字幕时，辅助字幕播放器使用 SubtitleMatroskaExtractor，它由 Apache-2.0 许可的 Media3 1.6.1 MatroskaExtractor 修改而来。保留原作者及版权头，完整许可在 licenses/Media3-Apache-2.0.txt。

主要改动：

- 不输出音视频样本；大块音视频数据通过 HTTP Range 定位跳过，小块在当前请求内跳过。
- 读取 CueTrack、CueClusterPosition 与 CueRelativePosition；各字幕轨都有相对定位索引时，优先跳至字幕块。
- 索引缺失、位置越界或原文件长度不明时保留逐块解析路径；不解密 DRM，不转换视频格式。
- 显式使用 DefaultSubtitleParserFactory，字幕样本与当前 Media3 TextRenderer 格式一致。
- 主播放器实际快进时重建辅助读取，暂停时同步位置；手动关闭与语言选择不被自动策略覆盖。

依据：Matroska 推荐每个字幕帧都有 CuePoint，并使用 CueRelativePosition 标明块位置。
https://www.matroska.org/technical/cues.html
https://www.matroska.org/technical/elements.html

测试素材均由 FFmpeg testsrc2/color 视频、合成音频和自写中英字幕生成，不包含用户影片、网盘账号或临时播放地址：

- app/src/androidTest/assets/subtitle-fixture.mkv：本地中英字幕、真实显示、快进与暂停同步。
- app/src/test/resources/subtitle-skip-fixture.mkv：15 秒、320×180、15 fps 的测试图案与中英 SRT，用于与标准提取器逐字节及时间戳对照。
- app/src/test/resources/subtitle-index-fixture.mkv：在同一合成文件的 Cues 中加入字幕块相对索引；用于验证索引定位后的完整字幕、读取量与请求次数。

单元测试 SubtitleMatroskaExtractorTest 逐轨比较标准提取器与修改版输出的全部字幕时间戳和内容，同时确认修改版不输出音视频样本。真实网盘的播放、连续快进与退出续播另外执行；单元通过不等同于所有真实文件通过。