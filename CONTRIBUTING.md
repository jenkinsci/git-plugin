Contributing to the Git Plugin
==============================

The git plugin implements the [Jenkins SCM API](https://wiki.jenkins.io/display/JENKINS/SCM+API+Plugin).
Refer to the SCM API documentation for [plugin naming conventions]https://github.com/jenkinsci/scm-api-plugin/blob/master/docs/implementation.adoc#naming-your-plugin(),
and for the [preferred locations of new functionality](https://github.com/jenkinsci/scm-api-plugin/blob/master/CONTRIBUTING.md#add-to-core-or-create-extension-plugin).

Plugin source code is hosted on [GitHub](https://github.com/jenkinsci/git-plugin).
New feature proposals and bug fix proposals should be submitted as
[GitHub pull requests](https://help.github.com/articles/creating-a-pull-request)
or can be submitted directly if you have commit permission to the
git-plugin repository.

If you're using a pull request, fork the repository on GitHub, prepare
your change on your forked copy, and submit a pull request.  Your pull
request will be evaluated by the
[Cloudbees Jenkins job](https://ci.jenkins.io/job/Plugins/job/git-plugin/).

Before submitting your change, please assure that you've added tests
which verify your change.  There have been many developers involved in
the git plugin and there are many, many users who depend on the git
plugin.  Tests help us assure that we're delivering a reliable plugin,
and that we've communicated our intent to other developers in a way
that they can detect when they run tests.

Code coverage reporting is available as a maven target.  Please try
your best to improve code coverage with tests when you submit.

Before submitting your change, please review the findbugs output to
assure that you haven't introduced new findbugs warnings.
- `mvn findbugs:check` to analyze project using [Findbugs](http://findbugs.sourceforge.net/)
- `mvn findbugs:gui` to check Findbugs report using GUI


Code formatting in the git plugin varies between files.  Try to
maintain reasonable consistency with the existing files where
feasible.  Please don't perform wholesale reformatting of a file
without discussing with the current maintainers.
