#!groovy

/* Only keep the 10 most recent builds. */
properties([[$class: 'BuildDiscarderProperty',
                strategy: [$class: 'LogRotator', numToKeepStr: '10']]])

node("docker") {
  stage 'Checkout'
  checkout scm

  stage 'Build'

  /* Call the maven build. */
  mvn "clean install -B -V -U -e -Dsurefire.useFile=false -Dmaven.test.failure.ignore=true"

  stage 'ATH'
  runAthForPlugin("git","2.7.4","*Git*")

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

void runAthForPlugin(String plugin, String coreVersion, String testPattern) {
  dir ('acceptance-test-harness') {
    git 'git://github.com/recampbell/acceptance-test-harness.git'
    pluginOverride = "${plugin}.jpi=../target/${plugin}.hpi"
    docker.image("selenium/standalone-firefox").withRun("--net=host -d -p 4444:4444") { c-> 
      withEnv(["JENKINS_VERSION=$coreVersion",pluginOverride,"ONLY_FOR_PLUGINS=${plugin}",
               "BROWSER=remote-webdriver-firefox", "JENKINS_LOCAL_HOSTNAME=${env.DOCKER_HOST_IP?:'localhost'}",
               "REMOTE_WEBDRIVER_URL=http://${env.DOCKER_CONTAINER_IP?:'localhost'}:4444/wd/hub"]){
        mvn "clean test -Dtest=$testPattern -Dmaven.test.failure.ignore=true"
      }
    }
    archiveArtifacts artifacts: 'target/diagnostics/**'
  }
}
