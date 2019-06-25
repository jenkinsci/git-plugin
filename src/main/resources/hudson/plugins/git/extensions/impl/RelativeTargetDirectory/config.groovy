package hudson.plugins.git.extensions.impl.RelativeTargetDirectory;

def f = namespace(lib.FormTagLib);

f.entry(title:_("Local subdirectory for repo"), field:"relativeTargetDir") {
    f.textbox()
}
f.entry(title:_("Get the basename"), field:"basename") {
    f.checkbox()
}