node {
  // Mark the code checkout 'stage'....
  stage 'Checkout'

  // Checkout code from repository
  checkout([$class: 'GitSCM',
            branches: [
              [name: '*/*'],
            ],
            browser: [$class: 'GithubWeb', repoUrl: 'https://github.com/jenkinsci/git-plugin'],
            doGenerateSubmoduleConfigurations: false,
            extensions: [[$class: 'LocalBranch', localBranch: '**']],
            gitTool: 'Default',
            submoduleCfg: [],
            userRemoteConfigs: [[
                 url: 'git://github.com/jenkinsci/git-plugin.git',
               ]
             ],
           ]
          )

  // Mark the code build 'stage'....
  stage 'Build'
  // Run the maven build
  withEnv(["JAVA_HOME=${ tool 'oracle-java-7' }", "PATH+MAVEN=${tool 'maven-latest'}/bin:${env.JAVA_HOME}/bin"]) {
    // Apache Maven related side notes:
    // -B : batch mode (less logs)
    // -V : display the JDK and Maven versions (sanity check)
    // -U : update snapshots each build (rather than hourly)
    // -e : produce execution error messages (easier diagnosis)
    // -Dsurefire.useFile=false : Display test errors in the logs
    //                            directly (instead of having to crawl
    //                            the workspace files to see the
    //                            cause).
    // -Dmaven.test.failure.ignore=true : Display test errors in the
    //                            logs directly (instead of having to
    //                            crawl the workspace files to see the
    //                            cause).
    def switches = "-B -V -U -e -Dsurefire.useFile=false -Dmaven.test.failure.ignore=true"
    def parameters = ""
    if (isUnix()) {
      sh  "mvn ${switches} clean install ${parameters}"
    } else {
      bat "mvn ${switches} clean install ${parameters}"
    }
  }

  // Results stage - remember things about the build ....
  stage 'Results'
  step([$class: 'JUnitResultArchiver', testResults: '**/target/surefire-reports/TEST-*.xml'])
  step([$class: 'ArtifactArchiver', artifacts: '**/target/*.*pi', fingerprint: true])
}
