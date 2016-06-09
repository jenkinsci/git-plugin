#!groovy

/* Only keep the 10 most recent builds. */
properties([[$class: 'BuildDiscarderProperty',
                strategy: [$class: 'LogRotator', numToKeepStr: '10']]])

node {
  stage 'Checkout'
  checkout scm

  stage 'Build'

  /* Call the maven build.  No tests./ */
  mvn "clean install -B -V -U -e -DskipTests"

  stage 'Test'

  /* Run tests in parallel on multiple nodes */
  runParallelTests()

  /* Save Results. */
  stage 'Results'

  /* Archive the build artifacts */
  step([$class: 'ArtifactArchiver', artifacts: 'target/*.hpi,target/*.jpi'])
}

void runParallelTests() {
  /* Request the test groupings.  Based on previous test exection. */
  def splits = splitTests parallelism: [$class: 'CountDrivenParallelism', size: 4], generateInclusions: true

  def testGroups = [:]
  for (int i = 0; i < splits.size(); i++) {
    def split = splits[i]

    testGroups["split${i}"] = {
      node {
        checkout scm

        sh 'mvn clean -B -V -U -e'

        def mavenInstall = 'mvn install -B -V -U -e -Dsurefire.useFile=false -Dmaven.test.failure.ignore=true'

        /* Write include or exclude file for maven tests.  Contents provided by splitTests. */
        if (split.includes) {
          writeFile file: "target/parallel-test-inclusions.txt", text: split.list.join("\n")
          mavenInstall += " -Dsurefire.includesFile=target/parallel-test-inclusions.txt"
        } else {
          writeFile file: "target/parallel-test-exclusions.txt", text: split.list.join("\n")
          mavenInstall += " -Dsurefire.excludesFile=target/parallel-test-exclusions.txt"
        }

        /* Call the maven build with tests. */
        sh mavenInstall

        /* Archive the test results */
        step([$class: 'JUnitResultArchiver', testResults: '**/target/surefire-reports/TEST-*.xml'])
      }
    }
  }
  parallel testGroups
}
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
