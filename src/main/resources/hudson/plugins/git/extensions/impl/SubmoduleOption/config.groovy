package hudson.plugins.git.extensions.impl.SubmoduleOption

def f = namespace(lib.FormTagLib)

f.entry(field:"disableSubmodules") {
    f.checkbox(title:_("Disable submodules processing"))
}
f.entry(field:"recursiveSubmodules") {
    f.checkbox(title:_("Recursively update submodules"))
}
f.entry(field:"trackingSubmodules") {
    f.checkbox(title:_("Update tracking submodules to tip of branch"))
}
f.entry(field:"parentCredentials") {
    f.checkbox(title:_("Use credentials from default remote of parent repository"))
}

f.entry(title:_("Path of the reference repo to use during submodule update"), field:"reference") {
    f.textbox()
}
f.entry(title:_("Timeout (in minutes) for submodules operations"), field:"timeout") {
    f.number(clazz:"number", min:1, step:1)
}
f.entry(title:_("Number of threads to use when updating submodules"), field:"threads") {
    f.number(clazz:"number", min:1, step:1)
}

f.optionalBlock(title:_("Shallow clone"), field:"shallow", inline: true) {
    f.entry(title:_("Shallow clone depth"), field:"depth") {
        f.number(clazz:"number", min:1, step:1)
    }
}

/*
  This needs more thought

  <f:entry>
    <f:checkbox title="Autogenerate submodule configurations" name="git.generate" checked="${scm.doGenerate}"/>
    <label class="attach-previous">Generate submodule configurations</label>

    <f:repeatable var="smcfg" name="smcfg" varStatus="cfgStatus" items="${scm.submoduleCfg}" noAddButton="false">
           <table width="100%">
           <f:entry title="Name of submodule">
             <f:textbox name="git.submodule.name" value="${smcfg.submoduleName}" />
           </f:entry>

           <f:entry title="Matching Branches">
            <f:textbox name="git.submodule.match" value="${smcfg.branchesString}" />
           </f:entry>


           <f:entry>
            <div align="right">
                <input type="button" value="Delete" class="repeatable-delete" style="margin-left: 1em;" />
            </div>
          </f:entry>
          </table>

        </f:repeatable>

  </f:entry>
*/
