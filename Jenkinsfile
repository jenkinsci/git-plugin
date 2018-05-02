#!groovy

// Don't test plugin compatibility - exceeds 1 hour timeout
// Allow failing tests to retry execution
// buildPlugin(failFast: false)

// Test plugin compatibility to latest Jenkins LTS
// Allow failing tests to retry execution
buildPlugin(jenkinsVersions: [null, '2.60.1'],
            findbugs: [run: true, archive: true, unstableTotalAll: '0'],
            failFast: false)

def branches = [:]

branches["ATH"] = {
    node("docker && highmem") {
        def checkoutGit
        stage("ATH: Checkout") {
            checkoutGit = pwd(tmp:true) + "/athgit"
            dir(checkoutGit) {
                checkout scm
                infra.runMaven(["clean", "package", "-DskipTests"])
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

parallel branches