package hudson.plugins.git.util.AncestryBuildChooser;

def f = namespace(lib.FormTagLib);

f.description {
    raw(_("maximum_age_of_commit_blurb"))
}

f.entry(title:_("Maximum Age of Commit"), field:"maximumAgeInDays") {
    f.number(clazz:"number", min:0, step:1)
}

f.description {
    raw(_("commit_in_ancestry_blurb"))
}

f.entry(title:_("Commit in Ancestry"), field:"ancestorCommitSha1") {
    f.textbox()
}
