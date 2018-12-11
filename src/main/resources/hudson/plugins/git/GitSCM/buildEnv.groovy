package hudson.plugins.git.GitSCM;

def l = namespace(lib.JenkinsTagLib)

// TODO handle GitSCMExtension.populateEnvironmentVariables somehow, say by optionally including GitSCMExtension/buildEnv.groovy; though GIT_{COMMITTER,AUTHOR}_{NAME,EMAIL} are only overridden by UserIdentity
['GIT_COMMIT', 'GIT_PREVIOUS_COMMIT', 'GIT_PREVIOUS_SUCCESSFUL_COMMIT', 'GIT_BRANCH', 'GIT_LOCAL_BRANCH', 'GIT_CHECKOUT_DIR', 'GIT_URL', 'GIT_COMMITTER_NAME', 'GIT_AUTHOR_NAME', 'GIT_COMMITTER_EMAIL', 'GIT_AUTHOR_EMAIL'].each {name ->
    l.buildEnvVar(name: name) {
        raw(_("${name}.blurb"))
    }
}
