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

        stage('Read commit_id for docker image tag - stage') {
            when {
                expression { env.BRANCH == 'dev' }
            }
            agent {
                label 'agent'
            }
            steps {
                script {
                    if (env.BRANCH == 'dev') {
                        env.COMMIT_ID = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
                        echo "Commit ID: ${env.COMMIT_ID}"
                        env.IMAGE_TAG = env.COMMIT_ID
                    }
                }
            }
        }

        stage('Read file Version for docker image tag - prod') {
            when {
                expression { env.BRANCH == 'main' }
            }
            agent {
                label 'agent'
            }
            steps {
                script {
                    // Read version from file Version
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
                    // Building a Docker image from a Dockerfile in the "app" directory
                    docker.build("${IMAGE_NAME}:${env.IMAGE_TAG}", "-f app/Dockerfile .")
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
                // Running a container using docker-compose
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
                        sh 'echo $DOCKER_PASSWORD | docker login -u $DOCKER_USERNAME --password-stdin'
                        // Пушим образ с тегом ${IMAGE_TAG}
                        image.push("${env.IMAGE_TAG}")
                        // Пушим образ с тегом latest
                        image.push("latest")
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
                // Stopping and removing the container using docker-compose
                sh 'docker compose -f ./app/docker-compose.yml down'
                // Removing all Docker images
                sh 'docker rmi --force $(docker images -q)'
                // Removing the directory after build and tests
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
                        
                        // Read the contents of the VERSION file
                        def version = readFile('VERSION').trim()
                         
                        // Update the tag value in the values-dev.yaml file
                        sh """
                            sed -i 's/tag: .*/tag: ${env.IMAGE_TAG}/' ./k8s-helm-diplom/values-stage.yaml
                        """

                        sh 'helm upgrade --install diplom-mysite-stage k8s-helm-diplom/ --values k8s-helm-diplom/values-stage.yaml'
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
                        
                        // Read the contents of the VERSION file
                        def version = readFile('VERSION').trim()
                         
                        // Update the tag value in the values-dev.yaml file
                        sh """
                            sed -i 's/tag: .*/tag: ${version}/' ./k8s-helm-diplom/values-prod.yaml
                        """

                        sh 'helm upgrade --install diplom-mysite-prod k8s-helm-diplom/ --values k8s-helm-diplom/values-prod.yaml'
                    }
                }
            }
        }
    }

    post {
        success {
            node('agent') {
                script {
                    def TEXT_SUCCESS_BUILD = "Build and deploy succeeded\n        Build: ${env.JOB_NAME} (Build number - ${env.BUILD_NUMBER})"
                    echo 'Build succeeded! Deploying...'
                    sendTelegramNotification(TEXT_SUCCESS_BUILD)
                }
            }
        }

        failure {
            node('agent') {
                script {
                    def TEXT_FAILURE_BUILD = "Build and deploy failed\n        Build: ${env.JOB_NAME} (Build number - ${env.BUILD_NUMBER})"
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