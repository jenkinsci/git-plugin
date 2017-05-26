#!groovy

// Don't test plugin compatibility - exceeds 1 hour timeout
// Allow failing tests to retry execution
// buildPlugin(failFast: false)

// Test plugin compatbility to latest Jenkins LTS
// Allow failing tests to retry execution
buildPlugin(jenkinsVersions: [null, '2.46.3'], failFast: false)
