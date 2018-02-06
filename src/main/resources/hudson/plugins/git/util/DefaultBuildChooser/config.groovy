package hudson.plugins.git.util.DefaultBuildChooser;

def f = namespace(lib.FormTagLib);

f.description {
    raw(_("filter_tip_branches_blurb"))
}

f.entry(title:_("Filter tip branches"), field:"filterTipBranches") {
    f.checkbox(default: true)
}
