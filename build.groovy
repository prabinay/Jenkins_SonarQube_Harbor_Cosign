
pipeline {

	agent any

	environment {
	}

	options {
		buildDiscarder(logRotator(artifactNumToKeepStr: '15', numToKeepStr: '15'))
	 }

	stages {
		stage('Git Clone') {
			steps {
			    git branch: "$GIT_BRANCH", changelog: false, credentialsId: "$GIT_CREDEITIALS_ID", poll: false, url: "$GIT_URL"
			}
		}

 		stage('SonarQube Analysis') {
 			steps {
     	    	withSonarQubeEnv(credentialsId: 'sonar', installationName: 'sonarqube') {
       				sh "./mvnw clean verify sonar:sonar -Dsonar.projectKey=$SONAR_PROJECT_KEY"
     			}
 			}
 		}

		stage('Maven Build') {
			steps {
				sh 'maven clean install'
			}
		}

		stage('Docker Build') {
			steps {
			    sh 'docker build -t <tag> .'
			}
		}

		stage('Cosign') {
					    steps {
		        sh 'echo -n $COSIGN_PASSWORD | cosign sign --key $COSIGN_PRIVATE_KEY $TAGGED_DOCKER_IMAGE'
		    }  
		}

	}

	post {
		always {
			cleanWs()
		}
	}
}


pipeline {

	agent any
	tools {
        maven 'Maven_3.9.0'
        dockerTool 'Docker_latest'
    }
    environment {
	    GIT_URL = "" //add git url
	    GIT_BRANCH = "master"  //add git branch name
	    SONAR_PROJECT_KEY = "" //add project key
	    SONAR_PROJECT_NAME = ""  //add project folder name
	    SONAR_USER_TOKEN = "" //add user token
	}
	stages {
		stage('Git Clone') {
			steps {
			    git branch: "$GIT_BRANCH", changelog: false, poll: false, url: "$GIT_URL"
			}
		}
		
        stage('Maven Build') {
            steps {
                sh 'mvn clean install'
            }
        }
        stage('Maven Test') {
            steps {
                sh 'mvn test'
            }
        }
        // stage('Docker Build'){
        //     steps {
        //         sh 'docker build -t java-maven:1.0 .'
        //     }
        // }
        stage('SonarQube Analysis') {
 			steps {
     	    	withSonarQubeEnv(installationName: 'sonarqube') {
       				sh 'mvn clean verify sonar:sonar -Dsonar.projectKey=$SONAR_PROJECT_KEY -Dsonar.projectName=$SONAR_PROJECT_KEY -Dsonar.token=$SONAR_USER_TOKEN'
     			}
 			}
 		}
	}
}
