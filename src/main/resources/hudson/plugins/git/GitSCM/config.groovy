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

f.entry(title:_("Repositories"), help:"/plugin/git/help.html") {
  repos = []
  branches = []
  bc = null
  gt = null
  doMerge = false
  
  if (instance != null) { 
    if (instance.repositories != null) {
      instance.repositories.each { r -> repos << r }
    }
    if (instance.branches != null) {
      instance.branches.each { b -> branches << b }
    }
    bc = instance.buildChooser
    gt = instance.gitTool
    doMerge = instance.getMergeOptions()!=null?true:false
  }
  f.repeatable(var:"repo", name:"repo", varStatus:"repoStatus", items:repos, minimum:"1", noAddButton:"false") {
    table(width:"100%") {
      f.entry(title:_("URL of repository"), field:"url") {
        f.textbox(value:repo!=null?repo.URIs.get(0).toPrivateString():"")
      }
      f.advanced() {
        f.entry(title:_("Name of repository (blank to create default)"), field:"name") {
          f.textbox(value:repo!=null?repo.name:"")
        }
        f.entry(title:_("Refspec (blank to create default)"), field:"refspec") {
          f.textbox(value:repo!=null?repo.fetchRefSpecs.get(0):"")
        }
      }
      f.entry() {
        div(align:"right") {
          input(type:"button", value:_("Delete Repository"), class:"repeatable-delete", style:"margin-left: 1em;")
        }
      }
    }
  }
}

f.entry(title:_("Branches to build"), help:"/plugin/git/branch.html") {
  f.repeatable(var:"branch", name:"branches", varStatus:"branchStatus", items:branches, minimum:"1", noAddButton:"false") {
    table(width:"100%") {
      f.entry(title:_("Branch Specifier (blank for default)"), field:"name") {
        f.textbox(value:branch!=null?branch.name:"")
      }
      div(align:"right") {
        input(type:"button", value:_("Delete Branch"), class:"repeatable-delete", style:"margin-left: 1em;")
      }
    }
  }
}

f.advanced() {
  f.entry(title:_("Excluded Regions"), field:"excludedRegions", help:"/plugin/git/help-excludedRegions.html") {
    f.textarea()
  }
  f.entry(title:_("Excluded Users"), field:"excludedUsers", help:"/plugin/git/help-excludedUsers.html") {
    f.textarea()
  }
  f.entry(title:_("Checkout/merge to local branch (optional)"), field:"localBranch", help:"/plugin/git/help-localBranch.html") {
    f.textbox()
  }
  f.entry(title:_("Local subdirectory for repo (optional)"), field:"relativeTargetDir", help:"/plugin/git/help-local.html") {
    f.textbox()
  }
  f.entry(title:_("Unique SCM name (optional)"), field:"scmName") {
    f.textbox()
  }
  f.entry(title:_("Config user.name Value"), field:"gitConfigName") { f.textbox() }
  f.entry(title:_("Config user.email Value"), field:"gitConfigEmail") { f.textbox() }

  f.entry(title:_("Merge options"), help:"/plugin/git/merge.html") {
    table(width:"100%") {
      f.optionalBlock(field:"doMerge", title:_("Merge before build"), checked:doMerge) {
        f.entry(title:_("Name of repository: (default first specified, e.g. origin)"), field:"mergeRemote") {
          f.textbox(id:"git.mergeRemote")
        }
        f.entry(title:_("Branch to merge to: (e.g. master)"), field:"mergeTarget") {
          f.textbox(id:"git.mergeTarget", clazz:"required")
        }
      }
    }
  }
  f.entry(title:_("Prune remote branches before build"), field:"pruneBranches", help:"/plugin/git/prune.html") { f.checkbox() }
  f.entry(title:_("Skip internal tag"), field:"skipTag", help:"/plugin/git/help-skipTag.html") { f.checkbox() }
  f.entry(title:_("Clean after checkout"), field:"clean", help:"/plugin/git/clean.html") { f.checkbox() }
  f.entry(title:_("Recursively update submodules"), field:"recursiveSubmodules", help:"/plugin/git/help-recursiveSubmodules.html") { f.checkbox() }
  f.entry(title:_("Use commit author in changelog"), field:"authorOrCommitter", help:"/plugin/git/help-authorCommitter.html") { f.checkbox() }
  f.entry(title:_("Wipe out workspace before build"), field:"wipeOutWorkspace", help:"/plugin/git/wipeOutWorkspace.html") { f.checkbox() }
  f.dropdownList(name:"buildChooser", title:_("Choosing strategy"), help:"/plugin/git/choosingStrategy.html") {
    descriptor.buildChooserDescriptors.each { cDesc ->
      shouldSelect = "false"
      if (((bc != null) && (bc.descriptor == cDesc)) ||
          ((bc == null) && (cDesc.displayName=='Default'))) {
        shouldSelect = "true"
      }
      f.dropdownListBlock(value:cDesc.clazz.name, title:cDesc.displayName,
                          selected:shouldSelect) {
        tr() {
          td () {
            input(type:"hidden", name:"stapler-class", value:cDesc.clazz.name)
          }
        }
        st.include(from:cDesc, page:cDesc.configPage, optional:"true")
      }
    }
  }
  
  f.entry(title:_("Git executable"), field:"gitTool") {
    select(name:"gitTool") {
      descriptor.getGitTools().each { gitTool ->
        if (gt == gitTool.name) {
          option(value:gitTool.name, selected:"SELECTED") {
            raw(gitTool.name)
          }
        }
        else {
          option(value:gitTool.name) { raw(gitTool.name) }
        }
      }
    }
  }
}

t.listScmBrowsers(name:"git.browser")

