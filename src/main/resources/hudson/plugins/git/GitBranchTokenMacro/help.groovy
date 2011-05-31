import hudson.taglibs.LayoutTagLib

l=namespace(LayoutTagLib)

dt("$${GIT_BRANCH}")
dd("Expands to the name of the branch that was built.")
h3("Parameters")
dl() { 
  dt("all")
  dd("If specified, all the branches that point to the given commit are listed.\n"
     + "By default, the token expands to just one of them.")
  dt("fullName")
  dd("If specified, this token expands to the full branch name, such as 'origin/master'.\n"
     + "Otherwise, it only expands to the short name, such as 'master'.")
}
