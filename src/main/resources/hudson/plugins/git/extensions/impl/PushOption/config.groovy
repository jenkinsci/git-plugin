package hudson.plugins.git.extensions.impl.PushOption;

def f = namespace(lib.FormTagLib);

f.entry(title:_("Timeout (in minutes) for push operation"), field:"timeout") {
    f.textbox()
}
