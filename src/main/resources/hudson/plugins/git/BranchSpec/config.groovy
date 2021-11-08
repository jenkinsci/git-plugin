package hudson.plugins.git.BranchSpec

f = namespace(lib.FormTagLib)

f.entry(title:_("Branch Specifier (blank for 'any')"), field:"name") {
    f.textbox(default:"*/master")
}

f.entry {
    div() {
        f.repeatableDeleteButton()
    }
}