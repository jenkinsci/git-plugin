package hudson.plugins.git.extensions.impl.CleanCheckout

def f = namespace(lib.FormTagLib)

f.entry(title: _("Delete untracked nested repositories"), field: "deleteUntrackedNestedRepositories") {
    f.checkbox()
}
