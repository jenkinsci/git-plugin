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

### Don't trigger a build on commit notifications

If checked, this repository will be ignored when the notifyCommit URL is accessed regardless of if the repository matches or not.

### Force polling using workspace

The git plugin polls remotely using `ls-remote` when configured with a single branch (no wildcards!).
When this extension is enabled, the polling is performed from a cloned copy of the workspace instead of using `ls-remote`.

If this option is selected, polling will use a workspace instead of using `ls-remote`.

### Git LFS pull after checkout

Enable [git large file support](https://git-lfs.github.com/) for the workspace by pulling large files after the checkout completes.
Requires that the master and each agent performing an LFS checkout have installed the `git lfs` command.

### Merge before build

These options allow you to perform a merge to a particular branch before building.
For example, you could specify an integration branch to be built, and to merge to master.
In this scenario, on every change of integration, Jenkins will perform a merge with the master branch, and try to perform a build if the merge is successful.
It then may push the merge back to the remote repository if the Git Push post-build action is selected.

<dl>

<dt>Name of repository</dt>
  <dd>
  Name of the repository, such as `origin`, that contains the branch.
  If left blank, it'll default to the name of the first repository configured.
  </dd>

<dt>Branch to merge to</dt>
  <dd>
  The name of the branch within the named repository to merge to, such as `master`.
  </dd>

<dt>Merge strategy</dt>
  <dd>
  Merge strategy selection.  Choices include:
  <ul>
    <li>default</li>
    <li>resolve</li>
    <li>recursive</li>
    <li>octopus</li>
    <li>ours</li>
    <li>subtree</li>
    <li>recursive_theirs</li>
  </ul>
  </dd>

<dt>Fast-forward mode</dt>
  <dd>
  <ul>
    <li>`--ff`: fast-forward which gracefully falls back to a merge commit when required</li>
    <li>`--ff-only`: fast-forward without any fallback</li>
    <li>`--no-ff`: merge commit always, even if a ast-forwardwould have been allowed</li>
  </ul>
  </dd>

</dl>

### Polling ignores commits from certain users

These options allow you to perform a merge to a particular branch before building.
For example, you could specify an integration branch to be built, and to merge to master.
In this scenario, on every change of integration, Jenkins will perform a merge with the master branch, and try to perform a build if the merge is successful.
It then may push the merge back to the remote repository if the Git Push post-build action is selected.

<dl>

<dt>Excluded Users</dt>
  <dd>
  If set and Jenkins is configured to poll for changes, Jenkins will ignore any revisions committed by users in this list when determining if a build should be triggered.
  This can be used to exclude commits done by the build itself from triggering another build, assuming the build server commits the change with a distinct SCM user.
  Using this behaviour will preclude the faster git `ls-remote` polling mechanism, forcing polling to require a workspace, as if you had selected the Force polling using workspace extension as well.

  <p>Each exclusion uses literal pattern matching, and must be separated by a new line.</p>
  </dd>

</dl>

### Polling ignores commits in certain paths

If set and Jenkins is configured to poll for changes, Jenkins will pay attention to included and/or excluded files and/or folders when determining if a build needs to be triggered.

Using this behaviour will preclude the faster remote polling mechanism, forcing polling to require a workspace thus sometimes triggering unwanted builds, as if you had selected the Force polling using workspace extension as well.
This can be used to exclude commits done by the build itself from triggering another build, assuming the build server commits the change with a distinct SCM user.
Using this behaviour will preclude the faster git `ls-remote` polling mechanism, forcing polling to require a workspace, as if you had selected the Force polling using workspace extension as well.

<dl>

<dt>Included Regions</dt>
  <dd>
  Each inclusion uses <a href="https://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html">java regular expression pattern matching</a>, and must be separated by a new line.
  An empty list implies that everything is included.
  </dd>

<dt>Excluded Regions</dt>
  <dd>
  Each exclusion uses <a href="https://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html">java regular expression pattern matching</a>, and must be separated by a new line.
  An empty list excludes nothing.
  </dd>

</dl>

### Polling ignores commits with certain messages

<dl>

<dt>Excluded Messages</dt>
  <dd>
  If set and Jenkins is set to poll for changes, Jenkins will ignore any revisions committed with message matched to Pattern when determining if a build needs to be triggered.
  This can be used to exclude commits done by the build itself from triggering another build, assuming the build server commits the change with a distinct message.

  <p>Exclusion uses <a href="https://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html">pattern matching</a>.

  <p>You can create more complex patterns using embedded flag expressions.

  <p><code>(?s).*FOO.*</code>

  <p>This example will search FOO message in all comment lines.

  </dd>

</dl>

### Prune stale remote tracking branches

Runs `git remote prune` for each remote to prune obsolete local branches.

### Sparse Checkout paths

Specify the paths that you'd like to sparse checkout.
This may be used for saving space (Think about a reference repository).
Be sure to use a recent version of Git, at least above 1.7.10.

Multiple sparse checkout path values can be added to a single job.

<dt>Path</dt>
  <dd>
  File or directory to be included in the checkout
  </dd>

</dl>

### Polling ignores commits in certain paths

If set and Jenkins is configured to poll for changes, Jenkins will pay attention to included and/or excluded files and/or folders when determining if a build needs to be triggered.

Using this behaviour will preclude the faster remote polling mechanism, forcing polling to require a workspace thus sometimes triggering unwanted builds, as if you had selected the Force polling using workspace extension as well.
This can be used to exclude commits done by the build itself from triggering another build, assuming the build server commits the change with a distinct SCM user.
Using this behaviour will preclude the faster git `ls-remote` polling mechanism, forcing polling to require a workspace, as if you had selected the Force polling using workspace extension as well.

<dl>

<dt>Included Regions</dt>
  <dd>
  Each inclusion uses [java regular expression pattern matching](https://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html), and must be separated by a new line.
  An empty list implies that everything is included.
  </dd>

<dt>Excluded Regions</dt>
  <dd>
  Each exclusion uses [java regular expression pattern matching](https://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html), and must be separated by a new line.
  An empty list excludes nothing.
  </dd>

</dl>

### Strategy for choosing what to build

When you are interested in using a job to build multiple branches, you can choose how Jenkins chooses the branches to build and the order they should be built.

This extension point in Jenkins is used by many other plugins to control the job as it builds specific commits.
When you activate those plugins, you may see them installing a custom build strategy.

<dl>

<dt>Ancestry</dt>
  <dd>
  <code>Maximum Age of Commit</code>: The maximum age of a commit (in days) for it to be built. This uses the GIT_COMMITTER_DATE, not GIT_AUTHOR_DATE
  <p><code>Commit in Ancestry</code>: If an ancestor commit (sha1) is provided, only branches with this commit in their history will be built.
  </dd>

<dt>Default</dt>
  <dd>
  Build all the branches that match the branch namne pattern.
  </dd>

<dt>Inverse</dt>
  <dd>
  Build all branches <b>except</b> for those which match the branch specifiers configure above.
  This is useful, for example, when you have jobs building your master and various release branches and you want a second job which builds all new feature branches.
  For example, branches which do not match these patterns without redundantly building master and the release branches again each time they change.
  </dd>

</dl>

### Use commit author in changelog

The default behavior is to use the Git commit's "Committer" value in build changesets.
If this option is selected, the git commit's "Author" value is used instead.

### Wipe out repository and force clone

Delete the contents of the workspace before build and before checkout.
This deletes the git repository inside the workspace and will force a full clone.

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
