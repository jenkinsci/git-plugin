package hudson.plugins.git.util;

import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.Api;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.util.List;

@ExportedBean(defaultVisibility = 999)
public class MultiBuildData implements Action {

    private AbstractBuild<?,?> build;

    public MultiBuildData(AbstractBuild<?,?> build) {
        this.build = build;
        this.build = build;
    }

    @Exported
    public List<BuildData> getData() {
        return build.getActions(BuildData.class);
    }

    public String getDisplayName() {
        return "Git Build Data";
    }

    public String getIconFileName() {
        return "/plugin/git/icons/git-32x32.png";
    }

    public String getUrlName() {
        return "git";
    }

    public boolean isMultiModule() {
        return getData().size() > 1;
    }

    public Api getApi() {
        return new Api(this);
    }

}
