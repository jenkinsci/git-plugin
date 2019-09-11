#!groovy

// Test plugin compatibility to recommended configurations
// Allow failing tests to retry execution
subsetConfiguration = [ [ jdk: '8',  platform: 'windows', jenkins: null                      ],
                        [ jdk: '8',  platform: 'linux',   jenkins: '2.164.1', javaLevel: '8' ],
                        [ jdk: '11', platform: 'linux',   jenkins: '2.164.1', javaLevel: '8' ]
                      ]

buildPlugin(configurations: subsetConfiguration, failFast: false)

def branches = [:]

branches["ATH"] = {
    node("docker && highmem") {
        def checkoutGit
        stage("ATH: Checkout") {
            checkoutGit = pwd(tmp:true) + "/athgit"
            dir(checkoutGit) {
                checkout scm
                infra.runMaven(["clean", "package", "-DskipTests"])
                // Include experimental git-client in target dir for ATH
                // This Git plugin requires experimental git-client
                infra.runMaven(["dependency:copy", "-Dartifact=org.jenkins-ci.plugins:git-client:3.0.0-beta3:hpi", "-DoutputDirectory=target", "-Dmdep.stripVersion=true"])
                dir("target") {
                    stash name: "localPlugins", includes: "*.hpi"
                }
            }
        }
        def metadataPath = checkoutGit + "/essentials.yml"
        stage("Run ATH") {
            def athFolder=pwd(tmp:true) + "/ath"
            dir(athFolder) {
                runATH metadataFile: metadataPath
            }
        }
    }
}
branches["PCT"] = {
    node("docker && highmem") {
        def metadataPath
        env.RUN_PCT_LOCAL_PLUGIN_SOURCES_STASH_NAME = "localPluginsPCT"
        stage("PCT: Checkout") {
            def checkoutGit = pwd(tmp:true) + "/pctgit"
            dir(checkoutGit) {
                dir("git") {
                    checkout scm
                }
                stash name: "localPluginsPCT", useDefaultExcludes: false
            }
            metadataPath = checkoutGit + "/git/essentials.yml"
        }
        stage("Run PCT") {
            def pctFolder = pwd(tmp:true) + "/pct"
            dir(pctFolder) {
                runPCT metadataFile: metadataPath
            }
        }
    }
}

// Intentionally disabled until tests are more reliable
// parallel branches
