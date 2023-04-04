def toDockerHub () {
    sh "docker context use default"
    echo "Pushing image to DockerHub."
    def buildImage = docker.build("sebastianom/${SERVICE}", ".")
    docker.withRegistry("", "docker_credentials") {
        buildImage.push()
    }
}

def toEcr() {
	withCredentials([[$class: "AmazonWebServicesCredentialsBinding", credentialsId: "sm-aws-credentials",
    accessKeyVariable: "AWS_ACCESS_KEY_ID", secretKeyVariable: "AWS_SECRET_ACCESS_KEY"]]) {
		sh "docker context use default"
		echo "Pushing the image to ECR."
		docker.withRegistry("https://${AWS_ACCOUNT_NUMBER}.dkr.ecr.${AWS_REGION}.amazonaws.com", "ecr:${AWS_REGION}:sm-aws-credentials") {
			def buildImage = docker.build("sm-${SERVICE}")
			buildImage.push("latest")
		}
	}
}