package hudson.plugins.git.extensions.impl.CleanBeforeCheckout;


def f = namespace(lib.FormTagLib);

f.entry(title:_("Clean submodules (Add additional -f flag)"), field:"cleanSubmodule") {
    f.checkbox()
}