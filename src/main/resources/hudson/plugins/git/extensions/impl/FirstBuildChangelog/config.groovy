package hudson.plugins.git.extensions.impl.FirstBuildChangelog

def f = namespace(lib.FormTagLib)

f.entry(field: "makeChangelog") {
    f.checkbox(title: _("Make Changelog"))
}
