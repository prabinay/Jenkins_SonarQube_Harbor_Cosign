pipeline {

    agent any
    environment {
        REGISTRY_URL = credentials('')
        REGISTRY_USER = credentials('')
        REGISTRY_PASS = credentials('')
        
        IMAGE_URL = ''
        IMAGE_URL = ''
        
        API_URL = ''
        API_URL = ''
        
        API_DOCS_URL = ''
        API_DOCS_URL = ''
    }
    
    stages {
        
        stage('Trivy Scan') {
            steps {
                withEnv(["REGISTRY=demo.goharbor.io", "REG_USER=$REGISTRY_USER"]) {
                    sh 'docker login ${REGISTRY} -u ${REG_USER} -p ${REGISTRY_PASS}'
                    sh "trivy image --format template --template \'@/var/jenkins_home/html.tpl' -o ${JOB_NAME}-trivy_report.html ${IMAGE_URL}:"+buildNumber
                    sh "trivy image -f json -o ${JOB_NAME}-trivy_report.json ${IMAGE_URL}:"+buildNumber
                }
            }
        }
    

        stage('Dastardly Scan ') {
            steps {
                sh 'docker pull public.ecr.aws/portswigger/dastardly:latest'
                sh  '''
                    docker run --user $(id -u) -v /var/data/jenkins_home/dastardly:${WORKSPACE}:rw \
                    -e DASTARDLY_TARGET_URL=$API_URL_BFIADMIN \
                    -e DASTARDLY_OUTPUT_FILE=${WORKSPACE}/dastardly_report.xml \
                    public.ecr.aws/portswigger/dastardly:latest
                    '''
                sh 'cp /var/jenkins_home/dastardly/dastardly_report.xml $JOB_NAME-bfiadmin-dastardly_report.xml'    
            }
        }
        
        stage('ZAP API Scan') {
            steps {
                script {
                    try {
                        sh 'docker pull owasp/zap2docker-weekly'
                        sh '''
                            docker run --user $(id -u):$(id -g) -v /var/data/jenkins_home/zap:/zap/wrk/:rw owasp/zap2docker-weekly zap-api-scan.py -t $API_DOCS_URL -f openapi -J $JOB_NAME-zap_report.json
                            '''
                    }catch(err) {
                        echo "Something failed"
                    }
                sh "cp /var/jenkins_home/zap/${JOB_NAME}-zap_report.json ${JOB_NAME}-zap_report.json"
                }
            }   
        } 
    }
    
    post {
    success {
      
        archiveArtifacts artifacts: '*.json, *.html, *.xml',
        onlyIfSuccessful: false,
        fingerprint: true

        publishHTML (target: [
            allowMissing: false,
            alwaysLinkToLastBuild: false,
            keepAll: true,
            reportDir: '.',
            reportFiles: '*-trivy_report.html',
            reportName: 'Trivy Scan',
            reportTitles: 'Trivy Scan'
            ])
            
        publishHTML (target: [
            allowMissing: false,
            alwaysLinkToLastBuild: false,
            keepAll: true,
            reportDir: '.',
            reportFiles: '*-zap_report.html',
            reportName: 'ZAP API Scan',
            reportTitles: 'ZAP Scan'
            ])
                
        junit testResults: '*.xml', skipPublishingChecks: false
        cleanWs()
    }
    failure {
        cleanWs()
    }
  }
}



pipeline{
    agent any
    tools {
        dockerTool 'Docker_latest'
    }
    environment {
        DOCKER_IMAGE_NAME = "java-maven"
        IMG_TAG = "1.1"
      
        HARBOR_CREDENTIALS = credentials('demo_goharbor')
        HARBOR_USERNAME = "${HARBOR_CREDENTIALS_USR}"
        HARBOR_PASSWORD = "${HARBOR_CREDENTIALS_PSW}"
        HARBOR_URL = "demo.goharbor.io"
        
        COSIGN_PUBLIC_KEY = credentials('cosign-public-key')

    }
    
    stages {
        stage('Docker Login'){
            steps {
                sh 'docker login -u $HARBOR_USERNAME -p $HARBOR_PASSWORD $HARBOR_URL'
            }
        }
        
        // stage('Image Verify by Cosign'){
        //     steps {
        //         // sh 'cosign verify demo.goharbor.io/sudip-java-maven/sudip-java-maven:1.1'
        //         // sh 'cosign fetch demo.goharbor.io/sudip-java-maven/sudip-java-maven:1.1'
        //         sh 'cosign verify --key $COSIGN_PUBLIC_KEY $HARBOR_URL/$DOCKER_IMAGE_NAME/$DOCKER_IMAGE_NAME:$IMG_TAG'
        //     }
        // }
        
        
    }
}


pipeline{
    agent any
    tools {
        dockerTool 'Docker_latest'
    }
    environment {
        DOCKER_IMAGE_NAME = "sudip-java-maven"
        IMG_TAG = "1.1"
        
        HARBOR_CREDENTIALS = credentials('demo_goharbor')
        HARBOR_USERNAME = "${HARBOR_CREDENTIALS_USR}"
        HARBOR_PASSWORD = "${HARBOR_CREDENTIALS_PSW}"
        HARBOR_URL = "demo.goharbor.io"
        
        COSIGN_PUBLIC_KEY = credentials('cosign-public-key')
        
        JOB_NAME = "devsecops"

    }
    
    stages {
        stage('Docker Login'){
            steps {
                sh 'docker login -u $HARBOR_USERNAME -p $HARBOR_PASSWORD $HARBOR_URL'
            }
        }
        
        // stage('Image Verify by Cosign'){
        //     steps {
        //         // sh 'cosign verify demo.goharbor.io/sudip-java-maven/sudip-java-maven:1.1'
        //         // sh 'cosign fetch demo.goharbor.io/sudip-java-maven/sudip-java-maven:1.1'
        //         sh 'cosign verify --key $COSIGN_PUBLIC_KEY $HARBOR_URL/$DOCKER_IMAGE_NAME/$DOCKER_IMAGE_NAME:$IMG_TAG'
        //     }
        // }
        
        stage('Trivy Scan') {
            steps {
                // sh "trivy image $HARBOR_URL/$DOCKER_IMAGE_NAME/$DOCKER_IMAGE_NAME:$IMG_TAG "
                sh "trivy image --format template --template \'@/var/jenkins_home/html.tpl' -o ${JOB_NAME}-trivy_report.html $HARBOR_URL/$DOCKER_IMAGE_NAME/$DOCKER_IMAGE_NAME:$IMG_TAG "
                sh "trivy image -f json -o ${JOB_NAME}-trivy_report.json $HARBOR_URL/$DOCKER_IMAGE_NAME/$DOCKER_IMAGE_NAME:$IMG_TAG "
            }
        }
        stage ('Docker Logout') {
            steps {
                sh 'docker logout $HARBOR_URL'
            }
        }
    }
    
    post {
        success {
            archiveArtifacts artifacts: '*.json, *.html, *.xml',
            onlyIfSuccessful: false,
            fingerprint: true
            
            publishHTML (target: [
            allowMissing: false,
            alwaysLinkToLastBuild: false,
            keepAll: true,
            reportDir: '.',
            reportFiles: '*-trivy_report.html',
            reportName: 'Trivy Scan',
            reportTitles: 'Trivy Scan'
            ])
            
            cleanWS()
        }
        failure {
            cleanWS()
        }
    }
}



pipeline{
    agent any
    tools {
        dockerTool 'Docker'
    }
    environment {
        DOCKER_IMAGE_NAME = "java-maven"
        IMG_TAG = "1.0"
      
        HARBOR_CREDENTIALS = credentials('harbor-creds')
        HARBOR_USERNAME = "${HARBOR_CREDENTIALS_USR}"
        HARBOR_PASSWORD = "${HARBOR_CREDENTIALS_PSW}"
        HARBOR_URL = "demo.goharbor.io"
        
        COSIGN_PUBLIC_KEY = credentials('cosign-public-key')

        JOB_NAME = "devsecops"


    }
    
    stages {
        stage('Docker Login'){
            steps {
                sh 'docker login -u $HARBOR_USERNAME -p $HARBOR_PASSWORD $HARBOR_URL'
            }
        }

        stage('Trivy Scan') {
            steps {
                // sh "trivy image $HARBOR_URL/$DOCKER_IMAGE_NAME/$DOCKER_IMAGE_NAME:$IMG_TAG "
                sh "trivy image --format template --template \'@/var/jenkins_home/html.tpl' -o ${JOB_NAME}-trivy_report.html $HARBOR_URL/$DOCKER_IMAGE_NAME/$DOCKER_IMAGE_NAME:$IMG_TAG "
                sh "trivy image -f json -o ${JOB_NAME}-trivy_report.json $HARBOR_URL/$DOCKER_IMAGE_NAME/$DOCKER_IMAGE_NAME:$IMG_TAG "
            }
        }


        stage ('Docker Logout') {
            steps {
                sh 'docker logout $HARBOR_URL'
            }
        }
        
        
        
        
    }

    post {
        success {
            archiveArtifacts artifacts: '*.json, *.html, *.xml',
            onlyIfSuccessful: false,
            fingerprint: true
            
            publishHTML (target: [
            allowMissing: false,
            alwaysLinkToLastBuild: false,
            keepAll: true,
            reportDir: '.',
            reportFiles: '*-trivy_report.html',
            reportName: 'Trivy Scan',
            reportTitles: 'Trivy Scan'
            ])
            
            cleanWS()
        }
        failure {
            cleanWS()
        }
    }
}