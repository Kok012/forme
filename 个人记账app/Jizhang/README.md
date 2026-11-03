# 记账 · Android 离线个人账本

暖白与薄荷绿风格的个人消费记录工具。单用户、单账本，无登录、无广告、无应用网络权限。支持 Android 8.0 及以上版本。

## 功能

- 消费添加、编辑、删除，备注与消费日期，连续记账。
- 12 个内置分类，自定义分类、改名、图标颜色、排序和归档。
- 月份切换、分类筛选、日期分组、每日合计。
- 月消费总额、分类占比、每日趋势及明细钻取。
- 本地 JSON 完整备份与原子恢复，恢复前快照及撤销恢复。
- 月度 CSV 导出，浅色、深色和跟随系统主题。

正式数据库不写入演示消费。金额以整数“分”保存；消费日期使用自然日，不随时区变化迁移到其他月份。

## 工程环境

| 项目 | 固定版本 |
| --- | --- |
| Java | 17 |
| Gradle | 8.11.1 |
| Android Gradle Plugin | 8.9.2 |
| Kotlin / Compose Compiler | 2.1.20 |
| KSP | 2.1.20-2.0.1 |
| Compose BOM | 2025.04.01 |
| Room | 2.7.1 |
| compileSdk / targetSdk / minSdk | 35 / 35 / 26 |

依赖的确切版本在 `gradle/libs.versions.toml`。原生工程采用单 app 模块，页面通过 ViewModel / Repository 访问 Room；偏好使用 DataStore。

版本选择依据：[Android 插件与 Gradle 兼容表](https://developer.android.com/build/releases/about-agp)、[Kotlin Compose 插件说明](https://kotlinlang.org/docs/compose-compiler-migration-guide.html)、[Room 发布说明](https://developer.android.com/jetpack/androidx/releases/room)。这些是经过选择的固定版本，不表示使用所有组件的最新版本。

## 构建

使用 Android Studio 打开本目录，安装 Android API 35 和 Build Tools 35.0.0，选择 JDK 17。首次准备依赖需要网络，运行应用无需网络。

Windows 上建议把解压后的工程放在完整路径均为英文的位置，例如 `E:/Android/Jizhang`，以减少 Java 17 测试进程的路径兼容问题。

配置 `local.properties` 指向本机 SDK；这个文件不提交到源码仓库。例如 Windows 使用正斜杠路径：

```properties
sdk.dir=E:/Android/Sdk
```

命令行构建：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
.\gradlew.bat assembleRelease
```

在本次工作目录中也可以使用 `scripts/Build.ps1`，该脚本复用工作目录下的 `.jizhang-build-tools`，并把编译输出放到其中的 `outputs/app`，避免 Windows/JDK 17 对中文测试类路径的兼容问题。可以通过 `-ToolsRoot` 显式指定工具根目录。直接使用 Gradle Wrapper 时默认仍输出到工程的 `app/build`。离线构建必须先准备全部 SDK 和依赖缓存，再使用 Gradle 的 `--offline`。

Release 签名读取未提交的 `signing.properties`，密钥保存在工程外侧的 `私密签名`。没有签名配置时 Gradle 可生成 unsigned APK，安装个人发布版需先配置签名；不要用不同密钥覆盖已有应用。

## 使用与安装

详细步骤见 [使用说明.md](使用说明.md)。构建后的安装包和校验信息位于外侧的 `交付` 文件夹；具体构建验证情况见 [验证记录.md](验证记录.md)。

系统文件选择器只请求所选文件的访问权限。应用关闭系统自动备份，并配置云备份、设备迁移排除规则；厂商迁移工具行为仍需在实际手机验证。手动备份是账本带走和迁移的正式方式。

## 数据与维护

- 不使用破坏性迁移；数据库结构版本快照位于 `app/schemas`。
- 改动数据库实体时需要增加版本并添加明确迁移与迁移测试。
- JSON 是明文备份，包含全部分类与消费；请保存在自己控制的位置。
- 恢复是完整替换，先预览笔数、金额和日期范围；失败则回滚。
- CSV 供表格查看，不作为完整恢复入口。
- 应用私有目录中的恢复前快照不代替应用之外的备份。

未实现的后续功能：收入、多账本、预算、退款管理、支付平台自动导入、云同步、生物识别和加密备份。
