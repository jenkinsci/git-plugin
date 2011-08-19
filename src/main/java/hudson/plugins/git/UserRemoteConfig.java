package hudson.plugins.git;

import java.io.Serializable;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
public class UserRemoteConfig extends AbstractDescribableImpl<UserRemoteConfig> implements Serializable {

    private String name;
    private String refspec;
    private String url;

    @DataBoundConstructor
    public UserRemoteConfig(String url, String name, String refspec) {
        this.url = url;
        this.name = name;
        this.refspec = refspec;
    }

    @Exported
    public String getName() {
        return name;
    }

    @Exported
    public String getRefspec() {
        return refspec;
    }

    @Exported
    public String getUrl() {
        return url;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<UserRemoteConfig> {
        public FormValidation doCheckUrl(@QueryParameter String value) {
            if (value == null || value.isEmpty()) {
                return FormValidation.error("Please enter Git repository.");
            } else {
                return FormValidation.ok();
            }
        }

        @Override
        public String getDisplayName() {
            return "";
        }
    }
}
