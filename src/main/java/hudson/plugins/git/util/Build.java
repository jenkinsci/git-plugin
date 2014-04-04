package hudson.plugins.git.util;

import hudson.model.Result;
import hudson.scm.SCMRevisionState;

import java.io.Serializable;

/**
 * @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
 * @deprecated
 */
public class Build extends SCMRevisionState implements Serializable, Cloneable {

    @Deprecated
    public transient int hudsonBuildNumber;

    @Deprecated
    public transient Result hudsonBuildResult;

    @Deprecated
    public int getBuildNumber() {
        return hudsonBuildNumber;
    }

    @Deprecated
    public Result getBuildResult() {
        return hudsonBuildResult;
    }




}
