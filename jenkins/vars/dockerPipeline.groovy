import org.pavangajjala.ci.ImageTag

/**
 * Build, test, scan and publish a container image, then optionally promote it.
 *
 *   @Library('cicd-pipeline-library') _
 *   dockerPipeline(
 *       image: '111111111111.dkr.ecr.eu-west-1.amazonaws.com/orders',
 *       context: 'services/orders',
 *       testCommand: 'make test',
 *       registryCredentialsId: 'ecr:eu-west-1:jenkins-ecr')
 */
def call(Map config) {
    String image = config.image ?: error('dockerPipeline: image is required')
    String context = config.context ?: '.'
    String dockerfile = config.dockerfile ?: "${context}/Dockerfile"
    String failOn = config.failOnSeverity ?: 'CRITICAL,HIGH'
    String mainBranch = config.mainBranch ?: 'main'

    pipeline {
        agent { label config.agentLabel ?: 'docker' }

        options {
            timeout(time: 30, unit: 'MINUTES')
            disableConcurrentBuilds(abortPrevious: true)
            buildDiscarder(logRotator(numToKeepStr: '30'))
            timestamps()
        }

        environment {
            IMAGE = "${image}"
            IMAGE_TAG = "${ImageTag.forBuild(env.BRANCH_NAME, env.GIT_COMMIT, env.BUILD_NUMBER)}"
        }

        stages {
            stage('Test') {
                when { expression { config.testCommand } }
                steps {
                    sh(script: config.testCommand, label: 'Run tests')
                }
            }

            stage('Build image') {
                steps {
                    sh(label: 'docker build', script: """
                        docker build \\
                          --pull \\
                          --file '${dockerfile}' \\
                          --label org.opencontainers.image.revision="\$GIT_COMMIT" \\
                          --tag "\$IMAGE:\$IMAGE_TAG" \\
                          '${context}'
                    """)
                }
            }

            stage('Scan image') {
                steps {
                    // Fails the build before anything is pushed.
                    sh(label: 'trivy image', script: """
                        trivy image --exit-code 1 --ignore-unfixed \\
                          --severity '${failOn}' "\$IMAGE:\$IMAGE_TAG"
                    """)
                }
            }

            stage('Push image') {
                when { branch mainBranch }
                steps {
                    script {
                        docker.withRegistry("https://${image.tokenize('/')[0]}", config.registryCredentialsId) {
                            sh(label: 'docker push', script: 'docker push "$IMAGE:$IMAGE_TAG"')
                        }
                    }
                }
            }
        }

        post {
            success {
                echo "Built ${image}:${env.IMAGE_TAG}"
            }
            cleanup {
                sh(label: 'Remove local image', script: 'docker image rm "$IMAGE:$IMAGE_TAG" || true')
                cleanWs()
            }
        }
    }
}
