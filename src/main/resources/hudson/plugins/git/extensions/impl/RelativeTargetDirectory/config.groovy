package hudson.plugins.git.extensions.impl.RelativeTargetDirectory;

def f = namespace(lib.FormTagLib);

f.entry(title:_("Local subdirectory for repo"), field:"relativeTargetDir") {
    f.textbox()
}
