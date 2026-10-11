# 第三方组件与接入说明

本项目采用 AGPL-3.0-or-later，完整源代码与 APK 一同提供。

* AndroidX Media3 1.6.1 — Google / Android Open Source Project，Apache-2.0。
  https://github.com/androidx/media
* OkHttp 4.12.0 — Square / OkHttp contributors，Apache-2.0。
  https://github.com/square/okhttp
* ZXing Core 3.5.3 — ZXing contributors，Apache-2.0。将官方返回或官方认证框识别到的扫码内容在本机编码成二维码，不上传到二维码生成网站。
  https://github.com/zxing/zxing
* Kotlin 标准库（OkHttp 传递依赖）— JetBrains，Apache-2.0。
* Bouncy Castle bcprov-jdk18on 1.78.1 — The Legion of the Bouncy Castle Inc.，MIT 风格许可，用于阿里设备会话的 secp256k1 签名。许可全文随源码提供于 licenses/BouncyCastle.txt。
  https://www.bouncycastle.org/licence.html
* Guava、AndroidX 与其他传递依赖采用各自许可证，构建依赖清单可通过 Gradle dependencies 生成。
* 天翼、夸克只读接口形状参考 OpenList 项目中的 drivers/189 和 drivers/quark_uc，
  并独立编写 Java 实现。参考来源：https://github.com/OpenListTeam/OpenList （AGPL-3.0）。

没有复制当贝播放器的源代码、图标、商标或素材，只借鉴常见的大屏操作方式。
百度、夸克、阿里、天翼的名称用于标识用户访问的官方服务，不代表官方合作。
官网、账号权益、风控以及所有接口由相应服务提供商控制。

天翼二维码参数处理对照官方静态资源：
https://static.e.189.cn/open/login/page/web/v5.0/static/js/App.7bf19c91.js
夸克网页转码请求的端点、格式参数与返回结构对照官方公开应用脚本（2026-09-23）：
https://g.alicdn.com/uc-cloud-drive-web-system/cloud-drive-web/4.6.7/index.js
天翼门户会话初始化对照官网 chunk-common.c399c49d.js / index.66304bd4.js；阿里官网完整登录流程对照官方 h5-sign/1.1.4、h5-auth/0.1.6 与 havana-nlogin/0.10.37 公开脚本。二维码仍由官方认证页面生成，旧 WebView 显示官方认证框，并尝试从其画面识别二维码，不自行生成认证会话或设备风控参数。
阿里设备会话格式对照 https://www.alipan.com/drive 公开网页 bundle 的 createSession/genSignature；其中 app_id 是官网公开客户端标识，不是密钥。设备私钥由本电视随机生成，没有复制其他客户端的私钥或秘密凭据。

在线字幕使用 OpenSubtitles.com REST API v1。用户需要提供自己的 API Key；下载可能还需
账号和可用配额。本应用不提供或共享第三方 API 密钥。字幕内容权利由各自权利人持有。

本应用不含影视资源，也不对加密 / DRM 内容解密，不绕过会员和账号权限限制。

阿里当前用户信息 hostType=user 对照官网 6.8.12-web 公开 bundle：
https://g.alicdn.com/aliyun-drive-fe/aliyun-drive/6.8.12-web/web/bundle.js
在线播放与字幕结构另参考官方 PDS GetVideoPreviewPlayInfo 文档：
https://help.aliyun.com/zh/pds/drive-and-photo-service-dev/developer-reference/api-pds-2022-03-01-getvideopreviewplayinfo
只使用当前账号实际返回的完整 url，不使用预览片段链接或受限档位，也不解密 DRM。
电影元数据与海报：The Movie Database (TMDB)。需要用户自己的 API Key / Read Access Token。
This product uses the TMDB API but is not endorsed or certified by TMDB.
https://www.themoviedb.org
API 接入对照官方文档：https://developer.themoviedb.org/docs/authentication-application
https://developer.themoviedb.org/reference/search-movie
https://developer.themoviedb.org/docs/image-basics
批准的品牌图标来源：https://www.themoviedb.org/about/logos-attribution
https://www.themoviedb.org/assets/2/v4/logos/v2/blue_square_2-d537fb228cf3ded904ef09b136fe3fec72548ebc1fea3fbbd1ad9e36364db38b.svg
图标路径等比例转换为 Android VectorDrawable，API 24+ 保留原渐变，API 23 使用其批准的浅蓝单色。
封面图片和元数据权利归各自权利人，TMDB 数据 / 图片使用受其 API 条款约束，不作为 AGPL 代码授权。
公开网络封面：豆瓣电影公开搜索与图片页面；维基百科 / Wikimedia 文章图片为补充来源。封面权利归各自权利人，不作为项目 AGPL 源码授权。保留来源页面，应用只在本机缓存所选图片，不上传或镜像发行海报数据集。
https://movie.douban.com/
https://www.mediawiki.org/wiki/Extension:PageImages#API
Wikimedia 每张图片的许可应以其 File: 来源页为准；不能将所有文章图片宣称为自由许可。
SubtitleMatroskaExtractor.java 由 Google Media3 1.6.1 MatroskaExtractor 修改而来（Apache-2.0），保留上游版权与许可证；改动为字幕专用读取时将未消费的音视频 Block 转为字节定位，原视频播放器继续使用标准解复用器。固定上游版本，不适用于普通视频播放。
https://github.com/androidx/media/blob/1.6.1/libraries/extractor/src/main/java/androidx/media3/extractor/mkv/MatroskaExtractor.java
许可证：licenses/Media3-Apache-2.0.txt。Checker Framework checker-qual 3.43.0 仅编译期空值注解（MIT），不打包运行时。