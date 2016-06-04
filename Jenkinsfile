#!groovy

/* Only keep the 10 most recent builds. */
properties([[$class: 'BuildDiscarderProperty',
                strategy: [$class: 'LogRotator', numToKeepStr: '10']]])

node {
  stage 'Checkout'
  checkout scm

  stage 'Build'

  /* Call the maven build. */
  mvn "clean install -B"

  /* Save Results. */
  stage 'Results'

  /* Archive the test results */
  step([$class: 'JUnitResultArchiver', testResults: '**/target/surefire-reports/TEST-*.xml'])

  /* Archive the build artifacts */
  step([$class: 'ArtifactArchiver', artifacts: 'target/*.hpi,target/*.jpi'])
}

/* Run maven from tool "mvn" */
void mvn(def args) {
    /* Get jdk tool. */
    String jdktool = tool name: "jdk8", type: 'hudson.model.JDK'

    /* Get the maven tool. */
    def mvnHome = tool name: 'mvn'

    /* Set JAVA_HOME, and special PATH variables. */
    List javaEnv = ["PATH+JDK=${jdktool}/bin", "JAVA_HOME=${jdktool}"]

    /* Call maven tool with java envVars. */
    withEnv(javaEnv) {
      sh "${mvnHome}/bin/mvn ${args}"
    }
}
