pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        skipDefaultCheckout(true)
    }

    environment {
        REGISTRY_HOST = 'lab.ssafy.com:5050'
        IMAGE_PREFIX = 'lab.ssafy.com:5050/s15-fintech-finance-sub1/s15p21a107'
        REGISTRY_CREDENTIALS = 'gitlab-registry'
        PRODUCTION_COMPOSE = 'infra/docker-compose.prod.yml'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    env.IMAGE_TAG = sh(
                        script: 'git rev-parse --short=12 HEAD',
                        returnStdout: true
                    ).trim()
                    echo "Building ${env.BRANCH_NAME} at ${env.IMAGE_TAG}"
                }
            }
        }

        stage('Test') {
            parallel {
                stage('Backend API') {
                    steps {
                        sh '''
                            docker run --rm \
                              --user "$(id -u):$(id -g)" \
                              -e HOME=/tmp \
                              -e GRADLE_USER_HOME=/tmp/gradle \
                              -v "$WORKSPACE:/workspace" \
                              -w /workspace/backend/api \
                              eclipse-temurin:21-jdk \
                              ./gradlew test --no-daemon
                        '''
                    }
                }

                stage('Frontend') {
                    steps {
                        sh '''
                            docker run --rm \
                              --user "$(id -u):$(id -g)" \
                              -e HOME=/tmp \
                              -e npm_config_cache=/tmp/npm \
                              -v "$WORKSPACE:/workspace" \
                              -w /workspace/frontend \
                              node:20-bookworm \
                              sh -c 'npm ci --no-audit --no-fund && npm run lint && npm run build'
                        '''
                    }
                }

                stage('AI') {
                    steps {
                        sh '''
                            docker run --rm \
                              --user "$(id -u):$(id -g)" \
                              -e HOME=/tmp \
                              -v "$WORKSPACE:/workspace" \
                              -w /workspace/ai \
                              python:3.12-slim \
                              sh -c 'python -m venv /tmp/venv && /tmp/venv/bin/pip install --disable-pip-version-check -r requirements.txt && /tmp/venv/bin/python -m pytest -q'
                        '''
                    }
                }

                stage('Ingest') {
                    steps {
                        sh '''
                            if ! find backend/ingest -name '*.go' -type f -print -quit | grep -q .; then
                                echo 'No Go source files yet; skipping ingest tests.'
                                exit 0
                            fi

                            docker run --rm \
                              --user "$(id -u):$(id -g)" \
                              -e HOME=/tmp \
                              -e GOMODCACHE=/tmp/go-mod \
                              -e GOPATH=/tmp/go \
                              -v "$WORKSPACE:/workspace" \
                              -w /workspace/backend/ingest \
                              golang:1.26-bookworm \
                              sh -c 'go test ./... && go vet ./...'
                        '''
                    }
                }
            }
        }

        stage('Build Docker Images') {
            when {
                branch 'develop'
            }
            steps {
                sh '''
                    set -eu

                    for dockerfile in \
                        backend/api/Dockerfile \
                        backend/ingest/Dockerfile \
                        ai/Dockerfile \
                        frontend/Dockerfile; do
                        if [ ! -f "$dockerfile" ]; then
                            echo "Required deployment file is missing: $dockerfile"
                            exit 1
                        fi
                    done

                    docker build \
                      -t "$IMAGE_PREFIX/backend-api:$IMAGE_TAG" \
                      -f backend/api/Dockerfile backend/api
                    docker build \
                      -t "$IMAGE_PREFIX/ingest:$IMAGE_TAG" \
                      -f backend/ingest/Dockerfile backend/ingest
                    docker build \
                      -t "$IMAGE_PREFIX/ai:$IMAGE_TAG" \
                      -f ai/Dockerfile ai
                    docker build \
                      -t "$IMAGE_PREFIX/frontend:$IMAGE_TAG" \
                      -f frontend/Dockerfile frontend
                '''
            }
        }

        stage('Push Docker Images') {
            when {
                branch 'develop'
            }
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: env.REGISTRY_CREDENTIALS,
                        usernameVariable: 'REGISTRY_USERNAME',
                        passwordVariable: 'REGISTRY_PASSWORD'
                    )
                ]) {
                    sh '''
                        set -eu
                        printf '%s' "$REGISTRY_PASSWORD" | docker login "$REGISTRY_HOST" \
                          --username "$REGISTRY_USERNAME" --password-stdin

                        docker push "$IMAGE_PREFIX/backend-api:$IMAGE_TAG"
                        docker push "$IMAGE_PREFIX/ingest:$IMAGE_TAG"
                        docker push "$IMAGE_PREFIX/ai:$IMAGE_TAG"
                        docker push "$IMAGE_PREFIX/frontend:$IMAGE_TAG"
                    '''
                }
            }
        }

        stage('Deploy to EC2') {
            when {
                branch 'develop'
            }
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: env.REGISTRY_CREDENTIALS,
                        usernameVariable: 'REGISTRY_USERNAME',
                        passwordVariable: 'REGISTRY_PASSWORD'
                    )
                ]) {
                    sh '''
                        set -eu

                        if [ ! -f "$PRODUCTION_COMPOSE" ]; then
                            echo "Required deployment file is missing: $PRODUCTION_COMPOSE"
                            exit 1
                        fi

                        printf '%s' "$REGISTRY_PASSWORD" | docker login "$REGISTRY_HOST" \
                          --username "$REGISTRY_USERNAME" --password-stdin

                        export IMAGE_TAG
                        docker compose -f "$PRODUCTION_COMPOSE" pull
                        docker compose -f "$PRODUCTION_COMPOSE" up -d --remove-orphans
                    '''
                }
            }
        }
    }

    post {
        always {
            sh 'docker image prune -f || true'
        }
        success {
            echo 'Pipeline completed successfully.'
        }
        failure {
            echo 'Pipeline failed. Check the stage log for the first failing command.'
        }
    }
}
