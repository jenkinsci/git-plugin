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

import hudson.model.Action;
import hudson.model.InvisibleAction;
import hudson.model.queue.FoldableAction;
import hudson.model.Queue;
import hudson.model.Queue.QueueAction;
import hudson.Util;

import org.eclipse.jgit.lib.ObjectId;
import java.io.Serializable;
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

    public RevisionParameterAction(String commit) {
        this(commit, false);
    }

    public RevisionParameterAction(String commit, boolean combineCommits) {
        this.commit = commit;
        this.combineCommits = combineCommits;
    }

    public Revision toRevision(IGitAPI git) {
        ObjectId sha1 = git.revParse(commit);
        Revision revision = new Revision(sha1);
        // TODO: if commit is a branch, retain that information instead of making it 'detached'
        revision.getBranches().add(new Branch("detached", sha1));
        return revision;
    }

    @Override
    public String toString() {
        return super.toString()+"[commit="+commit+"]";
    }

    /**
     * Returns whether the new item should be scheduled. 
     * An action should return true if the associated task is 'different enough' to warrant a separate execution.
     * from {@link #QueueAction}
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
     * from {@link #FoldableAction}
     */
    public void foldIntoExisting(Queue.Item item, Queue.Task owner, List<Action> otherActions) {
        // only do this if we are asked to.
        if(combineCommits) {
            RevisionParameterAction existing = item.getAction(RevisionParameterAction.class);
            if (existing!=null) {
                //because we cannot modify the commit in the existing action remove it and add self
                item.getActions().remove(existing);
                item.getActions().add(this);
                return;
            }
            // no CauseAction found, so add a copy of this one
            item.getActions().add(this);
        }
    }

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(RevisionParameterAction.class.getName());
}

