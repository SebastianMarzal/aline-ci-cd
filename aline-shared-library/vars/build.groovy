def maven() {
    echo "Building Maven package."
    sh "mvn package -DskipTests=true"
}

def node() {
    sh "sudo npm install -g npm@9.2.0"
    sh "sudo npm install --legacy-peer-deps"
    sh "sudo npm run build"
}