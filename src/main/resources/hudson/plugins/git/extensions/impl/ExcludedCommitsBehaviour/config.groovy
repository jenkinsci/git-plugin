package hudson.plugins.git.extensions.impl.ExcludedCommitsBehaviour;

def f = namespace(lib.FormTagLib);

f.entry(title:_("Hide excluded commits"), field:"hideExcludedCommits") {
    f.checkbox(default:true)
}
