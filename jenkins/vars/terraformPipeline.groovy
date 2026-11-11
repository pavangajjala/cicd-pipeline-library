import org.pavangajjala.ci.Environments

/**
 * Parameterised Terraform pipeline for a monorepo with one root per stack
 * and one tfvars + backend config per environment.
 *
 *   @Library('cicd-pipeline-library') _
 *   terraformPipeline(
 *       stacks: ['network', 'eks', 'data'],
 *       environments: ['dev', 'staging', 'prod'],
 *       awsCredentialsId: 'aws-deploy')
 *
 * Expected layout:
 *   stacks/<stack>/                      root configuration
 *   stacks/<stack>/envs/<env>.tfvars     variables
 *   stacks/<stack>/backend/<env>.hcl     backend config
 */
def call(Map config) {
    List stacks = config.stacks ?: error('terraformPipeline: stacks is required')
    List environments = config.environments ?: ['dev', 'staging', 'prod']
    String root = config.stacksDir ?: 'stacks'

    pipeline {
        agent { label config.agentLabel ?: 'terraform' }

        parameters {
            choice(name: 'STACK', choices: stacks, description: 'Which stack to run')
            choice(name: 'ENVIRONMENT', choices: environments, description: 'Target environment')
            choice(name: 'ACTION', choices: ['plan', 'apply'], description: 'plan only shows changes; apply runs them after approval')
        }

        options {
            timeout(time: 60, unit: 'MINUTES')
            // Two runs against the same state would fight over the lock.
            lock(resource: "terraform-${params.STACK}-${params.ENVIRONMENT}")
            buildDiscarder(logRotator(numToKeepStr: '50'))
            timestamps()
            ansiColor('xterm')
        }

        environment {
            TF_IN_AUTOMATION = 'true'
            TF_INPUT = '0'
            STACK_DIR = "${root}/${params.STACK}"
        }

        stages {
            stage('Init') {
                steps {
                    withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: config.awsCredentialsId]]) {
                        dir(env.STACK_DIR) {
                            sh(label: 'terraform init', script: 'terraform init -reconfigure -backend-config="backend/${ENVIRONMENT}.hcl"')
                            sh(label: 'terraform validate', script: 'terraform fmt -check -recursive && terraform validate')
                        }
                    }
                }
            }

            stage('Plan') {
                steps {
                    withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: config.awsCredentialsId]]) {
                        dir(env.STACK_DIR) {
                            sh(label: 'terraform plan', script: 'terraform plan -var-file="envs/${ENVIRONMENT}.tfvars" -out=tfplan')
                            sh(label: 'Save readable plan', script: 'terraform show -no-color tfplan > tfplan.txt')
                        }
                    }
                    archiveArtifacts(artifacts: "${env.STACK_DIR}/tfplan.txt", fingerprint: true)
                }
            }

            stage('Approve') {
                when {
                    allOf {
                        expression { params.ACTION == 'apply' }
                        expression { Environments.isProtected(params.ENVIRONMENT) }
                    }
                }
                steps {
                    // The approver sees the archived plan; the agent is not held while waiting.
                    timeout(time: 2, unit: 'HOURS') {
                        input(
                            message: "Apply ${params.STACK} to ${params.ENVIRONMENT}?",
                            ok: 'Apply',
                            submitter: config.approvers ?: 'platform-team'
                        )
                    }
                }
            }

            stage('Apply') {
                when { expression { params.ACTION == 'apply' } }
                steps {
                    withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: config.awsCredentialsId]]) {
                        dir(env.STACK_DIR) {
                            // The saved plan, not a fresh one: what was approved is what runs.
                            sh(label: 'terraform apply', script: 'terraform apply tfplan')
                        }
                    }
                }
            }
        }

        post {
            failure {
                echo "Terraform ${params.ACTION} failed for ${params.STACK}/${params.ENVIRONMENT}"
            }
            cleanup {
                cleanWs()
            }
        }
    }
}
