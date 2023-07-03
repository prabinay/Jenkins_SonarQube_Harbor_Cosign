pipeline {
    agent any
     
    
    
    tools {
        maven 'maven'
        dockerTool  'Docker'
    }
    
    environment {
	    GIT_URL = "https://github.com/prabinay/simple-java-maven-app.git"
	    GIT_BRANCH = "master"
	    SONAR_PROJECT_KEY = "Java-maven-app"
	    SONAR_PROJECT_NAME = "Java-maven-app"
	    SONAR_USER_TOKEN = "squ_b07cd315932c6cfafa7e0a7aa87b1528a98ada2a"
	    
	    DOCKER_IMAGE_NAME = "java-maven"
	    
	    HARBOR_PROJECT_NAME = "java-maven-app"
	    
	    HARBOR_CREDENTIALS = credentials('harbor-creds')
        HARBOR_USERNAME = "${HARBOR_CREDENTIALS_USR}"
        HARBOR_PASSWORD = "${HARBOR_CREDENTIALS_PSW}"
        HARBOR_URL = "demo.goharbor.io"
        
        COSIGN_PASSWORD= credentials('cosign-cred')
        COSIGN_PRIVATE_KEY = credentials('cosign-key')

    
	}
    
    stages {
        stage('Git Clone') {
			steps {
			    git branch: "$GIT_BRANCH", changelog: false,  poll: false, url: "$GIT_URL"
			}
		}
		
		stage('Maven Build') {
			steps {
			     sh 'mvn clean install'
			}  
        }
		
		stage('SonarQube Analysis') {
 			steps {
     	    	withSonarQubeEnv(installationName: 'Sonarqube') {
      				sh 'mvn clean verify sonar:sonar -Dsonar.projectKey=$SONAR_PROJECT_KEY -Dsonar.projectName=$SONAR_PROJECT_KEY -Dsonar.token=$SONAR_USER_TOKEN'
     			}
 			}
 		}
 		
 		stage('Docker Build') {
        	steps {
			    sh 'docker build -t $DOCKER_IMAGE_NAME .'
			}
		}
		
		stage('push to Harbor') {
            steps {
                sh 'docker login -u $HARBOR_USERNAME -p $HARBOR_PASSWORD $HARBOR_URL'
		        
		        sh 'docker tag $DOCKER_IMAGE_NAME $HARBOR_URL/$HARBOR_PROJECT_NAME/$DOCKER_IMAGE_NAME:1.0'
		        sh 'docker push $HARBOR_URL/$HARBOR_PROJECT_NAME/$DOCKER_IMAGE_NAME:1.0'
            }
        }
        
        stage('Cosign') {
			steps {
				// sh '/usr/local/bin/cosign version'
				// sh 'echo -n $COSIGN_PASSWORD | cosign sign --key $COSIGN_PRIVATE_KEY $DOCKER_IMAGE_NAME:1.0'
	
		        sh 'echo -n $COSIGN_PASSWORD | cosign sign --key $COSIGN_PRIVATE_KEY $HARBOR_URL/$HARBOR_PROJECT_NAME/$DOCKER_IMAGE_NAME:1.0'
		    }  
		}
    }
    
}


// mount the trivy to docker and create container
docker run -d -p 8080:8080 -p 50000:50000 -v jenkins_home:/var/jenkins_home -v /var/run/docker.sock:/var/run/docker.sock -v /usr/local/bin/trivy:/usr/local/bin/trivy jenkins/jenkins:lts