import hudson.taglibs.LayoutTagLib

l=namespace(LayoutTagLib)

dt("$${GIT_REVISION}")
dd("Expands to the Git SHA1 commit ID that points to the commit that was built.")

h3("Parameters")
dl() {
  dt("length=N (optional, default to 40)")
  dd("Specify the commit ID length. Full SHA1 commit ID is 40 character long, but it is common"
    + "to cut it off at 8 or 12 as that often provide enough uniqueness and is a lot more legible.")
}
