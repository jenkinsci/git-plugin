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
  /* see https://wiki.jenkins-ci.org/display/JENKINS/Parallel+Test+Executor+Plugin and demo on github
  /* Using arbitrary parallelism of 4 and "generateInclusions" feature added in v1.8. */
  def splits = splitTests parallelism: [$class: 'CountDrivenParallelism', size: 4], generateInclusions: true

  /* Create dictionary to hold set of parallel test executions. */
  def testGroups = [:]

  for (int i = 0; i < splits.size(); i++) {
    def split = splits[i]

    /* Loop over each record in splits to prepare the testGroups that we'll run in parallel. */
    /* Split records returned from splitTests contain { includes: boolean, list: List<String> }. */
    /*     includes = whether list specifies tests to include (true) or tests to exclude (false). */
    /*     list = list of tests for inclusion or exclusion. */
    /* The list of inclusions is constructed based on results gathered from */
    /* the previous successfully completed job. One addtional record will exclude */
    /* all known tests to run any tests not seen during the previous run.  */
    testGroups["split${i}"] = {  // example, "split3"
      node {
        checkout scm

        /* Clean each test node to start. */
        mvn 'clean -B -V -U -e'

        def mavenInstall = 'install -B -V -U -e -Dsurefire.useFile=false -Dmaven.test.failure.ignore=true'

        /* Write includesFile or excludesFile for tests.  Split record provided by splitTests. */
        /* Tell maven to read the appropriate file. */
        if (split.includes) {
          writeFile file: "target/parallel-test-inclusions.txt", text: split.list.join("\n")
          mavenInstall += " -Dsurefire.includesFile=target/parallel-test-inclusions.txt"
        } else {
          writeFile file: "target/parallel-test-exclusions.txt", text: split.list.join("\n")
          mavenInstall += " -Dsurefire.excludesFile=target/parallel-test-exclusions.txt"
        }

        /* Call the maven build with tests. */
        mvn mavenInstall

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
    if (isUnix()) {
      sh "${mvnHome}/bin/mvn ${args}"
    } else {
      bat "${mvnHome}\\bin\\mvn ${args}"
    }
  }
}
