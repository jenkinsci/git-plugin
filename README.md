# Git SCM plugin

Git software configuration management for Jenkins

* see [Jenkins wiki](https://plugins.jenkins.io/git) for feature descriptions
* use [JIRA](https://issues.jenkins-ci.org) to report issues / feature requests

## Master Branch

The master branch is the primary development branch.

Branch names using the pattern 'stable-x.y' are development branches
for changes from a base release 'x.y'.  For example, stable-3.9 is the
branch used to release fixes based on git plugin 3.9 while master branch
development is preparing for the 4.0.0 release.

## Contributing to the Plugin

Plugin source code is hosted on [GitHub](https://github.com/jenkinsci/git-plugin).
New feature proposals and bug fix proposals should be submitted as
[pull requests](https://help.github.com/articles/creating-a-pull-request).
Fork the repository, prepare your change on your forked
copy, and submit a pull request to the master branch.  Your pull request will be evaluated
by the [Jenkins job](https://ci.jenkins.io/job/Plugins/job/git-plugin/).

Before submitting your pull request, add tests which verify your change.
There have been many developers involved in the git plugin and there are
many users who depend on the git plugin.  Tests help us assure that we're
delivering a reliable plugin, and that we've communicated our intent to
other developers in a way that they can detect when they run tests.

Code coverage reporting is available as a maven target and is actively monitored.
Please improve code coverage with the tests you submit.
Code coverage reporting is written to `target/site/jacoco/` by the maven command:

```
  $ mvn -P enable-jacoco clean install jacoco:report
```

Before submitting your change, review the findbugs output to
assure that you haven't introduced new findbugs warnings.

## Building the Plugin

```bash
  $ java -version # Need Java 1.8
  $ mvn -version # Need a modern maven version; maven 3.5.0 or later are required
  $ mvn clean install
```

## To Do

* Fix [bugs](https://issues.jenkins-ci.org/secure/IssueNavigator.jspa?mode=hide&reset=true&jqlQuery=project+%3D+JENKINS+AND+status+in+%28Open%2C+"In+Progress"%2C+Reopened%29+AND+component+%3D+git-plugin)
* Improve code coverage
* Improve javadoc
