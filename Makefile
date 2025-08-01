# 需要执行权限?内置的工具是如何不需要的?
.PHONY: chmod
chmod:
	@test -x ./gradlew || chmod +x ./gradlew

# 常用 Gradle 命令
# 以下是 Android 项目中常用的 Gradle 命令：

## 查看任务
# ./gradlew tasks：列出所有可用的 Gradle 任务。
# ./gradlew tasks --all：显示更详细的任务列表。
.PHONY: tasks
tasks: chmod
	./gradlew tasks

## 依赖管理
# ./gradlew dependencies：列出项目的依赖树，用于排查依赖冲突。
# ./gradlew resolveDependencies：强制解析并缓存依赖。
.PHONY: deps
deps: chmod
	./gradlew dependencies

# 构建项目
# ./gradlew build：编译并构建整个项目，包括调试和发布版本的 APK/AAB，运行测试。
# ./gradlew assemble：仅生成 APK/AAB，不运行测试。
# ./gradlew assembleDebug：生成调试版本的 APK/AAB。
# ./gradlew assembleRelease：生成发布版本的 APK/AAB（需配置签名）。
.PHONY: build
build: chmod
	./gradlew assembleDebug

# 运行测试
# ./gradlew test：运行所有单元测试。
# ./gradlew connectedCheck：运行连接设备或模拟器的仪器化测试。
# ./gradlew testDebugUnitTest：运行调试版本的单元测试。
.PHONY: test
test: chmod
	./gradlew test

# 安装应用
# ./gradlew installDebug：将调试版本的 APK 安装到连接的设备或模拟器。
# ./gradlew installRelease：安装发布版本的 APK。
.PHONY: install
install: chmod
	./gradlew installDebug

# 清理项目
# ./gradlew clean：删除构建输出（如 build/ 目录），重新构建时使用。
.PHONY: clean
clean: chmod
	./gradlew clean

