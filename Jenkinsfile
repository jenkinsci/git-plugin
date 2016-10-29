#!/usr/bin/env groovy

/* Only keep the 10 most recent builds. */
properties([[$class: 'BuildDiscarderProperty',
                strategy: [$class: 'LogRotator', numToKeepStr: '10']]])

List labels = ['linux', 'windows']
Map platforms = [:]

for (int i = 0; i < labels.size(); ++i) {
    String label = labels[i]
    platforms[label] = {
        node(label) {
            stage("Checkout ${label}") {
                checkout scm
            }

            stage("Build ${label}") {
                /* Call the maven build. */
                mvn "clean install -B -V -U -e -Dsurefire.useFile=false -Dmaven.test.failure.ignore=true"
            }

            /* Save Results. */
            stage("Results ${label}") {
                /* Archive the test results */
                junit '**/target/surefire-reports/TEST-*.xml'

                /* Archive the build artifacts */
                archiveArtifacts artifacts: 'target/*.hpi,target/*.jpi'
            }
        }
    }
}

parallel(platforms)

/* Run maven from tool "mvn" */
void mvn(def args) {
  /* Get jdk tool. */
  String jdktool = tool name: "jdk7", type: 'hudson.model.JDK'

  /* Get the maven tool. */
  def mvnHome = tool name: 'mvn'

  /* Set JAVA_HOME, and special PATH variables. */
  List javaEnv = [
    "PATH+JDK=${jdktool}/bin", "JAVA_HOME=${jdktool}",
    // Additional variables needed by tests on machines
    // that don't have global git user.name and user.email configured.
    'GIT_COMMITTER_EMAIL=me@hatescake.com','GIT_COMMITTER_NAME=Hates','GIT_AUTHOR_NAME=Cake','GIT_AUTHOR_EMAIL=hates@cake.com', 'LOGNAME=hatescake'
  ]

  /* Call maven tool with java envVars. */
  withEnv(javaEnv) {
    timeout(time: 60, unit: 'MINUTES') {
      if (isUnix()) {
        sh "${mvnHome}/bin/mvn ${args}"
      } else {
        bat "${mvnHome}\\bin\\mvn ${args}"
      }
    }
  }
}
