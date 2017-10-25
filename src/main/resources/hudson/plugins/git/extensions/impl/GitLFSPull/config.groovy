package hudson.plugins.git.extensions.impl.GitLFSPull;

f = namespace(lib.FormTagLib)
c = namespace(lib.CredentialsTagLib)

f.advanced {
    f.entry(title:_("Credentials"), field:"credentialsId") {
        c.select()
    }
}
