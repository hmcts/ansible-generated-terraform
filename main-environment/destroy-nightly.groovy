#!groovy
def channel = '#devops-builds'

properties(
  [[$class: 'GithubProjectProperty', projectUrlStr: 'https://github.com/hmcts/ansible-generated-terraform/']]
)

@Library('Reform') _

node {
  try {
    wrap([$class: 'AnsiColorBuildWrapper', colorMapName: 'xterm']) {

      stage('Checkout') {
        checkout scm
      }

      stage('Generate ansible output') {
        dir ('main-environment') {
          sh """
          molecule create
          molecule syntax
          molecule verify
          """
        }
        sh """
        ansible-playbook -i "127.0.0.1," --tags destroy_nightly -c local main-environment/playbook.yml
        sudo chown -R jenkins main-environment/ansible-generated
        """
      }

      withCredentials([
          [$class: 'StringBinding', credentialsId: 'IDAM_ARM_CLIENT_SECRET', variable: 'ARM_CLIENT_SECRET'],
          [$class: 'StringBinding', credentialsId: 'IDAM_ARM_CLIENT_ID', variable: 'ARM_CLIENT_ID'],
          [$class: 'StringBinding', credentialsId: 'IDAM_ARM_TENANT_ID', variable: 'ARM_TENANT_ID'],
          [$class: 'StringBinding', credentialsId: 'IDAM_ARM_SUBSCRIPTION_ID', variable: 'ARM_SUBSCRIPTION_ID']
      ]) {

        stage('TF destroy code') {
          dir ("main-environment/ansible-generated/uksouth/destroy-nightly") {
            sh '''
            /bin/bash destroy-nightly.sh
            '''
          }
        }

      }
    }

  } catch (err) {
    notifyBuildFailure channel: "${channel}"
    throw err
  } finally {
    stage('Cleanup') {
      dir ('main-environment') {
        sh '''
          sudo chown -R jenkins ansible-generated
          molecule destroy
        '''
      }
      deleteDir()
    }
  }

}
