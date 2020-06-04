#!/usr/bin/env groovy

import java.util.Collections

// Clean the agents used for the build
// Remove when https://github.com/jenkins-infra/pipeline-library/pull/141 is merged
def temporary_clean(Map params = [:]) {
    def failFast = params.containsKey('failFast') ? params.failFast : true
    def timeoutValue = params.containsKey('timeout') ? params.timeout : 60
    def forceAci = params.containsKey('forceAci') ? params.forceAci : false
    def useAci = params.containsKey('useAci') ? params.useAci : forceAci
    if(timeoutValue > 180) {
      echo "Timeout value requested was $timeoutValue, lowering to 180 to avoid Jenkins project's resource abusive consumption"
      timeoutValue = 180
    }

    Map tasks = [failFast: failFast]
    getConfigurations(params).each { config ->
        String label = config.platform
        String jdk = config.jdk
        String jenkinsVersion = config.jenkins
        String javaLevel = config.javaLevel

        String stageIdentifier = "${label}-${jdk}${jenkinsVersion ? '-' + jenkinsVersion : ''}"
        boolean first = tasks.size() == 1
        boolean runFindbugs = first && params?.findbugs?.run
        boolean runCheckstyle = first && params?.checkstyle?.run
        boolean archiveFindbugs = first && params?.findbugs?.archive
        boolean archiveCheckstyle = first && params?.checkstyle?.archive
        boolean skipTests = params?.tests?.skip
        boolean addToolEnv = !useAci

        if(useAci && (label == 'linux' || label == 'windows')) {
            String aciLabel = jdk == '8' ? 'maven' : 'maven-11'
            if(label == 'windows') {
                aciLabel += "-windows"
            }
            label = aciLabel
        }

        tasks[stageIdentifier] = {
            node(label) {
                timeout(timeoutValue) {
                    stage("TmpClean (${stageIdentifier})") {
                        if (isUnix()) {
                            sh(script: 'git clean -xffd > /dev/null 2>&1',
                               label:'Clean for incrementals',
                               returnStatus: true) // Ignore failure if CLI git is not available or this is not a git repository
                        } else {
                            bat(script: 'git clean -xffd 1> nul 2>&1',
                                label:'Clean for incrementals',
                                returnStatus: true) // Ignore failure if CLI git is not available or this is not a git repository
                        }
                    }
                }
            }
        }
    }
    parallel(tasks)
}

List<Map<String, String>> getConfigurations(Map params) {
    boolean explicit = params.containsKey("configurations")
    boolean implicit = params.containsKey('platforms') || params.containsKey('jdkVersions') || params.containsKey('jenkinsVersions')

    if (explicit && implicit) {
        error '"configurations" option can not be used with either "platforms", "jdkVersions" or "jenkinsVersions"'
    }

    def configs = params.configurations
    configs.each { c ->
        if (!c.platform) {
            error("Configuration field \"platform\" must be specified: $c")
        }
        if (!c.jdk) {
            error("Configuration field \"jdk\" must be specified: $c")
        }
    }

    if (explicit) return params.configurations

    def platforms = params.containsKey('platforms') ? params.platforms : ['linux', 'windows']
    def jdkVersions = params.containsKey('jdkVersions') ? params.jdkVersions : [8]
    def jenkinsVersions = params.containsKey('jenkinsVersions') ? params.jenkinsVersions : [null]

    def ret = []
    for (p in platforms) {
        for (jdk in jdkVersions) {
            for (jenkins in jenkinsVersions) {
                ret << [
                        "platform": p,
                        "jdk": jdk,
                        "jenkins": jenkins,
                        "javaLevel": null   // not supported in the old format
                ]
            }
        }
    }
    return ret
}

// Valid Jenkins versions for test
def testJenkinsVersions = [ '2.204.1', '2.204.6', '2.222.1', '2.222.4', '2.235', '2.238' ]
Collections.shuffle(testJenkinsVersions)

// Test plugin compatibility to subset of Jenkins versions
subsetConfiguration = [ [ jdk: '8',  platform: 'windows', jenkins: testJenkinsVersions[0], javaLevel: '8' ],
                        [ jdk: '8',  platform: 'linux',   jenkins: testJenkinsVersions[1], javaLevel: '8' ],
                        [ jdk: '11', platform: 'linux',   jenkins: testJenkinsVersions[2], javaLevel: '8' ]
                      ]

// Clean before build so that `git status -s` will have empty output for incrementals
// Remove when https://github.com/jenkins-infra/pipeline-library/pull/141 is merged
temporary_clean(configurations: subsetConfiguration, failFast: false)

buildPlugin(configurations: subsetConfiguration, failFast: false)
