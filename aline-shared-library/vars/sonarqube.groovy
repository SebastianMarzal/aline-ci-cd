def maven() {
    withSonarQubeEnv(installationName: "sonarqube") {
        sh "mvn clean verify sonar:sonar"
    }
}

def node() {
    def scannerHome = tool name: "SonarScanner", type: "hudson.plugins.sonar.SonarRunnerInstallation";
    withSonarQubeEnv(installationName: "sonarqube") {
        sh "${scannerHome}/bin/sonar-scanner"
    }
}

def qualityGate() {
    timeout(1) {
        def qg = waitForQualityGate()
        if (qg.status != "OK") {
            error "Pipeline aborted due to quality gate failure: ${qg.status}"
        }
    }
}