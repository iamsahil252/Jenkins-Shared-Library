def call() {

    def configFile = libraryResource('config/monitoring.conf')
    def config = [:]

    configFile.readLines().each { line ->
        line = line.trim()

        if (line && !line.startsWith('#')) {
            def parts = line.split('=', 2)
            if (parts.size() == 2) {
                config[parts[0].trim()] = parts[1].trim()
            }
        }
    }

    stage('Clone Ansible Repository') {
        sh """
            rm -rf '${config.CODE_BASE_PATH}'
            git clone -b '${config.ANSIBLE_BRANCH}' \
                '${config.ANSIBLE_REPO_URL}' \
                '${config.CODE_BASE_PATH}'
        """
    }

    if (config.KEEP_APPROVAL_STAGE.toBoolean()) {
        stage('User Approval') {
            input message: "Approve Grafana and Prometheus deployment?",
                  ok: "Deploy"
        }
    }

    try {
        stage('Ansible Deployment') {
            dir(config.CODE_BASE_PATH) {
                sh '''
                    ansible-playbook -i inventory site.yml
                '''
            }
        }

        stage('Success Notification') {
            slackSend(
                channel: config.SLACK_CHANNEL_NAME,
                message: "${config.ACTION_MESSAGE} | Environment: ${config.ENVIRONMENT}"
            )
        }

    } catch (Exception e) {

        stage('Failure Notification') {
            slackSend(
                channel: config.SLACK_CHANNEL_NAME,
                message: "Grafana and Prometheus deployment failed | Environment: ${config.ENVIRONMENT}"
            )
        }

        throw e
    }
}
