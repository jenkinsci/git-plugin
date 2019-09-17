Contributing to the Git Plugin
==============================

The git plugin implements the [Jenkins SCM API](https://plugins.jenkins.io/scm-api).
Refer to the SCM API documentation for [plugin naming conventions](https://github.com/jenkinsci/scm-api-plugin/blob/master/docs/implementation.adoc#naming-your-plugin),
and for the [preferred locations of new functionality](https://github.com/jenkinsci/scm-api-plugin/blob/master/CONTRIBUTING.md#add-to-core-or-create-extension-plugin).

Plugin source code is hosted on [GitHub](https://github.com/jenkinsci/git-plugin).
New feature proposals and bug fix proposals should be submitted as
[GitHub pull requests](https://help.github.com/articles/creating-a-pull-request).
Your pull request will be evaluated by the [Jenkins job](https://ci.jenkins.io/job/Plugins/job/git-plugin/).

Before submitting your change, please assure that you've added tests
which verify your change.  There have been many developers involved in
the git plugin and there are many, many users who depend on the git
plugin.  Tests help us assure that we're delivering a reliable plugin,
and that we've communicated our intent to other developers as
executable descriptions of plugin behavior.

Code coverage reporting is available as a maven target.
Please try to improve code coverage with tests when you submit.
* `mvn -P enable-jacoco clean install jacoco:report` to report code coverage

Please don't introduce new spotbugs output.
* `mvn spotbugs:check` to analyze project using [Spotbugs](https://spotbugs.github.io/)
* `mvn spotbugs:gui` to review Findbugs report using GUI

Code formatting in the git plugin varies between files.  Try to
maintain reasonable consistency with the existing files where
feasible.  Please don't perform wholesale reformatting of a file
without discussing with the current maintainers.
New code should follow the [SCM API code style guidelines](https://github.com/jenkinsci/scm-api-plugin/blob/master/CONTRIBUTING.md#code-style-guidelines).
