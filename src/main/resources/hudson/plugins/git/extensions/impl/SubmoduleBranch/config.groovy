package hudson.plugins.git.extensions.impl.SubmoduleBranch;

def f = namespace(lib.FormTagLib);

f.entry(title:_("Submodule Name"), field:"submodule") {
    f.textbox()
}

f.entry(title:_("Submodule Branch"), field:"branch") {
    f.textbox()
}

f.entry {
    div(align:"right") {
        f.repeatableDeleteButton()
    }
}
