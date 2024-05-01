/*
 * The MIT License
 *
 * Copyright (c) 2011, CloudBees, Inc.
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

import hudson.Extension;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.plugins.git.util.BuildData;
import hudson.plugins.parameterizedtrigger.AbstractBuildParameters;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Build parameter in the parameterized build trigger to pass the Git commit to the downstream build
 * (to do something else on the same commit.)
 *
 * @author Kohsuke Kawaguchi
 */
public class GitRevisionBuildParameters extends AbstractBuildParameters {

	private boolean combineQueuedCommits = false;

	@DataBoundConstructor
	public GitRevisionBuildParameters(boolean combineQueuedCommits) {
		this.combineQueuedCommits = combineQueuedCommits;
	}

	public GitRevisionBuildParameters() {
	}

	@Override
	public Action getAction(AbstractBuild<?,?> build, TaskListener listener) {
		BuildData data = build.getAction(BuildData.class);
		if (data == null && Jenkins.get().getPlugin("promoted-builds") != null) {
            if (build instanceof hudson.plugins.promoted_builds.Promotion) {
                // We are running as a build promotion, so have to retrieve the git scm from target job
                AbstractBuild<?,?> targetBuild = ((hudson.plugins.promoted_builds.Promotion) build).getTargetBuild();
                if (targetBuild != null) {
                    data = targetBuild.getAction(BuildData.class);
                }
            }
        }
        if (data == null) {
			listener.getLogger().println("This project doesn't use Git as SCM. Can't pass the revision to downstream");
			return null;
		}

		Revision lastBuiltRevision = data.getLastBuiltRevision();
		if (lastBuiltRevision == null) {
			listener.getLogger().println("Missing build information. Can't pass the revision to downstream");
			return null;
		}

		return new RevisionParameterAction(lastBuiltRevision, getCombineQueuedCommits());
	}

	public boolean getCombineQueuedCommits() {
		return combineQueuedCommits;
	}

	@Extension(optional=true)
	public static class DescriptorImpl extends Descriptor<AbstractBuildParameters> {
		@Override
		public String getDisplayName() {
			return "Pass-through Git Commit that was built";
		}
	}
}
