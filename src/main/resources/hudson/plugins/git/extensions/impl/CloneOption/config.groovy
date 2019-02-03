package hudson.plugins.git.extensions.impl.CloneOption;

def f = namespace(lib.FormTagLib);

f.optionalBlock(title:_("Fetch tags"), field:"noTags", negative:true, checked:(instance==null||!instance.noTags))
f.optionalBlock(title:_("Honor refspec on initial clone"), field:"honorRefspec")
f.optionalBlock(title:_("Shallow clone"), field:"shallow")
f.entry(title:_("Shallow clone depth"), field:"depth") {
    f.number(clazz:"number", min:1, step:1)
}
f.entry(title:_("Path of the reference repo to use during clone"), field:"reference") {
    f.textbox()
}
f.entry(title:_("Timeout (in minutes) for clone and fetch operations"), field:"timeout") {
    f.number(clazz:"number", min:1, step:1)
}
