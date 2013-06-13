package hudson.plugins.git.extensions.impl.revexc.UserExclusion;

def f = namespace(lib.FormTagLib);

f.entry(title:_("Excluded Users"), field:"excludedUsers") {
    f.textarea()
}