Git SCM plugin
==============

Git software configuration management support for Jenkins

* see [Jenkins wiki](https://wiki.jenkins-ci.org/display/JENKINS/Git+Plugin) for detailed feature descriptions
* use [JIRA](https://issues.jenkins-ci.org) to report issues / feature requests

2.2.x Branch
=============

This is the 2.2.x branch of the git plugin. It depends on an older
version of Jenkins core so that it can support environments using
those older versions of Jenkins. It does not support more advanced
plugins which require newer versions of Jenkins core, like the
workflow plugin.

This branch is generally limited to bug fixes rather than adding new
features.  The master branch is the primary development branch for the
git plugin.  This branch is *NOT* the primary development branch for
the git plugin.

Contributing to the Plugin
==========================

Plugin source code is hosted on [GitHub](https://github.com/jenkinsci/git-plugin).
New feature proposals and bug fix proposals should be submitted as
[GitHub pull requests](https://help.github.com/articles/creating-a-pull-request).
Fork the repository on GitHub, prepare your change on your forked
copy, and submit a pull request.  Your pull request will be evaluated
by the [Cloudbees Jenkins job](https://jenkins.ci.cloudbees.com/job/plugins/job/git-plugin/)
and you should receive e-mail with the results of the evaluation.

Before submitting your change, please assure that you've added a test
which verifies your change.  There have been many developers involved
in the git plugin and there are many, many users who depend on the
git-plugin.  Tests help us assure that we're delivering a reliable
plugin, and that we've communicated our intent to other developers in
a way that they can detect when they run tests.

Code coverage reporting is available as a maven target and is actively
monitored.  Please try your best to improve code coverage with tests
when you submit.

Before submitting your change, please review the findbugs output to
assure that you haven't introduced new findbugs warnings.

To Do
=====

* Fix [bugs](https://issues.jenkins-ci.org/secure/IssueNavigator.jspa?mode=hide&reset=true&jqlQuery=project+%3D+JENKINS+AND+status+in+%28Open%2C+"In+Progress"%2C+Reopened%29+AND+component+%3D+git-plugin)
* Create submodule tests
* Improve code coverage
* Improve javadoc
