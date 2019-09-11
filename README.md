# Git plugin

[![Build Status](https://ci.jenkins.io/job/Plugins/job/git-plugin/job/master/badge/icon)](https://ci.jenkins.io/job/Plugins/job/git-plugin/job/master/)
[![Contributors](https://img.shields.io/github/contributors/jenkinsci/git-plugin.svg)](https://github.com/jenkinsci/git-plugin/graphs/contributors)
[![GitHub release](https://img.shields.io/github/release/jenkinsci/git-plugin.svg?label=release)](https://github.com/jenkinsci/git-plugin/releases/latest)

<img src="https://git-scm.com/images/logos/downloads/Git-Logo-2Color.png" width="303">

## Introduction

The git plugin provides fundamental git operations for Jenkins projects.
It can poll, fetch, checkout, branch, list, merge, and tag repositories.

## Contents

* [Changelog](#changelog)
* [Configuration](#configuration)
* [Extensions](#extensions)
* [Environment Variables](#environment-variables)
* [Properties](#properties)
* [Combining repositories](#combining-repositories)
* [Bug Reports](#bug-reports)
* [Contributing to the Plugin](#contributing-to-the-plugin)

## Changelog

Release notes are recorded in [GitHub](https://github.com/jenkinsci/git-plugin/releases) beginning with git plugin 3.10.1.
Prior release notes are recorded on the [Jenkins wiki](https://wiki.jenkins.io/display/JENKINS/Git+Plugin#GitPlugin-ChangeLog-MovedtoGitHub).

## Configuration

### Using Credentials

The git plugin supports username / password credentials and private key credentials provided by the [Jenkins credentials plugin](https://plugins.jenkins.io/credentials).
Select credentials from the job definition drop down menu or enter their identifiers in Pipeline job definitions.

### Push Notifications

### Enabling JGit

See the [git client plugin documentation](https://plugins.jenkins.io/git-client) for instructions to enable JGit.
JGit becomes available throughout Jenkins once it has been enabled.

## Extensions

### Advanced checkout behaviors

<dl>

<dt>Timeout (in minutes) for checkout operation</dt>
  <dd>
  Specify a timeout (in minutes) for checkout.
  </dd>

</dl>

### Advanced clone behaviours

<dl>

<dt>Fetch tags</dt>
  <dd>
  Deselect this to perform a clone without tags, saving time and disk space when you just want to access what is specified by the refspec.
  </dd>

<dt>Honor refspec on initial clone</dt>
  <dd>
  Perform initial clone using the refspec defined for the repository.
  This can save time, data transfer and disk space when you only need to access the references specified by the refspec.
  </dd>

<dt>Shallow clone</dt>
  <dd>
  Perform shallow clone.
  Git will not download the complete history of the project, saving time and disk space when you just want to access the latest version of a repository.
  </dd>

<dt>Shallow clone depth</dt>
  <dd>
  Set shallow clone depth to the specified numebr of commits.
  Git will only download that many commits from the remote repository, saving time and disk space.
  </dd>

<dt id="clone-reference-repository-path">Path of the reference repo to use during clone</dt>
  <dd>
  Specify a folder containing a repository that will be used by git as a reference during clone operations.
  This option will be ignored if the folder is not available on the agent.
  </dd>

<dt>Timeout (in minutes) for clone and fetch operations</dt>
  <dd>
  Specify a timeout (in minutes) for clone and fetch operations.
  </dd>

</dl>

### Advanced sub-modules behaviours

<dl>

<dt>Disable submodules processing</dt>
  <dd>
  Ignore submodules in the repository.
  </dd>

<dt>Recursively update submodules</dt>
  <dd>
  Retrieve all submodules recursively.
  Without this option, submodules which contain other submodules will ignore the contained submodules.
  </dd>

<dt>Update tracking submodules to tip of branch</dt>
  <dd>
  Retrieve the tip of the configured branch in .gitmodules.
  </dd>

<dt>Use credentials from default remote of parent repository</dt>
  <dd>
  Use credentials from the default remote of the parent project.
  Submodule updates do not use credentials by default.
  Enabling this extension will provide the parent repository credentials to each of the submodule repositories.
  Submodule credentials require that the submodule repository must accept the same credentials as the parent project.
  If the parent project is cloned with https, then the authenticated submodule references must use https as well.
  If the parent project is cloned with ssh, then the authenticated submodule references must use ssh as well.
  </dd>

<dt>Shallow clone</dt>
  <dd>
  Perform shallow clone of submodules.
  Git will not download the complete history of the project, saving time and disk space.
  </dd>

<dt>Shallow clone depth</dt>
  <dd>
  Set shallow clone depth for submodules.
  Git will only download recent history of the project, saving time and disk space.
  </dd>

<dt id="submodule-reference-repository-path">Path of the reference repo to use during submodule update</dt>
  <dd>
  Folder containing a repository that will be used by git as a reference during submodule clone operations.
  This option will be ignored if the folder is not available on the agent running the build.
  A reference repository may contain multiple subprojects.
  See the [combining repositories](#combining-repositories) section for more details.
  </dd>

<dt>Timeout (in minutes) for submodules operations</dt>
  <dd>
  Specify a timeout (in minutes) for submodules operations.
  This option overrides the default timeout.
  </dd>

<dt>Number of threads to use when updating submodules</dt>
  <dd>
  Number of parallel processes to be used when updating submodules.
  Default is to use a single thread for submodule updates
  </dd>

</dl>

### Calculate changelog against a specific branch

<dl>

<dt>Name of repository</dt>
  <dd>
  Name of the repository, such as origin, that contains the branch.
  </dd>

<dt>Name of branch</dt>
  <dd>
  Name of the branch used for the changelog calculation within the named repository.
  </dd>

</dl>

### Checkout to a sub-directory

Specify a local directory (relative to the workspace root) where the git repository will be checked out.
If left empty, the workspace root itself will be used.

### Checkout to specific local branch

<dl>

<dt>Branch name</dt>
  <dd>
  If given, checkout the revision to build as HEAD on the named branch.
  If value is an empty string or "\*\*", then the branch name is computed from the remote branch without the origin.
  In that case, a remote branch origin/master will be checked out to a local branch named master, and a remote branch origin/develop/new-feature will be checked out to a local branch named develop/newfeature.
  </dd>

</dl>

### Clean after checkout

Clean the workspace **after** every checkout by deleting all untracked files and directories, including those which are specified in .gitignore.
Resets all tracked files to their versioned state.
Ensures that the workspace is in the same state as if cloned and checkout were performed in a new workspace.
Reduces the risk that current build will be affected by files generated by prior builds.
Does not remove files outside the workspace (like temporary files or cache files).
Does not remove files in the `.git` repository of the workspace.

### Clean before checkout

Clean the workspace **before** every checkout by deleting all untracked files and directories, including those which are specified in .gitignore.
Resets all tracked files to their versioned state.
Ensures that the workspace is in the same state as if cloned and checkout were performed in a new workspace.
Reduces the risk that current build will be affected by files generated by prior builds.
Does not remove files outside the workspace (like temporary files or cache files).
Does not remove files in the `.git` repository of the workspace.

### Create a tag for every build

Create a tag in the workspace for every build to unambiguously mark the commit that was built.
You can combine this with Git publisher to push the tags to the remote repository.

### Custom SCM name - __Deprecated__

Unique name for this SCM.
Was needed when using Git within the Multi SCM plugin.
Pipeline is the robust and feature-rich way to checkout from multiple repositories in a single job.

### Custom user name/e-mail address

<dl>

<dt>user.name</dt>
  <dd>
  Defines the user name value which git will assign to new commits made in the workspace.
  If given, `git config user.name [this]` is called before builds.
  This overrides values from the global settings.
  </dd>

<dt>user.email</dt>
  <dd>
  Defines the user email value which git will assign to new commits made in the workspace.
  If given, `git config user.email [this]` is called before builds.
  This overrides whatever is in the global settings.
  </dd>

</dl>

### Prune stale remote tracking branches

Runs `git remote prune` for each remote to prune obsolete local branches.

### Use commit author in changelog

The default behavior is to use the Git commit's "Committer" value in build changesets.
If this option is selected, the git commit's "Author" value is used instead.

## Environment Variables

## Properties

Some git plugin settings can only be controlled from command line properties set at Jenkins startup.

<dl>

<dt>Default timeout</dt>
  <dd>
  The default initial git timeout value can be overridden through the property `org.jenkinsci.plugins.gitclient.Git.timeOut` (see JENKINS-11286) ).
  The property should be set on both master and agent to have effect (see JENKINS-22547).
  </dd>

</dl>

### Combining repositories

A single reference repository may contain commits from multiple repositories.
For example, if a repository named `parent` includes references to submodules `child-1` and `child-2`, a reference repository could be created to cache commits from all three repositories using the commands:
```
$ mkdir multirepository-cache.git
$ cd  multirepository-cache.git
$ git init --bare
$ git remote add parent https://github.com/jenkinsci/git-plugin
$ git remote add child-1 https://github.com/jenkinsci/git-client-plugin
$ git remote add child-2 https://github.com/jenkinsci/platformlabeler-plugin
$ git fetch --all
```

Those commands will create a single bare repository which includes the current commits from all three repositories.
If that reference repository is used in the advanced clone options [clone reference repository](#clone-reference-repository-path), it will reduce data transfer and disc use for the parent repository.
If that reference repository is used in the submodule options [clone reference repository](#submodule-reference-repository-path), it will reduce data transfer and disc use for the submodule repositories.

## Bug Reports

Report issues and enhancements in the [Jenkins issue tracker](https://issues.jenkins-ci.org).

## Contributing to the Plugin

Refer to [contributing to the plugin](CONTRIBUTING.md) for contribution guidelines.
