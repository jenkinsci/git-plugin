package hudson.plugins.git.extensions.impl.CleanCheckout;


def f = namespace(lib.FormTagLib);

f.entry(title:_("Clean submodules (Add additional -f flag)"), field:"cleanSubmodule") {
    f.checkbox()
}
