def maven() {
	echo "Git procedures have started."
	sh "git submodule deinit -f ."
	sh "git submodule update --init --remote --merge"
}