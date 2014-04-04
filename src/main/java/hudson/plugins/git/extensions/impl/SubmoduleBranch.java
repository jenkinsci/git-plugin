package hudson.plugins.git.extensions.impl;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;

public class SubmoduleBranch extends AbstractDescribableImpl<SubmoduleBranch> implements Serializable {

    private static final long serialVersionUID = -1234567890L;

    private String submodule;
    private String branch;

    @DataBoundConstructor
    public SubmoduleBranch(String submodule, String branch) {
        this.submodule = submodule;
        this.branch = branch;
    }

    public String getSubmodule() {
        return submodule;
    }

    public String getBranch() {
        return branch;
    }

    // public Descriptor<SubmoduleBranch> getDescriptor() {
    //     return Hudson.getInstance().getDescriptor(getClass());
    // }

    @Extension
    public static class DescriptorImpl extends Descriptor<SubmoduleBranch> {
        @Override
        public String getDisplayName() {
            return "SubmoduleBranch";
        }
    }
}
