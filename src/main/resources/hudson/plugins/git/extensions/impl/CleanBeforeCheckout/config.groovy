package hudson.plugins.git.extensions.impl.CleanBeforeCheckout

def f = namespace(lib.FormTagLib)

f.entry(title: _("Delete untracked nested repositories"), field: "deleteUntrackedNestedRepositories") {
    f.checkbox()
}
