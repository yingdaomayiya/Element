# Jenkins 远程打包配置

项目已经支持在 Jenkins 网页通过 **Build with Parameters** 构建 Debug/Release APK，并可选上传蒲公英。

## Jenkins 构建节点

节点需要安装：

- JDK 17
- Android SDK Platform 35、Build Tools 35
- Git
- 能访问 Gradle、Google Maven、Maven Central 和蒲公英

项目始终使用仓库内的 Gradle Wrapper，不依赖 Jenkins 全局 Gradle。

## Jenkins Credentials

在 Jenkins 的 Credentials 中创建以下凭据，ID 必须与表格一致：

| Credentials ID | 类型 | 用途 |
| --- | --- | --- |
| `android-release-keystore` | Secret file | Release 签名证书 |
| `android-keystore-password` | Secret text | Keystore 密码 |
| `android-key-alias` | Secret text | Key alias |
| `android-key-password` | Secret text | Key 密码 |
| `pgyer-api-key` | Secret text | 蒲公英 API Key |

签名密码和蒲公英密钥不会写入代码仓库。

## 创建 Pipeline

1. Jenkins 新建 `Pipeline`。
2. Definition 选择 `Pipeline script from SCM`。
3. 配置 Git 仓库和凭据。
4. Script Path 填写 `Jenkinsfile`。
5. 首次运行后，任务页面会出现 **Build with Parameters**。

可选参数：

- `BUILD_TYPE`：`debug` / `release`
- `BUILD_ENV`：`dev` / `test` / `prod`
- `UPLOAD_PGYER`：是否上传蒲公英
- `VERSION_NAME`：为空时自动使用 `1.0.<Jenkins BUILD_NUMBER>`
- `BUILD_DESC`：蒲公英更新说明

Jenkins 构建号会自动作为 Android `versionCode`。

## 本地验证

Debug：

```powershell
.\gradlew.bat clean :app:assembleDebug `
  -PbuildEnv=test `
  -PciVersionCode=2 `
  "-PciVersionName=1.0.2"
```

Release 需要先设置签名环境变量：

```powershell
$env:ANDROID_KEYSTORE_FILE = "D:\secure\release.jks"
$env:ANDROID_KEYSTORE_PASSWORD = "store-password"
$env:ANDROID_KEY_ALIAS = "release"
$env:ANDROID_KEY_PASSWORD = "key-password"

.\gradlew.bat clean :app:assembleRelease `
  -PbuildEnv=prod `
  -PciVersionCode=2 `
  "-PciVersionName=1.0.2" `
  -PrequireReleaseSigning=true
```

APK 位于 `app/build/outputs/apk/<debug|release>/`。

## 环境地址

`app/build.gradle.kts` 中预留了 `dev/test/prod` 的 `BASE_URL` 示例地址。接入真实业务前，请将三个 `example.com` 地址替换为实际接口地址；应用代码可通过 `BuildConfig.BASE_URL` 和 `BuildConfig.BUILD_ENV` 读取。

## 手表项目

远程打包流程与手机、Wear OS 项目一致。当前工程仍是普通 Android Compose 模板；如果目标是 Wear OS，请再将 UI 和依赖迁移到 Wear Compose，但不影响本 Jenkins 流水线。
