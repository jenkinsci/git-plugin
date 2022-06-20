package jenkins.plugins.git.maintenance;

import jenkins.plugins.git.AbstractGitSCMSource;

import java.io.File;

public class GitMaintenanceSCM extends AbstractGitSCMSource {
    @Override
    public String getCredentialsId() {
        return null;
    }

    @Override
    public String getRemote() {
        return null;
    }

    public static File[] getCachesDirectory(){
       return getCachesDir();
    }
}
