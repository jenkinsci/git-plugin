package hudson.plugins.git.extensions.impl.ScmName;

def f = namespace(lib.FormTagLib);

f.entry(title:_("Unique SCM name"), field:"name") {
    f.textbox()
}
