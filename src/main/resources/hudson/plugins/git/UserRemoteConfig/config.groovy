package hudson.plugins.git.UserRemoteConfig

f = namespace(lib.FormTagLib)
c = namespace(lib.CredentialsTagLib)

f.entry(title:_("Repository URL"), field:"url") {
    f.textbox(checkMethod: "post")
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

f.advanced {
    f.entry(title:_("Name"), field:"name") {
        f.textbox()
    }
    f.entry(title:_("Refspec"), field:"refspec") {
        f.textbox()
    }
}

f.entry {
    div(class: "show-if-not-only") {
        f.repeatableDeleteButton()
    }
}
