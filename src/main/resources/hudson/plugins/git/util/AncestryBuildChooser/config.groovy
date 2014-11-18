package com.teslamotors.jenkins.extensions;

def f = namespace(lib.FormTagLib);

f.description {
    raw(_("maximum_age_of_commit_blurb"))
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
