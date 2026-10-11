# 在线更新发布

仓库：https://github.com/hlhlive0304/cloudcurtain-tv

安装包：GitHub Releases。版本清单：仓库 main 分支的 update.json。公开下载不需要在电视内配置 GitHub 账号或令牌。预览版也由清单指向具体 Release，不依赖 GitHub 的 latest 规则。

0.1.15 及以前没有更新客户端；首次需要手动覆盖安装 0.1.16-preview。之后在首页或“字幕与设置”进入“检查更新”，选择下载，再选择安装。Android 8.0+ 首次可能需要在系统设置允许云幕 TV 安装应用；授权后返回更新页重新选择安装。最终由系统提示确认，不能静默安装。

## 每次发布

1. 递增 app/build.gradle 的 versionCode，修改 versionName，使用原来的签名密钥构建。当前包名是 cn.cloudcurtain.tv；更换包名或签名将不能覆盖升级。
2. 在模拟器完成播放回归和更新流程验证，编写本次修正及未解决限制。
3. 用 tools/prepare-release.py 生成安装包、SHA256SUMS.txt 和 update.json。脚本通过 Android aapt/apksigner 核对包名、版本和原签名；不会登录或上传。
4. 将本次源码提交到仓库。Releases → Draft a new release，创建与清单一致的版本标签，如 v0.1.16-preview，选择本次源码提交，填入更新说明。
5. 上传脚本生成的同名 APK、SHA256SUMS.txt 和对应源码包。预览版选择“这是预发布版本”，发布 Release。
6. 确认 APK 下载成功后，最后把生成的 update.json 提交到 main 分支。不要先指向尚未上传的安装包。清单的 URL、版本号、大小和 SHA256 必须对应实际上传文件。
7. 用当前版 App 检查更新，确认获取到版本、下载校验通过并能打开系统安装界面；覆盖后核对版本及账号保留。

示例（路径按本机构建环境填写）：

```powershell
python tools/prepare-release.py --apk app/build/outputs/apk/debug/app-debug.apk --build-tools C:/Android/sdk/build-tools/35.0.0 --notes release-notes.txt --output-dir release
```

需要 JDK 17，Java 位于 PATH，或通过 --java 指定 java.exe。脚本输出 release 目录，不自动发布；从中复制 update.json 到仓库根目录。安装包名字由 APK 内的真实版本生成。

## 校验与范围

更新客户端只访问本仓库的 HTTPS 清单与 Release 文件，允许 GitHub 的官方安装包存储跳转。请求不携带网盘 Cookie、令牌、豆包密钥或文件信息。下载限大小、可取消，未完成文件留在应用私有缓存并清理；校验 SHA256、包名、数字版本和原签名后才能安装，安装前再复核一次。

GitHub 在某些网络中可能不可达；网络失败时保留当前版本并允许重试。本版没有加入广告、遥测或付费更新服务。

签名私钥需在本机单独备份，不上传到公开仓库。个人 API 配置、配置码、测试账号和模拟器数据不属于源码或 Release。本次公开版不包含个人配置资产；覆盖安装保留电视原有的加密配置。
