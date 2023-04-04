def init() {
    sh "terraform init"
}

def plan() {
    sh "cp ${SM_TFVARS} terraform.tfvars"
    sh "terraform plan"
}

def lint() {
    sh "tflint --init"
    sh "tflint --module --recursive"
}

def test() {
    sh "go mod init modules"
    sh "go mod tidy"
    sh "go test -v -timeout 0"
}

def action(String action) {
    if (action == "destroy") {
        sh "terraform destroy -auto-approve"
    } else {
        withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', credentialsId: "sm-aws-credentials", accessKeyVariable: 'AWS_ACCESS_KEY_ID', secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
            try {
                sh "terraform apply -auto-approve"
            } catch (Exception e) {
                sh "aws eks update-kubeconfig --name ${cluster_name}"
                sh "terraform apply -auto-approve"
            }
        }
    }
}