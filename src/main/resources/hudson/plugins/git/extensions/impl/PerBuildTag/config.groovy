package hudson.plugins.git.extensions.impl.PerBuildTag

def f = namespace(lib.FormTagLib);

f.entry(title:_("Tag comment"), field:"tagComment") {
    f.textarea(default:"Jenkins Build #\${BUILD_NUMBER}")
}

f.entry(title:_("Tag name"), field:"tagName") {
    f.textarea(default:"\${BUILD_TAG}")
}
