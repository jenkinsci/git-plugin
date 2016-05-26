package hudson.plugins.git.util.CompoundBuildChooser;

def f = namespace(lib.FormTagLib)

f.description {
    raw(_("blurb"))
}

f.entry(title:_("Maximum Age of Commit"), field:"maximumAgeInDays") {
    f.textbox()
}

f.description {
    raw(_("commit_in_ancestry_blurb"))
}

f.entry(title:_("Commit in Ancestry"), field:"ancestorCommitSha1") {
    f.textbox()
}
