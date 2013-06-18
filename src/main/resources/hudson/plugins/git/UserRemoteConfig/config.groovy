package hudson.plugins.git.UserRemoteConfig;

f = namespace(lib.FormTagLib)

f.entry(title:_("Repository URL"), field:"url") {
    f.textbox()
}
f.advanced {
    f.entry(title:_("Name"), field:"name") {
        f.textbox()
    }
    f.entry(title:_("Refspec"), field:"refspec") {
        f.textbox()
    }
}

f.entry {
    div(align:"right") {
        input (type:"button", value:_("Delete Repository"), class:"repeatable-delete")
    }
}
