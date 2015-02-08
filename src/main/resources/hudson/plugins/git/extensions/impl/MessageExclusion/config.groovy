package hudson.plugins.git.extensions.impl.MessageExclusion;

def f = namespace(lib.FormTagLib);

f.entry(title:_("Excluded Messages"), field:"excludedMessage") {
    f.textbox()
}
f.advanced {
    f.entry(title:_("Partial Matches"), field:"partialMatch") {
        f.checkbox()
    }
    f.entry(title:_("Exclude <i>not</i> matching messages"), field:"includeInsteadOfExclude") {
        f.checkbox()
    }
    f.entry(title: 'Commit message to test', field: 'testMessage') {
        f.textarea(name: 'testMessage')
    }
    f.validateButton(
            title: 'Test Regexp', 
            progress: 'Matching', 
            method: 'matchMessage', 
            with: 'excludedMessage,includeInsteadOfExclude,partialMatch,testMessage')
}
