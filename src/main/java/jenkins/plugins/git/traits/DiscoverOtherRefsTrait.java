/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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
package jenkins.plugins.git.traits;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import jenkins.plugins.git.GitSCMBuilder;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitSCMSourceContext;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.trait.SCMBuilder;
import jenkins.scm.api.trait.SCMSourceContext;
import jenkins.scm.api.trait.SCMSourceTrait;
import jenkins.scm.api.trait.SCMSourceTraitDescriptor;
import jenkins.scm.impl.trait.Discovery;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.lib.Constants;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import static jenkins.plugins.git.AbstractGitSCMSource.REF_SPEC_REMOTE_NAME_PLACEHOLDER_STR;

public class DiscoverOtherRefsTrait extends SCMSourceTrait {

    private final String ref;
    private String nameMapping;

    @DataBoundConstructor
    public DiscoverOtherRefsTrait(String ref) {
        if (StringUtils.isEmpty(ref)) {
            throw new IllegalArgumentException("ref can not be empty");
        }
        this.ref = StringUtils.removeStart(StringUtils.removeStart(ref, Constants.R_REFS), "/");
        setDefaultNameMapping();
    }

    //for easier testing
    public DiscoverOtherRefsTrait(String ref, String nameMapping) {
        this(ref);
        setNameMapping(nameMapping);
    }

    public String getRef() {
        return ref;
    }

    String getFullRefSpec() {
        return new StringBuilder("+")
        .append(Constants.R_REFS).append(ref)
        .append(':').append(Constants.R_REMOTES)
        .append(REF_SPEC_REMOTE_NAME_PLACEHOLDER_STR)
        .append('/').append(ref).toString();
    }

    public String getNameMapping() {
        return nameMapping;
    }

    @DataBoundSetter
    public void setNameMapping(String nameMapping) {
        if (StringUtils.isEmpty(nameMapping)) {
            setDefaultNameMapping();
        } else {
            this.nameMapping = nameMapping;
        }
    }

    private void setDefaultNameMapping() {
        this.nameMapping = null;
        String[] paths = ref.split("/");
        for (int i = 0; i < paths.length; i++) {
            if("*".equals(paths[i]) && i > 0) {
                this.nameMapping = paths[i-1] + "-@{1}";
                break;
            }
        }
        if (StringUtils.isEmpty(this.nameMapping)) {
            if (ref.contains("*")) {
                this.nameMapping = "other-@{1}";
            } else {
                this.nameMapping = "other-ref";
            }
        }
    }

    @Override
    protected void decorateContext(SCMSourceContext<?, ?> context) {
        GitSCMSourceContext c = (GitSCMSourceContext) context;
        c.withRefSpec(getFullRefSpec());
        c.wantOtherRef(new GitSCMSourceContext.RefNameMapping(this.ref, this.nameMapping));
    }

    /**
     * Our descriptor.
     */
    @Extension
    @Discovery
    public static class DescriptorImpl extends SCMSourceTraitDescriptor {

        /**
         * {@inheritDoc}
         */
        @Override
        @NonNull
        public String getDisplayName() {
            return Messages.DiscoverOtherRefsTrait_displayName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Class<? extends SCMBuilder> getBuilderClass() {
            return GitSCMBuilder.class;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Class<? extends SCMSourceContext> getContextClass() {
            return GitSCMSourceContext.class;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Class<? extends SCMSource> getSourceClass() {
            return GitSCMSource.class;
        }
    }
}
