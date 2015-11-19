package hudson.plugins.git.extensions.impl.CloneOption;

def f = namespace(lib.FormTagLib);

f.entry(title:_("Shallow clone"), field:"shallow") {
    f.checkbox()
}
f.entry(title:_("Do not fetch tags"), field:"noTags") {
	f.checkbox()
}
f.entry(title:_("Shallow clone depth"), field:"depth") {
    f.textbox()
}
f.entry(title:_("Path of the reference repo to use during clone"), field:"reference") {
    f.textbox()
}
f.entry(title:_("Timeout (in minutes) for clone and fetch operations"), field:"timeout") {
    f.textbox()
}
