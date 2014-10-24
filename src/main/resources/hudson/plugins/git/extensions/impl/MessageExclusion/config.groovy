package hudson.plugins.git.extensions.impl.MessageExclusion;

def f = namespace(lib.FormTagLib);

f.entry(title:_("Excluded Messages"), field:"excludedMessage") {
    f.textbox()
}
