package hudson.plugins.git.extensions.impl.SubmoduleOption;

def f = namespace(lib.FormTagLib);

f.entry(title:__("Disable submodules processing"), field:"disableSubmodules") {
    f.checkbox()
}
f.entry(title:__("Recursively update submodules"), field:"recursiveSubmodules") {
    f.checkbox()
}
