pipeline {
    agent none
    
    environment {
        // Укажите имя образа Docker
        IMAGE_NAME = 'diplom-mysite'
        // Переменные окружения для telegram bot
        TELEGRAM_BOT_TOKEN_ID = 'jenkins_telegram_bot_token'
        TELEGRAM_CHAT_ID_ID = 'jenkins_telegram_bot_id'
    }
    
    stages {
        stage('Branch Check') {
            agent {
                label 'agent'
            }
            steps {
                script {
                    env.BRANCH = "${env.GIT_BRANCH.replaceFirst('origin/', '')}"
                    echo "Branch: ${env.BRANCH}"
                }
            }
        }
        
        stage('Checkout SCM') {
            when {
                expression { env.BRANCH == 'main' || env.BRANCH == 'dev' }
            }
            agent {
                label 'agent'
            }
            steps {
                // Этап Checkout SCM для извлечения кода из системы управления версиями
                checkout scm
                script {
                    def buildCause = currentBuild.getBuildCauses('hudson.model.Cause$UserIdCause')
                    if (buildCause) {
                        env.AUTHOR = buildCause[0].userId
                        env.AUTHOR_TYPE = "Jenkins user"
                    } else {
                        env.AUTHOR = sh(returnStdout: true, script: 'git log -1 --pretty=format:"%an"').trim()
                        env.AUTHOR_TYPE = "GitHub user"
                    }
                    echo "Author: ${env.AUTHOR} (${env.AUTHOR_TYPE})"
                }    
            }
        }

        stage('HTMLHint Check') {
            when {
                expression { env.BRANCH == 'main' || env.BRANCH == 'dev' }
            }
            agent {
                label 'agent'
            }
            steps {
                // Проверка всех HTML файлов с помощью HTMLHint
                sh 'htmlhint ./**/*.html'
            }
        }

        stage('Read version from file Version') {
            when {
                expression { env.BRANCH == 'main' || env.BRANCH == 'dev' }
            }
            agent {
                label 'agent'
            }
            steps {
                script {
                    // Чтение версии из файла
                    env.IMAGE_TAG = readFile('VERSION').trim()
                    echo "Using version: ${env.IMAGE_TAG}"
                }
            }
        }
        
        stage('Build') {
            when {
                expression { env.BRANCH == 'main' || env.BRANCH == 'dev' }
            }
            agent {
                label 'agent'
            }
            steps {
                script {
                    // Собрать Docker образ из Dockerfile, который находится в папке "app"
                    docker.build("${IMAGE_NAME}:${IMAGE_TAG}", "-f app/Dockerfile .")
                }
            }
        }
        
        stage('Deploy to Agent host for test') {
            when {
                expression { env.BRANCH == 'main' || env.BRANCH == 'dev' }
            }
            agent {
                label 'agent'
            }
            steps {
                // Запуск контейнера с использованием docker-compose
                sh 'docker compose -f ./app/docker-compose.yml up -d'
            }
        }
        
        stage('Test my app') {
            when {
                expression { env.BRANCH == 'main' || env.BRANCH == 'dev' }
            }
            agent {
                label 'agent'
            }
            steps {
                script {
                    // Проверка доступности страницы на localhost:8080
                    def retries = 10
                    def success = false
                    for (int i = 0; i < retries; i++) {
                        try {
                            sh 'curl -f http://localhost:8080'
                            success = true
                            break
                        } catch (Exception e) {
                            sleep 1
                        }
                    }
                    if (success) {
                        echo 'Проверка прошла успешно'
                    } else {
                        error 'Ошибка. Проверка неудачна.'
                    }
                }
            }
        }

        stage('Push to Docker Hub') {
            when {
                expression { env.BRANCH == 'main' || env.BRANCH == 'dev' }
            }
            agent {
                label 'agent'
            }
            steps {
                script {
                    // Получаем имя пользователя и пароль из глобальных учетных данных Jenkins
                    withCredentials([usernamePassword(credentialsId: 'docker-hub-credentials', usernameVariable: 'DOCKER_USERNAME', passwordVariable: 'DOCKER_PASSWORD')]) {
                        // Определяем команды Docker login и передаем учетные данные через stdin
                        sh "echo $DOCKER_PASSWORD | docker login -u $DOCKER_USERNAME --password-stdin"
                        // Тегирование и загрузка образа
                        sh "docker tag ${IMAGE_NAME}:${IMAGE_TAG} ${DOCKER_USERNAME}/${IMAGE_NAME}:${IMAGE_TAG}"
                        sh "docker tag ${IMAGE_NAME}:${IMAGE_TAG} ${DOCKER_USERNAME}/${IMAGE_NAME}:latest"
                        sh "docker push ${DOCKER_USERNAME}/${IMAGE_NAME}:${IMAGE_TAG}"
                        sh "docker push ${DOCKER_USERNAME}/${IMAGE_NAME}:latest"
                    }
                }
            }
        }

        stage('Cleanup from build and test') {
            when {
                expression { env.BRANCH == 'main' || env.BRANCH == 'dev' }
            }
            options {
                skipDefaultCheckout()
            }
            agent {
                label 'agent'
            }
            steps {
                // Остановка и удаление контейнера с использованием docker-compose
                sh 'docker compose -f ./app/docker-compose.yml down'
                // Удаление всех образов Docker
                sh 'docker rmi --force $(docker images -q)'
                // Удаление директории после сборки и тестов
                sh 'rm -rf ${WORKSPACE}/*'
            }
        }

        stage('Deploy to GKE stage') {
            when {
                expression { env.BRANCH == 'dev' }
            }
            agent {
                label 'agent'
            }
            steps {
                script {
                    withCredentials([file(credentialsId: 'google_k8s_serviceaccount', variable: 'GOOGLE_APPLICATION_CREDENTIALS')]) {
                        sh 'gcloud auth activate-service-account --key-file=$GOOGLE_APPLICATION_CREDENTIALS'
                        sh 'gcloud container clusters get-credentials diplom-gke-cluster --region europe-west3-a --project vps2033'
                        
                        // Читаем содержимое файла VERSION 
                        def version = readFile('VERSION').trim()
                         
                        // Обновляем значение тега в values.yaml
                        sh """
                            sed -i 's/tag: .*/tag: ${version}/' ./k8s-helm-diplom/values.yaml
                        """
                        
                        // Проверяем, установлен ли уже Helm релиз
                        def helmStatus = sh(script: 'helm status diplom-mysite-stage', returnStatus: true)
            
                        if (helmStatus == 0) {
                            // Если релиз уже установлен, удаляем его
                            sh 'helm uninstall diplom-mysite-stage'
                        }

                        sh 'helm install diplom-mysite-stage k8s-helm-diplom/ --set hostname=stage.hitmouz.com --set tlsSecretName=stage-hitmouz-com-tls'
                    }
                }
            }
        }

        stage('Deploy to GKE prod') {
            when {
                expression { env.BRANCH == 'main' }
            }
            agent {
                label 'agent'
            }
            steps {
                input(message: 'Please confirm Deploy Prod', ok: 'Proceed?')
                script {
                    withCredentials([file(credentialsId: 'google_k8s_serviceaccount', variable: 'GOOGLE_APPLICATION_CREDENTIALS')]) {
                        sh 'gcloud auth activate-service-account --key-file=$GOOGLE_APPLICATION_CREDENTIALS'
                        sh 'gcloud container clusters get-credentials diplom-gke-cluster --region europe-west3-a --project vps2033'
                        
                        // Читаем содержимое файла VERSION 
                        def version = readFile('VERSION').trim()
                         
                        // Обновляем значение тега в values.yaml
                        sh """
                            sed -i 's/tag: .*/tag: ${version}/' ./k8s-helm-diplom/values.yaml
                        """
                        
                        // Проверяем, установлен ли уже Helm релиз
                        def helmStatus = sh(script: 'helm status diplom-mysite-prod', returnStatus: true)
            
                        if (helmStatus == 0) {
                            // Если релиз уже установлен, удаляем его
                            sh 'helm uninstall diplom-mysite-prod'
                        }

                        sh 'helm install diplom-mysite-prod k8s-helm-diplom/'
                    }
                }
            }
        }
    }

    post {
        success {
            node('agent') {
                script {
                    def TEXT_SUCCESS_BUILD = "Build succeeded: ${env.JOB_NAME} (Build number - ${env.BUILD_NUMBER})"
                    echo 'Build succeeded! Deploying...'
                    sendTelegramNotification(TEXT_SUCCESS_BUILD)
                }
            }
        }

        failure {
            node('agent') {
                script {
                    def TEXT_FAILURE_BUILD = "Build failed: ${env.JOB_NAME} (Build number - ${env.BUILD_NUMBER})"
                    echo 'Build failed!'
                    sendTelegramNotification(TEXT_FAILURE_BUILD)
                }
            }
        }
    }
}

def sendTelegramNotification(String status) {
    withCredentials([string(credentialsId: TELEGRAM_BOT_TOKEN_ID, variable: 'TOKEN'), string(credentialsId: TELEGRAM_CHAT_ID_ID, variable: 'CHAT_ID')]) {
        def repoName = sh(returnStdout: true, script: 'git config --get remote.origin.url').trim().split('/')[-1].replace('.git', '')
        def message = """
Jenkins agent
        Repository: ${repoName}
        Branch: ${env.BRANCH}
        Author: ${env.AUTHOR} (${env.AUTHOR_TYPE})
        ${status}
"""
        sh """
            curl -s -X POST https://api.telegram.org/bot${TOKEN}/sendMessage -d chat_id=${CHAT_ID} -d text="${message}"
        """.stripIndent()
    }
}