package hudson.plugins.git.extensions.impl.SubmoduleOption

def f = namespace(lib.FormTagLib)

f.entry(field:"disableSubmodules") {
    f.checkbox(title:_("Disable submodules processing"))
}
f.entry(field:"recursiveSubmodules") {
    f.checkbox(title:_("Recursively update submodules"))
}
f.entry(field:"trackingSubmodules") {
    f.checkbox(title:_("Update tracking submodules to tip of branch"))
}
f.entry(field:"parentCredentials") {
    f.checkbox(title:_("Use credentials from default remote of parent repository"))
}

f.entry(title:_("Path of the reference repo to use during submodule update"), field:"reference") {
    f.textbox()
}
f.entry(title:_("Timeout (in minutes) for submodules operations"), field:"timeout") {
    f.number(clazz:"number", min:1, step:1)
}
f.entry(title:_("Number of threads to use when updating submodules"), field:"threads") {
    f.number(clazz:"number", min:1, step:1)
}

f.optionalBlock(title:_("Shallow clone"), field:"shallow", inline: true) {
    f.entry(title:_("Shallow clone depth"), field:"depth") {
        f.number(clazz:"number", min:1, step:1)
    }
}
