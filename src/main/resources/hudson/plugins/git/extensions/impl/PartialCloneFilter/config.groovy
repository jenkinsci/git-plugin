package hudson.plugins.git.extensions.impl.PartialCloneFilter

def f = namespace(lib.FormTagLib)

f.entry(title:_("Filter specification"), field:"filterSpec") {
    f.textbox()
}
