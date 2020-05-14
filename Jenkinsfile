#!groovy

Random random = new Random() // Randomize which Jenkins version is selected for more testing
def use_newer_jenkins = random.nextBoolean() // Use newer Jenkins on one build but slightly older on other

// Test plugin compatibility to recommended configurations
subsetConfiguration = [ [ jdk: '8',  platform: 'windows', jenkins: null                      ],
                        // Compile with Java 8, test 2.204.6 or 2.222.1 depending on random use_newer_jenkins
                        [ jdk: '8',  platform: 'linux',   jenkins: !use_newer_jenkins ? '2.204.6' : '2.222.1', javaLevel: '8' ],
                        // Compile with Java 11, test the Jenkins version that Java 8 did *not* test
                        [ jdk: '11', platform: 'linux',   jenkins:  use_newer_jenkins ? '2.204.6' : '2.222.1', javaLevel: '8' ]
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
