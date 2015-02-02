Contributing to the Git Plugin
==============================

Plugin source code is hosted on [GitHub](https://github.com/jenkinsci/git-plugin).
New feature proposals and bug fix proposals should be submitted as
[GitHub pull requests](https://help.github.com/articles/creating-a-pull-request)
or can be submitted directly if you have commit permission to the
git-plugin repository.

If you're using a pull request, fork the repository on GitHub, prepare
your change on your forked copy, and submit a pull request.  Your pull
request will be evaluated by the
[Cloudbees Jenkins job](https://jenkins.ci.cloudbees.com/job/plugins/job/git-plugin/)
and you should receive e-mail with the results of the evaluation.

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

Code formatting in the git plugin varies between files.  Try to
maintain reasonable consistency with the existing files where
feasible.  Please don't perform wholesale reformatting of a file
without discussing with the current maintainers.
