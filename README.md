# Player Presence Logger

Minecraft Java **1.21.1 / NeoForge** 的专用服务端 Mod，用文件记录玩家上线和下线时间。

## 行为

- 仅在专用服务端启用；入口标注 `@Mod(dist = Dist.DEDICATED_SERVER)`。
- 客户端无需安装；即使安装，也不会初始化日志功能、创建配置或写日志。单人游戏、局域网开放的内置服务器也不会启用。
- 使用 NeoForge 的 `PlayerLoggedInEvent` 和 `PlayerLoggedOutEvent`；不需要客户端自定义网络通信。
- 默认将记录追加到服务器目录下的 `logs/player-presence/YYYY-MM-DD.csv`。
- UTF-8 编码，记录时间（精确到毫秒并含 UTC 偏移）、LOGIN/LOGOUT、玩家名、UUID。
- 每条事件写入后关闭文件，重启不会覆盖已有记录。文件按配置时区的日期分组。
- 使用的是服务器检测到登录/退出事件的时间。掉线、超时、踢出等情况在服务器确认玩家离开时记录。
- 服务器进程崩溃、断电或被强制终止时，无法记录尚未触发的退出事件。
- 写文件失败会写入服务器错误日志，不会阻止玩家进出；失败的记录不会自动重试。

## 安装

运行环境：Java 21、Minecraft 1.21.1、NeoForge 21.1 分支。构建配置使用 21.1.252；较旧版本尚未实机验证。

1. 将 `player-presence-logger-1.0.0.jar` 放进**服务器**的 `mods` 文件夹。
2. 启动服务器。首次启动自动创建 `config/player-presence-logger.properties`。
3. 玩家进入或离开后，到 `logs/player-presence` 查看当天的 CSV 文件。

只下载源码时，请先按下面的步骤编译。源码 ZIP 本身不能放进 `mods` 文件夹。

## 配置

文件：`config/player-presence-logger.properties`。修改后重启服务器。

```properties
logDirectory=logs/player-presence
timeZone=Asia/Shanghai
```

- `logDirectory`：相对服务器根目录的路径，也支持绝对路径。Windows 路径建议使用 `/`，例如 `D:/minecraft/player-logs`。
- `timeZone`：默认 `Asia/Shanghai`（UTC+8），也可设为 `Asia/Singapore`、`UTC` 等 Java 支持的时区。
- 配置无效时，该次启动使用默认设置，并在控制台输出错误。

示例文件 `logs/player-presence/2026-10-12.csv`：

```csv
timestamp,event,player,uuid
2026-10-12T20:35:24.123+08:00,"LOGIN","Steve",00000000-0000-0000-0000-000000000001
2026-10-12T21:06:10.456+08:00,"LOGOUT","Steve",00000000-0000-0000-0000-000000000001
```

UUID 是主要身份标识。上线、下线可以跨日期，查找时请同时查看对应日期的文件。

## 从源码构建

安装完整的 **JDK 21**，设置 `JAVA_HOME` 后，在项目根目录执行：

Windows PowerShell：

```powershell
.\gradlew.bat build
```

Linux / macOS：

```bash
chmod +x gradlew
./gradlew build
```

构建首次运行需要联网下载 Gradle、NeoForge 和 Minecraft 开发依赖。
成品位置：`build/libs/player-presence-logger-1.0.0.jar`。
构建时会执行文件写入验证，检查追加、重启保留、跨天、UTF-8、CSV 转义、配置与 I/O 错误。

开发测试可用 `./gradlew runServer` 和 `./gradlew runClient`。
启动开发服务器前请自行阅读并接受 Minecraft EULA；项目不会替你接受。

## Docker Compose 构建

在包含 `compose.yaml` 和 `build.gradle` 的项目根目录执行：

```bash
docker compose run --rm build
```

使用 JDK 21 容器编译，通过验证后将 JAR 复制到当前项目目录。
首次构建需要联网；`gradle-cache` 命名卷保存构建依赖，`--rm` 不会删除该卷。
需要清空缓存时，执行 `docker compose down -v`。

## GitHub Actions 手动构建并发布

工作流文件：`.github/workflows/release.yml`，仅支持手动触发，提交代码不会自动发布。

1. 打开仓库的 **Actions** 页面，选择 **Build and Release JAR**。
2. 点击 **Run workflow**，选择要构建的分支（默认 `master`）。
3. 输入 `version`，例如 `1.0.0` 或 `1.0.1-rc.1`，无需加 `v`。
4. 如需预发布，勾选 `prerelease`，然后运行。
5. 编译和文件写入验证全部通过后，工作流创建 `v<version>` 标签和 Release，并上传 JAR。

成品可在 [Releases](https://github.com/pininkara/mc-player-logger/releases) 下载；
同时保存为该次 Actions 运行的 Artifact，保留 14 天。
输入的版本号会用于 JAR 文件名及 Mod 内部版本，不会修改仓库中的 `gradle.properties`。
Release 的标签指向本次实际构建的提交。已有同名标签时拒绝发布，请使用新版本号。
构建失败时不会运行发布步骤；发布失败时，仍可从成功的构建步骤下载 Artifact。

使用 GitHub 自动提供的 `GITHUB_TOKEN`，无需额外配置个人访问令牌。
构建任务只有读取代码的权限；发布任务授予 `contents: write` 用于创建 Release。

## 实机检查

1. 启动专用服务器，用客户端连接、退出、重新连接：每次应各产生一条 LOGIN/LOGOUT。
2. 踢出玩家后检查 LOGOUT；正常停止服务器后检查仍在线玩家的退出记录。
3. 重启服务器，确认历史记录保留且表头未重复。
4. 在客户端安装同一 JAR，运行单人世界，确认不创建本 Mod 的配置和日志。
5. 使用未安装本 Mod 的 NeoForge 客户端连接，确认可以正常加入。

## 参考

- NeoForge 1.21.1 服务端限制：[Sides](https://docs.neoforged.net/docs/1.21.1/concepts/sides/)
- [官方 1.21.1 ModDevGradle MDK](https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle)
- [构建说明](https://docs.neoforged.net/docs/1.21.1/gettingstarted/)

本项目采用 MIT 协议；Gradle Wrapper 来自官方 MDK，参见 THIRD-PARTY-NOTICES.md。

## 验证状态

已用 JDK 21 编译日志与配置核心并运行文件写入验证，全部通过。
已核对官方 NeoForge 1.21.1 文档中的专用服务端入口限制，以及 21.1.252 源码中的登录、退出事件。
完整构建和文件写入验证会在手动运行 GitHub Actions 时执行。尚未进行 Minecraft 实机测试。
源码仓库不提交预编译 JAR，请从成功运行后的 Release 下载，或使用上面的本地及 Docker 构建命令。
