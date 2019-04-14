def imageName = "summarybox"
def podName = "slave-${imageName}-${env.BUILD_NUMBER}"

pipeline {
  agent {
    kubernetes {
      label "${podName}"
      yaml """
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: docker
    image: docker:18.09.3
    command: ['cat']
    tty: true
    volumeMounts:
    - name: dockersock
      mountPath: /var/run/docker.sock
  volumes:
  - name: dockersock
    hostPath:
      path: /var/run/docker.sock
"""
    }
  }

  triggers {
    pollSCM('H/5 * * * *')
  }

  environment {
    REGISTRY_URL = credentials('registry-url')
    REGISTRY_USER = credentials('registry-user')
    REGISTRY_PASS = credentials('registry-password')
    TAG = readFile("version.txt").trim()
    FULL_TAG = "${REGISTRY_URL}/${imageName}:${TAG}"
  }

  stages {
    stage('Build container') {
      steps {
        container('docker') {
          sh "docker build -t ${env.FULL_TAG} ."
        }
      }
    }

    stage('Push container') {
      steps {
        container('docker') {
          sh "docker login ${env.REGISTRY_URL} -u ${env.REGISTRY_USER} -p ${env.REGISTRY_PASS}"
          sh "docker push ${env.FULL_TAG}"
        }
      }
    }
  }
}
