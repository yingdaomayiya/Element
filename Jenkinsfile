pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '30', artifactNumToKeepStr: '15'))
        skipDefaultCheckout(true)
    }

    parameters {
        choice(
            name: 'BUILD_TYPE',
            choices: ['debug', 'release'],
            description: '构建 Debug 测试包或 Release 正式包'
        )

        choice(
            name: 'FLAVOR',
            choices: ['dev', 'test', 'prod'],
            description: '选择产品环境 Flavor'
        )

        booleanParam(
            name: 'UPLOAD_PGYER',
            defaultValue: false,
            description: '构建成功后上传蒲公英'
        )

        string(
            name: 'VERSION_NAME',
            defaultValue: '',
            description: '版本名；留空时使用 1.0.Jenkins构建号'
        )

        text(
            name: 'BUILD_DESC',
            defaultValue: 'Jenkins 自动构建',
            description: '蒲公英更新说明'
        )
    }

    environment {
        APP_MODULE = 'app'
        PGYER_INSTALL_TYPE = '1'

        // JDK 17 根目录，不要写到 /bin/java
        JAVA_HOME = '/usr/local/jdk/jdk-17.0.13'

        // Jenkins 服务器已安装的 Android SDK
        ANDROID_HOME = '/opt/android-sdk'
        ANDROID_SDK_ROOT = '/opt/android-sdk'

        // 让 Jenkins 当前任务优先使用指定的 JDK 和 Android SDK 工具
        PATH = "/usr/local/jdk/jdk-17.0.13/bin:/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:${env.PATH}"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Prepare') {
            steps {
                script {
                    if (isUnix()) {
                        sh '''
                            echo "========== Java 环境 =========="
                            echo "JAVA_HOME=$JAVA_HOME"
                            which java
                            java -version

                            echo "========== Android SDK 环境 =========="
                            echo "ANDROID_HOME=$ANDROID_HOME"
                            echo "ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT"

                            test -d "$ANDROID_HOME" || {
                                echo "Android SDK 目录不存在：$ANDROID_HOME"
                                exit 1
                            }
                            test -d "$ANDROID_HOME/platforms/android-35" || {
                                echo "缺少 Android SDK Platform 35"
                                exit 1
                            }
                            test -d "$ANDROID_HOME/build-tools/35.0.0" || {
                                echo "缺少 Android SDK Build Tools 35.0.0"
                                exit 1
                            }

                            # local.properties 仅在 Jenkins 工作区临时生成，不提交到 Git
                            printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
                            cat local.properties

                            echo "========== Gradle 环境 =========="
                            chmod +x ./gradlew

                            # 停止旧 Gradle Daemon，避免复用 Java 8
                            ./gradlew --stop || true

                            ./gradlew --version
                        '''
                    } else {
                        bat '''
                            echo ========== Java 环境 ==========
                            echo JAVA_HOME=%JAVA_HOME%
                            where java
                            java -version

                            echo ========== Android SDK 环境 ==========
                            echo ANDROID_HOME=%ANDROID_HOME%
                            echo sdk.dir=%ANDROID_HOME%>local.properties
                            type local.properties

                            echo ========== Gradle 环境 ==========
                            .\\gradlew.bat --stop
                            .\\gradlew.bat --version
                        '''
                    }
                }
            }
        }

        stage('Build APK') {
            steps {
                script {
                    // Android Gradle Plugin 不允许 Flavor 名以 test 开头。
                    // Jenkins 对用户仍显示 test，构建时映射到内部 qa Flavor。
                    def gradleFlavor = params.FLAVOR == 'test' ? 'qa' : params.FLAVOR
                    def flavor = gradleFlavor.capitalize()
                    def buildType = params.BUILD_TYPE.capitalize()
                    def taskName = ":${env.APP_MODULE}:assemble${flavor}${buildType}"

                    def versionName = params.VERSION_NAME?.trim()
                        ? params.VERSION_NAME.trim()
                        : "1.0.${env.BUILD_NUMBER}"

                    if (!(versionName ==~ /[0-9A-Za-z._-]+/)) {
                        error 'VERSION_NAME 只能包含数字、字母、点、下划线和连字符'
                    }

                    def gradleArgs = [
                        'clean',
                        taskName,
                        '--console=plain',
                        "-PciVersionCode=${env.BUILD_NUMBER}",
                        "-PciVersionName=${versionName}"
                    ]

                    def runGradle = {
                        if (isUnix()) {
                            sh "./gradlew ${gradleArgs.join(' ')}"
                        } else {
                            bat ".\\gradlew.bat ${gradleArgs.join(' ')}"
                        }
                    }

                    if (params.BUILD_TYPE == 'release') {
                        gradleArgs.add('-PrequireReleaseSigning=true')

                        withCredentials([
                            file(credentialsId: 'android-release-keystore', variable: 'ANDROID_KEYSTORE_FILE'),
                            string(credentialsId: 'android-keystore-password', variable: 'ANDROID_KEYSTORE_PASSWORD'),
                            string(credentialsId: 'android-key-alias', variable: 'ANDROID_KEY_ALIAS'),
                            string(credentialsId: 'android-key-password', variable: 'ANDROID_KEY_PASSWORD')
                        ]) {
                            runGradle()
                        }
                    } else {
                        runGradle()
                    }
                }
            }
        }

        stage('Archive APK') {
            steps {
                script {
                    def gradleFlavor = params.FLAVOR == 'test' ? 'qa' : params.FLAVOR
                    archiveArtifacts(
                        artifacts: "${env.APP_MODULE}/build/outputs/apk/${gradleFlavor}/${params.BUILD_TYPE}/**/*.apk",
                        fingerprint: true
                    )
                }
            }
        }

        stage('Upload Pgyer') {
            when {
                expression {
                    return params.UPLOAD_PGYER
                }
            }

            steps {
                withCredentials([
                    string(credentialsId: 'pgyer-api-key', variable: 'PGYER_API_KEY')
                ]) {
                    withEnv([
                        "PGYER_BUILD_DESC=${params.BUILD_DESC}"
                    ]) {
                        script {
                            def gradleFlavor = params.FLAVOR == 'test' ? 'qa' : params.FLAVOR
                            def apkDir = "${env.APP_MODULE}/build/outputs/apk/${gradleFlavor}/${params.BUILD_TYPE}"

                            if (isUnix()) {
                                sh """
                                    echo "========== 上传蒲公英 =========="
                                    echo "APK_DIR=${apkDir}"
                                    java -version
                                """

                                sh "java ci/PgyerUploader.java '${apkDir}'"
                            } else {
                                bat """
                                    echo ========== 上传蒲公英 ==========
                                    echo APK_DIR=${apkDir}
                                    java -version
                                    java ci\\PgyerUploader.java "${apkDir}"
                                """
                            }
                        }
                    }
                }
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: 'pgyer_result.json', allowEmptyArchive: true
        }

        success {
            echo '构建成功，可在 Artifacts 中下载 APK。'
        }

        failure {
            echo '构建失败，请查看 Console Output。'
        }
    }
}
