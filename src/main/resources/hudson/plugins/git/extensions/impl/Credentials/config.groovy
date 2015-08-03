package hudson.plugins.git.extensions.impl.Credentials;

def f = namespace(lib.FormTagLib);
def c = namespace(lib.CredentialsTagLib)

f.entry(title:_("URL"), field:"url") {
    f.textbox()
}

f.entry(title:_("Credentials"), field:"credentialsId") {
    c.select(onchange="""{
            var self = this.targetElement ? this.targetElement : this;
            var r = findPreviousFormItem(self,'url');
            r.onchange(r);
            self = null;
            r = null;
    }""" /* workaround for JENKINS-19124 */)
}

f.entry {
    div(align:"right") {
        input (type:"button", value:_("Delete"), class:"repeatable-delete")
    }
}
