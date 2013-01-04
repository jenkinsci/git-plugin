package hudson.plugins.git.util;

import hudson.*;
import hudson.model.*;
import hudson.plugins.git.*;
import java.io.*;
import org.kohsuke.stapler.*;
import edu.umd.cs.findbugs.annotations.*;

/**
 * LastSuccessfulBuildChooser:
 *    Override DefaultBuildChooser to always user last successful build for change log
 */
public class LastSuccessfulBuildChooser extends DefaultBuildChooser {

   @DataBoundConstructor
   public LastSuccessfulBuildChooser() {
   }

   @Override public Build prevBuildForChangelog(String branch, @Nullable BuildData data, IGitAPI git, BuildChooserContext context) throws IOException, InterruptedException {
      AbstractProject project = context.getProject();
      AbstractBuild successfulBuild = (AbstractBuild)project.getLastSuccessfulBuild();
      if (successfulBuild != null) {
         BuildData successfulBuildData = successfulBuild.getAction(BuildData.class);
         if (successfulBuildData != null && successfulBuildData.lastBuild != null) {
            Build gitBuild = successfulBuildData.lastBuild;
            if (gitBuild.revision != null) {
               return gitBuild;
            }
            if (gitBuild.mergeRevision != null) {
               return gitBuild;
            }
         }
      }
      return null;
   }

   @Extension public static final class DescriptorImpl extends BuildChooserDescriptor {
      
      @Override public String getDisplayName() {
         return "Last Successful Build Chooser";
      }

      @Override public String getLegacyId() {
         return "LastSuccess";
      }
      
   }

}
