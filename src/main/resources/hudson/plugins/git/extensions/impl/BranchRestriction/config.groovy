package hudson.plugins.git.extensions.impl.BranchRestriction;

def f = namespace(lib.FormTagLib);

f.entry(title:_("Whitelist"), field:"whitelist") {
    f.textbox()
}
f.entry(title:_("Blacklist"), field:"blacklist") {
    f.textbox()
}