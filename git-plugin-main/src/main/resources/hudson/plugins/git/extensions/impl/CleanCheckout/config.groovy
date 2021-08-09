package hudson.plugins.git.extensions.impl.CleanCheckout

def f = namespace(lib.FormTagLib)

f.entry(field: "deleteUntrackedNestedRepositories") {
    f.checkbox(title: _("Delete untracked nested repositories"))
}
