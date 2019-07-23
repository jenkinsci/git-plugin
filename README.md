# Git SCM plugin for Jenkins

![Plugin Version](https://img.shields.io/jenkins/plugin/v/git.svg?label=version) [![Build Status](https://ci.jenkins.io/buildStatus/icon?job=Plugins/git-plugin/master)](https://ci.jenkins.io/job/Plugins/job/gitplugin/job/master/)

Git software configuration management for Jenkins.

* see [Jenkins Wiki](https://plugins.jenkins.io/git) for feature descriptions
* use [Jenkins JIRA](https://issues.jenkins-ci.org) to report issues or request features

## Requirements

* Jenkins `2.121.1` or newer

## Development

### Branches

The `master` branch is the primary development branch.

Branches using name pattern `stable-{VERSION}` are development branches
for changes from a base release `VERSION`. For example `stable-3.x` is the
branch used to release fixes for plugin version `3.x`.

### Building the Plugin

To build the plugin you will need
* [Maven](https://maven.apache.org/) version `3.5.4` or newer
* [Java Development Kit (JDK)](https://jdk.java.net/) version `8`

Run the following command to build the plugin

```shell
mvn package
```

### Contributing to the Plugin

Plugin source code is hosted on [GitHub](https://github.com/jenkinsci/git-plugin).
New feature proposals and bug fix proposals should be submitted as
[pull requests](https://help.github.com/articles/creating-a-pull-request).
Fork the repository. Make the desired changes in your forked copy. Submit
a pull request to the `master` branch. Your pull request will be evaluated
by the [Jenkins job](https://ci.jenkins.io/job/Plugins/job/git-plugin/).

Before submitting your pull request, please add tests which verify your
change. There have been many developers involved in the git plugin and
there are many users who depend on the git plugin. Tests help us assure
that we're delivering a reliable plugin, and that we've communicated
our intent to other developers in a way that they can detect when they
run tests.

Code coverage reporting is available as a maven target and is actively
monitored. Please improve code coverage with the tests you submit.
Code coverage reporting is written to `target/site/jacoco/` by the maven command

```shell
mvn -P enable-jacoco clean install jacoco:report
```

Before submitting your change, review the SpotBugs output to
assure that you haven't introduced new warnings.

```shell
mvn spotbugs:check
```

## To Do

* Fix [bugs](https://issues.jenkins-ci.org/secure/IssueNavigator.jspa?mode=hide&reset=true&jqlQuery=project+%3D+JENKINS+AND+status+in+%28Open%2C+"In+Progress"%2C+Reopened%29+AND+component+%3D+git-plugin)
* Improve code coverage
* Improve javadoc
