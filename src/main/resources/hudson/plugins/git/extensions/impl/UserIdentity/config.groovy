package hudson.plugins.git.extensions.impl.UserIdentity;

def f = namespace(lib.FormTagLib);

f.entry(title:_("user.name"), field:"name") {
    f.textbox()
}
f.entry(title:_("user.email"), field:"email") {
    f.textbox()
}
