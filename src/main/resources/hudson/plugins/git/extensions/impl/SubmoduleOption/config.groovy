package hudson.plugins.git.extensions.impl.SubmoduleOption;

def f = namespace(lib.FormTagLib);

f.entry(title:_("Disable submodules processing"), field:"disableSubmodules") {
    f.checkbox()
}
f.entry(title:_("Recursively update submodules"), field:"recursiveSubmodules") {
    f.checkbox()
}

/*
  This needs more thought

  <f:entry title="Autogenerate submodule configurations">
    <f:checkbox name="git.generate" checked="${scm.doGenerate}"/>
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