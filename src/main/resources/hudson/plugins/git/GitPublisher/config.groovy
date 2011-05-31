import hudson.taglibs.LayoutTagLib

l=namespace(LayoutTagLib)
t=namespace("/lib/hudson")
st=namespace("jelly:stapler")
f=namespace("/lib/form")

script """
    function encodeAllInputs(sep, form, field) {
        var inputs = Form.getInputs(form, null, field);
        if (inputs.length == 0)
            return "";

        var rv = sep;
        for (var i = 0; i < inputs.length; ++i) {
            if (i != 0)
                rv += "&";
            rv += field+"="+encode(inputs[i].value);
        }
        return rv;
    }
"""

f.entry(field:"pushOnlyIfSuccess", title:_("Push Only If Build Succeeds")) {
  f.checkbox()
}

f.entry(field:"pushMerge", title:_("Merge Results"),
        description:_("If pre-build merging is configured, push the result back to the origin.")) {
  f.checkbox()
}

f.entry(field:"tagsToPush", title:_("Tags"),
        description:_("Tags to push to remote repositories")) {
  f.repeatable(field:"tagsToPush", add:_("Add Tag")) {
    table(width:"100%") {
      br()
      f.entry(field:"tagName", title:_("Tag to push")) {
        f.textbox(checkUrl:"'descriptorByName/GitPublisher/checkTagName?value='+escape(this.value)")
      }
      f.entry(field:"createTag", title:_("Create new tag")) {
        f.checkbox()
      }
      f.entry(field:"targetRepoName", title:_("Target remote name")) {
        f.textbox(checkUrl:"'${rootURL}/scm/GitSCM/gitRemoteNameCheck?value='+escape(this.value)"
                  + "+encodeAllInputs('&amp;', this.form, 'git.repo.name')"
                  + "+encodeAllInputs('&amp;', this.form, 'git.repo.url')")
      }
    }
    div(align:"right") {
      input(type:"button", value:"Delete Tag", class:"repeatable-delete", style:"margin-left: 1em;")
    }
  }
}

f.entry(field:"branchesToPush", title:_("Branches"),
        description:_("Branches to push to remote repositories")) {
  f.repeatable(field:"branchesToPush", add:_("Add Branch")) {
    table(width:"100%") {
      br()
      f.entry(field:"branchName", title:_("Branch to push")) {
        f.textbox(checkUrl:"'descriptorByName/GitPublisher/checkBrancName?value='+escape(this.value)")
      }
      f.entry(field:"targetRepoName", title:_("Target remote name")) {
        f.textbox(checkUrl:"'${rootURL}/scm/GitSCM/gitRemoteNameCheck?value='+escape(this.value)"
                  + "+encodeAllInputs('&amp;', this.form, 'git.repo.name')"
                  + "+encodeAllInputs('&amp;', this.form, 'git.repo.url')")
      }
    }
    div(align:"right") {
      input(type:"button", value:_("Delete Branch"), class:"repeatable-delete", style:"margin-left: 1em;")
    }
  }
}
