import hudson.taglibs.LayoutTagLib

l=namespace(LayoutTagLib)

/*
  Displays the Git change log digest for the build top page 
  when a build history link (or number) is followed
  e.g http://<hudson server>/job/<project>/<build number>/
*/

browser = it.build.parent.scm.effectiveBrowser

if (it.emptySet) {
  raw(_("No Changes"))
} else {
  raw(_("Changes"))
  ol() {
    it.logs.eachWithIndex { cs,index ->
      li() {
        raw(cs.msgAnnotated())
        raw("(")
        a(href:"changes#detail${index}", "detail")
        cslink = browser.getChangeSetLink(cs)
        if (cs != null) {
          raw(" / ")
          a(href:"${cslink}", browser.descriptor.displayName)
        }
        raw(")")
      }
    }
  }
}

