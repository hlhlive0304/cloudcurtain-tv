云幕 TV 0.1.15-preview · 模拟器验证记录（2026-10-11）

当前范围：按用户最新要求，暂缓阿里原文件 4K；提供天翼普通视频与夸克兼容线路的修复测试包。兼容播放通过不等于原画通过。天翼指定 Dolby Vision 文件在模拟器仍解码失败，不能标为通过。

测试设备：Android TV 11 / API 30 / x86 模拟器，192 MiB Java 堆，1280×720 显示。账号保存在测试设备内。日志只记录状态、分辨率、响应码和文件 ID 的哈希，不记录账号、登录凭据、豆包密钥或签名地址。

| 项目 | 结果 | 证据与限制 |
| --- | --- | --- |
| 百度真实账号 | 通过 | 当前目录 58 个 MP4；抽测 6.6 GiB 视频，首帧、持续前进、中段快进、退出后进度恢复通过；新增网络等待检测后的最后回归也通过。 |
| 夸克地址过期恢复 | 通过 | 新版兼容线路故意返回 HTTP 403 后自动重取地址；首帧约 6 秒，45 秒连续播放、中段、片尾及退出续播通过。 |
| 夸克《复仇者联盟4》播放恢复 | 兼容通过，原文件未通过 | 直接兼容播放 90 秒中 87 秒正在播放、3 秒缓冲；实际 480×202 AVC。自动模式先请求原文件，模拟器 HEVC 4K 解码失败后自动切换兼容线路，新版 90 秒中 85 秒正在播放，5 秒缓冲。中段、片尾、退出续播通过。原下载线路抽测约 117 KiB/s，不能把兼容通过当成 4K 通过。 |
| 阿里《复仇者联盟4》兼容播放 | 通过 | 原文件 65,904,375,558 字节，兼容视频 1280×720 AVC；90 秒播放、快进通过；另测片尾及退出续播通过，首帧约 2 秒。原文件下载地址仍为空。 |
| 天翼会话恢复 | 通过 | 官网 SSO 跳转后目录恢复；故意置入失效文件会话，自动恢复、验证后保存，再次读取目录通过。 |
| 天翼播放地址过期恢复 | 通过 | 本轮新版故意返回 HTTP 403，自动重取同画质真实 CDN 地址，首帧约 6 秒；60 秒连续播放、中段快进、片尾、退出续播通过。 |
| 天翼 H.264 基线 | 通过 | 278 MiB 原文件；首帧、45 秒连续播放、中段快进、片尾、退出续播通过。 |
| 天翼《复仇者联盟4》原文件 | 未通过 | 76,315,188,624 字节，3840×2020，dvhe.08.06，video/dolby-vision；最新直接 CDN 播放的头部、中段、尾部 HTTP 206 正常，播放器没有 HTTP 错误；连续读取 64 MiB 用时 4.357 秒，约 14.7 MiB/s，但播放器 ERROR_CODE_DECODING_FAILED，无出画面。不能据此判断实际电视能否解码。 |
| 最终安装包回归 | 通过 | 交付同一 APK 再次安装，天翼首帧约 3 秒、夸克兼容首帧约 1 秒；播放前进、中段快进、退出恢复及续播通过；直连 CDN 旧会话客户端被拒绝，未清除任何保存账号。 |
| 四网盘文件浏览 | 通过 | 真实目录读取通过；类型/来源筛选、排序、四种视图、分栏切换、重进保存通过；1.3 倍字体检查通过。筛选和排序针对当前已加载文件。 |
| 电影墙真实账号 | 通过 | 27 个已索引视频；3 次进入退出、12 次网盘筛选、9 次详情打开；首次加载最多 547 ms，UI 往返最多 16 ms，堆峰值约 15 MiB；没有意外返回首页。 |
| 电影墙大库压力 | 通过 | 3000 条测试索引、每页最多 24 张卡片；6 次进入退出和反复滚动；保留真实账号索引并在测试后恢复。 |
| 天翼长时间等待 | 通过 | 自动模式在长等待后重取一次同画质地址；恢复次数用完后，等待超时暂停并提供重试/返回入口；不自动虚构低清线路。 |
| 豆包字幕 | 已有实测通过 | 实际文本和 PGS 裁片识别翻译、暂停/快进同步、关闭停止请求通过。只有用户开启翻译后发送字幕裁片。豆包片名识别不等于从无字幕的电影凭空生成时间轴。 |
| 自动检查 | 通过 | 152 个单元测试，150 通过、2 个需要在线服务的测试按现有规则跳过，0 失败、0 错误；构建与 Android Lint 通过。 |

本次增加播放直连：用单字节 Range 请求确认门户返回的 HTTPS CDN，再使用该地址播放；校验来源与 Content-Range，保留签名查询参数；直连 CDN 也受单盘退出会话代次校验约束。复测《复联4》读取到完整视频信息，播放器没有 HTTP 错误，但仍因模拟器解码能力失败。模拟器仅有一个 HEVC 解码器，格式能力检查不支持该片 HEVC Main10；未把单纯分辨率支持当成此片可以解码。

本次修正了天翼续期：文件会话失效时，先在受限的天翼官方域名内完成 SSO 跳转，再获取 portal sessionKey；只有原请求成功后才保存新会话。空值、占位值、失败的 portal 返回不会污染已保存凭据。另将网络播放的等待超时检测覆盖天翼和百度，保留阿里/夸克已有的兼容线路策略。

阿里原画检查现已按用户要求暂缓。保留此前结果：阿里手机官方 App 可以播放原画/4K（用户已确认），因此没有把问题归因为用户没有播放权益。旧 PDS 接口、正式官网登录授权令牌、官网登录页面的实际 SDK 调用均没有返回此文件的原地址。官方 OpenSDK 路线尚未验证：用户目前没有自己的开放平台 App ID；没有使用其他应用的 App ID 或密钥。

关键日志随源码和交付目录提供：

- [tianyi-cdn-generation-final](tianyi-cdn-generation-final-0.1.15.txt)
- [tianyi-installed-final](tianyi-installed-final-0.1.15.txt)
- [quark-installed-final](quark-installed-final-0.1.15.txt)
- [tianyi-cdn-recovery-play-final](tianyi-cdn-recovery-play-final-0.1.15.txt)
- [tianyi-wait-delivery](tianyi-wait-delivery-0.1.15.txt)
- [tianyi-renew-integration](tianyi-renew-integration-0.1.15.txt)
- [quark-auto-delivery](quark-auto-delivery-0.1.15.txt)
- [quark-expired-compatible-delivery](quark-expired-compatible-delivery-0.1.15.txt)
- [baidu-delivery-regression](baidu-delivery-regression-0.1.15.txt)
- [video-decoders-delivery](video-decoders-delivery-0.1.15.txt)
- [browser-four-providers-large-font-final](browser-four-providers-large-font-final-0.1.15.txt)
- [browser-interactions-final](browser-interactions-final-0.1.15.txt)
- [browser-large-font-interactions](browser-large-font-interactions-0.1.15.txt)
- [wall-real-accounts-final](wall-real-accounts-final-0.1.15.txt)
- [wall-stress](wall-stress-0.1.15.txt)
- [translation-pgs-sync-type](translation-pgs-sync-type-0.1.15.txt)
- [translation-text-final](translation-text-final-0.1.15.txt)
- [tianyi-dv-cdn-final](tianyi-dv-cdn-final-0.1.15.txt)
- [tianyi-dv-delivery](tianyi-dv-delivery-0.1.15.txt)
- [tianyi-sustained-network-final](tianyi-sustained-network-final-0.1.15.txt)
- [build-delivery-final](build-delivery-final-0.1.15.txt)
