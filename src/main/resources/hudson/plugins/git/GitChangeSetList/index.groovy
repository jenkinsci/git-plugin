import hudson.taglibs.LayoutTagLib

l=namespace(LayoutTagLib)
t=namespace("/lib/hudson")
st=namespace("jelly:stapler")

/*
  Displays the Git change log.
*/

browser = it.build.parent.scm.effectiveBrowser

h2(_("Summary"))
ol() {
  it.logs.eachWithIndex { cs,index ->
    li() {
      raw("${cs.msgAnnotated} (")
      a(href:"#detail${index}", "details")
      raw(")")
    }
  }
}

table(class:"pane", style:"border:none") {
  it.logs.eachWithIndex { cs,index ->
    tr(class:"pane") {
      td(colspan:"2", class:"changeset") {
        a(name:"detail${index}")
        div(class:"changeset-message") {
          b() {
            raw("Commit")
            cslink = browser.getChangeSetLink(cs)
            if (cs != null) {
              a(href:"${cslink}", "${cs.id}")
            } else {
              raw("${cs.id}")
            }
            raw("by ")
            a(href:"${rootURL}/${cs.author.url}/", "${cs.author}")
          }
          pre("${cs.commentAnnotated}")
        }
      }
    }
    cs.paths.each { p ->
      tr() {
        td(width:"16") {
          t.editTypeIcon(type:p.editType)
        }
        td() {
          a(href:browser.getFileLink(p), p.path)
          diff = browser.getDiffLink(p)
          if (diff != null) {
            st.nbsp()
            a(href:diff, "(${diff})")
          }
        }
      }
    }
  }
}
