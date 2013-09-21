package hudson.plugins.git.BranchSpec;

f = namespace(lib.FormTagLib)

f.entry(title:_("Branch Specifier (blank for default)"), field:"name") {
    f.textbox()
}

f.entry {
    div(align:"right") {
        input (type:"button", value:_("Add Branch"), class:"repeatable-add show-if-last")
        input (type:"button", value:_("Delete Branch"), class:"repeatable-delete")
    }
}
