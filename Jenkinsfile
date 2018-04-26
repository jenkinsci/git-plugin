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
        deleteDir()
        stage("ATH: Checkout") {
            dir("git") {
                checkout scm
                sh "mvn clean package -DskipTests"
                dir("target") {
                    stash name: "localPlugins", includes: "*.hpi"
                }
                sh "mvn clean"
            }
        }
        def metadataPath = pwd() + "/git/essentials.yml"
        stage("Run ATH") {
            dir("ath") {
                runATH metadataFile: metadataPath
                deleteDir()
            }
        }
    }
}
branches["PCT"] = {
    node("docker && highmem") {
        deleteDir()
        env.RUN_PCT_LOCAL_PLUGIN_SOURCES_STASH_NAME = "localPluginsPCT"
        stage("PCT: Checkout") {
            dir("git") {
                checkout scm
            }
            stash name: "localPluginsPCT", useDefaultExcludes: false

        }
        def metadataPath = pwd() + "/git/essentials.yml"

        stage("Run PCT") {
            dir("pct") {
                runPCT metadataFile: metadataPath,
                        javaOptions: ["-Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn"]
                deleteDir()
            }
        }
    }
}

parallel branches