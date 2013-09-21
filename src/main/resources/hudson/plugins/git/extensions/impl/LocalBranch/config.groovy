package hudson.plugins.git.extensions.impl.LocalBranch;

def f = namespace(lib.FormTagLib);

f.entry(title:_("Branch name"), field:"localBranch") {
    f.textbox()
}
