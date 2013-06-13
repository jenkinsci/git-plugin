package hudson.plugins.git.extensions.impl.PathRestriction;

def f = namespace(lib.FormTagLib);

f.entry(title:_("Included Regions"), field:"includedRegions") {
    f.textarea()
}
f.entry(title:_("Excluded Regions"), field:"excludedRegions") {
    f.textarea()
}