package hudson.plugins.git.extensions.impl.JGitCredential;

def f = namespace(lib.FormTagLib);

f.entry(title:_("Credentials"), field:"credentialsId") {
    f.select()
}
