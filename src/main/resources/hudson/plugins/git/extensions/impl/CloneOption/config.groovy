package hudson.plugins.git.extensions.impl.CloneOption

def f = namespace(lib.FormTagLib)

f.entry(field:"noTags") {
    f.checkbox(title:_("Fetch tags"), negative:true, checked:(instance==null||!instance.noTags))
}
f.entry(field:"longPath") {
    f.checkbox(title:_("Honor refspec on initial clone"))
}
f.entry(field:"honorRefspec") {
    f.checkbox(title:_("Honor refspec on initial clone"))
}
f.entry(field:"shallow") {
    f.checkbox(title:_("Shallow clone"))
}
f.entry(field:"depth") {
    f.number(clazz:"number", min:1, step:1)
}
f.entry(title:_("Path of the reference repo to use during clone"), field:"reference") {
    f.textbox()
}
f.entry(title:_("Timeout (in minutes) for clone and fetch operations"), field:"timeout") {
    f.number(clazz:"number", min:1, step:1)
}
