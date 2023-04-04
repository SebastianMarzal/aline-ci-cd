def toEcs() {
	withCredentials([file(credentialsId: 'sm-compose-env', variable: 'COMPOSE_ENV_VARS'),
		[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: "sm-aws-credentials",
		accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
		def composeContents = libraryResource 'compose.yaml'
		writeFile file: "docker-compose.yml", text: composeContents
		sh "cat ${COMPOSE_ENV_VARS} | envsubst '${LOAD_BALANCER_DNS_NAME}' > .env"

		docker.withRegistry("https://${AWS_ACCOUNT_NUMBER}.dkr.ecr.${AWS_REGION}.amazonaws.com", "ecr:${AWS_REGION}:${AWS_ACCOUNT_NUMBER}") {
			sh 'docker context create ecs sm-ecs-context --from-env'
			sh 'docker context use sm-ecs-context'
			sh 'docker compose -p "sm-aline" up'
			sh 'docker context use default'
		}
	}
}

def toEks(String end) {
	withCredentials([file(credentialsId: 'sm-eks-env', variable: 'EKS_ENV_VARS'),
		            [$class: 'AmazonWebServicesCredentialsBinding', credentialsId: "sm-aws-credentials",
                    accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
		try {
			sh "eksctl utils write-kubeconfig --cluster=${CLUSTER_NAME}"
		} catch (Exception ex) {
			def clusterManifest = libraryResource 'eks/cluster.yaml'
			writeFile file: "cluster.yaml", text: clusterManifest
			sh '''envsubst < cluster.yaml | eksctl create cluster -f -'''

			sh "eksctl utils associate-iam-oidc-provider \
				--region ${AWS_REGION} \
				--cluster ${CLUSTER_NAME} \
				--approve"
			
            sh "eksctl create iamserviceaccount \
				--cluster=${CLUSTER_NAME} \
				--namespace=kube-system \
				--name=aws-load-balancer-controller \
				--attach-policy-arn=${LB_CONTROLLER_POLICY_ARN}\
				--override-existing-serviceaccounts \
				--approve"

			sh "helm repo add eks https://aws.github.io/eks-charts"
			sh "helm repo update"
			sh "helm install aws-load-balancer-controller eks/aws-load-balancer-controller \
				-n kube-system \
				--set clusterName=${CLUSTER_NAME} \
				--set serviceAccount.create=false \
				--set serviceAccount.name=aws-load-balancer-controller \
				--set region=${AWS_REGION} \
				--set vpcId=${VPC_ID} \
				--set image.repository=${LB_CONTROLLER_IMAGE_REPO}"
			
			sh "kubectl create namespace aline"

			sh "kubectl create secret generic db-username --from-literal=DB_USERNAME=${DB_USERNAME} --namespace=aline"
			sh "kubectl create secret generic db-password --from-literal=DB_PASSWORD=${DB_PASSWORD} --namespace=aline"
			sh "kubectl create secret generic db-host --from-literal=DB_HOST=${DB_HOST} --namespace=aline"
			sh "kubectl create secret generic db-port --from-literal=DB_PORT=${DB_PORT} --namespace=aline"
			sh "kubectl create secret generic db-name --from-literal=DB_NAME=${DB_NAME} --namespace=aline"
			sh "kubectl create secret generic encrypt-key --from-literal=ENCRYPT_SECRET_KEY=${ENCRYPT_SECRET_KEY} --namespace=aline"
			sh "kubectl create secret generic jwt-key --from-literal=JWT_SECRET_KEY=${JWT_SECRET_KEY} --namespace=aline"
			
			def servicesManifest = libraryResource 'eks/services.yaml'
			writeFile file: "services.yaml", text: servicesManifest
			sh '''envsubst < services.yaml | kubectl apply -f -'''
		} finally {
			def deployManifest = libraryResource 'eks/b-deployment.yaml'
			if (env.SERVICE == 'gateway') {
				deployManifest = libraryResource 'eks/g-deployment.yaml'
			} else if (end == 'front-end') {
				deployManifest = libraryResource 'eks/f-deployment.yaml'
			}
			writeFile file: "deployment.yaml", text: deployManifest
			sh '''envsubst < deployment.yaml | kubectl apply -f -'''
		}
	}
}