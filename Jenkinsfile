pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '30', artifactNumToKeepStr: '15'))
    }

    parameters {
        choice(
            name: 'BUILD_TYPE',
            choices: ['debug', 'release'],
            description: '构建 Debug 测试包或 Release 正式包'
        )
        choice(
            name: 'BUILD_ENV',
            choices: ['dev', 'test', 'prod'],
            description: '应用运行环境'
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
                        sh 'chmod +x ./gradlew && ./gradlew --version'
                    } else {
                        bat '.\\gradlew.bat --version'
                    }
                }
            }
        }

        stage('Build APK') {
            steps {
                script {
                    def buildType = params.BUILD_TYPE.capitalize()
                    def taskName = ":${env.APP_MODULE}:assemble${buildType}"
                    def versionName = params.VERSION_NAME?.trim()
                        ? params.VERSION_NAME.trim()
                        : "1.0.${env.BUILD_NUMBER}"
                    if (!(versionName ==~ /[0-9A-Za-z._-]+/)) {
                        error 'VERSION_NAME 只能包含数字、字母、点、下划线和连字符'
                    }
                    def gradleArgs = [
                        'clean',
                        taskName,
                        "--console=plain",
                        "-PbuildEnv=${params.BUILD_ENV}",
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
                archiveArtifacts(
                    artifacts: "${env.APP_MODULE}/build/outputs/apk/${params.BUILD_TYPE}/**/*.apk",
                    fingerprint: true
                )
            }
        }

        stage('Upload Pgyer') {
            when {
                expression { params.UPLOAD_PGYER }
            }
            steps {
                withCredentials([
                    string(credentialsId: 'pgyer-api-key', variable: 'PGYER_API_KEY')
                ]) {
                    withEnv(["PGYER_BUILD_DESC=${params.BUILD_DESC}"]) {
                        script {
                            def apkDir = "${env.APP_MODULE}/build/outputs/apk/${params.BUILD_TYPE}"
                            if (isUnix()) {
                                sh "java ci/PgyerUploader.java '${apkDir}'"
                            } else {
                                bat "java ci\\PgyerUploader.java \"${apkDir}\""
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
            echo "构建成功，可在 Artifacts 中下载 APK。"
        }
        failure {
            echo "构建失败，请查看 Console Output。"
        }
    }
}
