package hudson.plugins.git;

import java.io.Serializable;
import org.kohsuke.stapler.DataBoundConstructor;

public class UserRemoteConfig implements Serializable {

    private String name;
    private String refspec;
    private String url;

    @DataBoundConstructor
    public UserRemoteConfig(String url, String name, String refspec) {
        this.url = url;
        this.name = name;
        this.refspec = refspec;
    }

    public String getName() {
        return name;
    }

    public String getRefspec() {
        return refspec;
    }

    public String getUrl() {
        return url;
    }
}
