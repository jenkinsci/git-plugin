/*
 * The MIT License
 *
 * Copyright (c) 2017 CloudBees, Inc.
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
 *
 */

package jenkins.plugins.git;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.TaskListener;
import hudson.plugins.git.GitTool;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMSourceRequest;
import org.eclipse.jgit.transport.RefSpec;

/**
 * The {@link SCMSourceRequest} base class for {@link AbstractGitSCMSource}.
 *
 * @since 3.4.0
 */
public class GitSCMSourceRequest extends SCMSourceRequest {

    /**
     * The {@link RefSpec} instances.
     */
    private List<RefSpec> refSpecs = new ArrayList<>();
    /**
     * The remote name.
     */
    private final String remoteName;
    /**
     * The {@link GitTool#getName()}.
     */
    private final String gitTool;

    /**
     * Constructor.
     *
     * @param source   the source.
     * @param context  the context.
     * @param listener the (optional) {@link TaskListener}.
     */
    public GitSCMSourceRequest(@NonNull SCMSource source, @NonNull GitSCMSourceContext<?, ?> context, TaskListener listener) {
        super(source, context, listener);
        remoteName = context.remoteName();
        gitTool = context.gitTool();
        refSpecs = Collections.unmodifiableList(context.asRefSpecs());
    }

    /**
     * Returns the name of the {@link GitTool} to use or {@code null} to use the default.
     *
     * @return the name of the {@link GitTool} to use or {@code null} to use the default.
     */
    @CheckForNull
    public final String gitTool() {
        return gitTool;
    }

    /**
     * Returns the name to give the remote.
     *
     * @return the name to give the remote.
     */
    @NonNull
    public final String remoteName() {
        return remoteName;
    }


    /**
     * Returns the list of {@link RefSpec} instances to use.
     *
     * @return the list of {@link RefSpec} instances to use.
     */
    @NonNull
    public final List<RefSpec> refSpecs() {
        return refSpecs;
    }
}
