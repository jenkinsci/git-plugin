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

import hudson.model.InvisibleAction;

import java.io.Serializable;
import java.util.logging.Logger;

import org.spearce.jgit.lib.ObjectId;

/**
 * Used as a build parameter to specify the revision to be built.
 *
 * @author Kohsuke Kawaguchi
 */
public class RevisionParameterAction extends InvisibleAction implements Serializable {
    /**
     * SHA1, ref name, etc. that can be "git rev-parse"d into a specific commit.
     */
	public final String commit;

    public RevisionParameterAction(String commit) {
        this.commit = commit;
    }

    public Revision toRevision(IGitAPI git) {
        ObjectId sha1 = git.revParse(commit);
        Revision revision = new Revision(sha1);
        revision.getBranches().add(new Branch("detached", sha1));
        return revision;
    }

	@Override
	public String toString() {
		return super.toString()+"[commit="+commit+"]";
	}

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(RevisionParameterAction.class.getName());
}

