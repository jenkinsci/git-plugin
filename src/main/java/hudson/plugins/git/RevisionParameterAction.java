/*
 * The MIT License
 *
 * Copyright (c) 2009-2010, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.git;

import hudson.Util;
import hudson.model.Action;
import hudson.model.InvisibleAction;
import hudson.model.Queue;
import hudson.model.Queue.QueueAction;
import hudson.model.queue.FoldableAction;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.jenkinsci.plugins.gitclient.GitClient;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;


/**
 * Used as a build parameter to specify the revision to be built.
 *
 * @author Kohsuke Kawaguchi
 * @author Chris Johnson
 */
public class RevisionParameterAction extends InvisibleAction implements Serializable,QueueAction,FoldableAction {
    /**
     * SHA1, ref name, etc. that can be "git rev-parse"d into a specific commit.
     */
    public final String commit;
    public final boolean combineCommits;
    public final Revision revision;
    private final URIish repoURL;

    public RevisionParameterAction(String commit) {
        this(commit, false, null);
    }

    public RevisionParameterAction(String commit, URIish repoURL) {
        this(commit, false, repoURL);
    }

    public RevisionParameterAction(String commit, boolean combineCommits) {
        this(commit, combineCommits, null);
    }

    public RevisionParameterAction(String commit, boolean combineCommits, URIish repoURL) {
        this.commit = commit;
        this.combineCommits = combineCommits;
        this.revision = null;
        this.repoURL = repoURL;
    }
    
    public RevisionParameterAction(Revision revision) {
        this(revision, false);
    }   

    public RevisionParameterAction(Revision revision, boolean combineCommits) {
    	this.revision = revision;
    	this.commit = revision.getSha1String();
    	this.combineCommits = combineCommits;
        this.repoURL = null;
    }   

    @Deprecated
    public Revision toRevision(IGitAPI git) throws InterruptedException {
        return toRevision((GitClient) git);
    }

    public Revision toRevision(GitClient git) throws InterruptedException {
    	if (revision != null) {
    		return revision;
    	}
        ObjectId sha1 = git.revParse(commit);
        Revision revision = new Revision(sha1);
        // Here we do not have any local branches, containing the commit. So...
        // we are to get all the remote branches, and show them to users, as
        // they are local
        final List<Branch> branches = normalizeBranches(git.getBranchesContaining(
                ObjectId.toString(sha1), true));
        revision.getBranches().addAll(branches);
        return revision;
    }

    /**
     * This method tries to determine whether the commit is from given remotes.
     * To achieve that it uses remote URL supplied during construction of this instance.
     *
     * @param remotes candidate remotes for this commit
     * @return <code>false</code> if remote URL was supplied during construction and matches none
     * of given remote URLs, otherwise <code>true</code>
     */
    public boolean canOriginateFrom(Iterable<RemoteConfig> remotes) {
        if (repoURL == null) {
            return true;
        }

        for (RemoteConfig remote : remotes) {
            for (URIish remoteURL : remote.getURIs()) {
                if (remoteURL.equals(repoURL)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * This method is aimed to normalize all the branches to the same naming
     * convention, as {@link GitClient#getBranchesContaining(String, boolean)}
     * returns branches with "remotes/" prefix.
     * @param branches branches, retrieved from git client
     * @return list of branches without the "remote/" prefix.
     */
    private List<Branch> normalizeBranches(List<Branch> branches) {
        final List<Branch> normalBranches = new ArrayList<>(branches.size());
        final String remotesPrefix = "remotes/";
        for (Branch initialBranch : branches) {
            final String initialBranchName = initialBranch.getName();
            final Branch normalBranch;
            if (initialBranchName.startsWith(remotesPrefix)) {
                final String normalName = initialBranchName.substring(remotesPrefix.length());
                normalBranch = new Branch(normalName, initialBranch.getSHA1());
            } else {
                normalBranch = initialBranch;
            }
            normalBranches.add(normalBranch);
        }
        return normalBranches;
    }

    @Override
    public String toString() {
        return super.toString()+"[commit="+commit+"]";
    }

    /**
     * Returns whether the new item should be scheduled. 
     * An action should return true if the associated task is 'different enough' to warrant a separate execution.
     * from {@link QueueAction}
      */
    public boolean shouldSchedule(List<Action> actions) {
        /* Called in two cases 
        1. On the action attached to an existing queued item 
        2. On the action attached to the new item to add.
        Behaviour 
        If actions contain a RevisionParameterAction with a matching commit to this one, we do not need to schedule
        in all other cases we do.
        */
        List<RevisionParameterAction> otherActions = Util.filter(actions,RevisionParameterAction.class);
        if(combineCommits) {
            // we are combining commits so we never need to schedule another run.
            // unless other job does not have a RevisionParameterAction (manual build)
            if(otherActions.size() != 0)
                return false;
        } else {
            for (RevisionParameterAction action: otherActions) {
                if(this.commit.equals(action.commit))
                    return false;
            }
        }
        // if we get to this point there were no matching actions so a new build is required
        return true;
    }

    /**
     * Folds this Action into another action already associated with item
     * from {@link FoldableAction}
     */
    public void foldIntoExisting(Queue.Item item, Queue.Task owner, List<Action> otherActions) {
        // only do this if we are asked to.
        if(combineCommits) {
            //because we cannot modify the commit in the existing action remove it and add self
            // or no CauseAction found, so add a copy of this one
            item.replaceAction(this);
        }
    }

    private static final long serialVersionUID = 2L;
    private static final Logger LOGGER = Logger.getLogger(RevisionParameterAction.class.getName());
}
