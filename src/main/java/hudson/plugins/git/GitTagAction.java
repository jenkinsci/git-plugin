package hudson.plugins.git;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.*;
import hudson.plugins.git.util.BuildData;
import hudson.remoting.VirtualChannel;
import hudson.scm.AbstractScmTagAction;
import hudson.security.Permission;
import hudson.util.CopyOnWriteMap;
import hudson.util.MultipartFormDataParser;
import jenkins.model.*;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Vivek Pandey
 */
@ExportedBean
public class GitTagAction extends AbstractScmTagAction implements Describable<GitTagAction> {

    /**
     * Map is from the repository URL to the URLs of tags.
     * If a module is not tagged, the value will be empty list.
     * Never an empty map.
     */
    private final Map<String, List<String>> tags = new CopyOnWriteMap.Tree<String, List<String>>();

    private final String ws;

    protected GitTagAction(AbstractBuild build, BuildData buildData) {
        super(build);
        List<String> val = new ArrayList<String>();
        this.ws = build.getWorkspace().getRemote();
        for (Branch b : buildData.lastBuild.revision.getBranches()) {
            tags.put(b.getName(), new ArrayList<String>());
        }
    }

    public Descriptor<GitTagAction> getDescriptor() {
        return Jenkins.getInstance().getDescriptorOrDie(getClass());
    }

    @Override
    public boolean isTagged() {
        for (List<String> t : tags.values()) {
            if (!t.isEmpty()) return true;
        }
        return false;
    }

    public String getIconFileName() {
        if (!isTagged() && !getACL().hasPermission(getPermission()))
            return null;
        return "save.gif";
    }

    public String getDisplayName() {
        int nonNullTag = 0;
        for (List<String> v : tags.values()) {
            if (!v.isEmpty()) {
                nonNullTag += v.size();
                if (nonNullTag > 1)
                    break;
            }
        }
        if (nonNullTag == 0)
            return "No Tags";
        if (nonNullTag == 1)
            return "There is one tag";
        else
            return "There are more than one tag";
    }

    /**
     * @see #tags
     */
    public Map<String, List<String>> getTags() {
        return Collections.unmodifiableMap(tags);
    }

    /**
     * Register tags added from gitPublisher
     */
    public void RegisterTag(String branchName, String tagName)
    {
        ArrayList<String> tagArray = new ArrayList<String>();
        tagArray.add(tagName);
        this.tags.put(branchName, tagArray);
    }
    
    @Exported(name = "tags")
    public List<TagInfo> getTagInfo() {
        List<TagInfo> data = new ArrayList<TagInfo>();
        for (Map.Entry<String, List<String>> e : tags.entrySet()) {
            String module = e.getKey();
            for (String tag : e.getValue())
                data.add(new TagInfo(module, tag));
        }
        return data;
    }

    @ExportedBean
    public static class TagInfo {
        private String module, url;

        private TagInfo(String branch, String tag) {
            this.module = branch;
            this.url = tag;
        }

        @Exported
        public String getBranch() {
            return module;
        }

        @Exported
        public String getTag() {
            return url;
        }
    }

    @Override
    public String getTooltip() {
        String tag = null;
        for (List<String> v : tags.values()) {
            for (String s : v) {
                if (tag != null) return "Tagged"; // Multiple tags
                tag = s;
            }
        }
        if (tag != null) return "Tag: " + tag;
        else return null;
    }

    /**
     * Invoked to actually tag the workspace.
     */
    public synchronized void doSubmit(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        getACL().checkPermission(getPermission());

        MultipartFormDataParser parser = new MultipartFormDataParser(req);

        Map<String, String> newTags = new HashMap<String, String>();

        int i = -1;
        for (String e : tags.keySet()) {
            i++;
            if (tags.size() > 1 && parser.get("tag" + i) == null)
                continue; // when tags.size()==1, UI won't show the checkbox.
            newTags.put(e, parser.get("name" + i));
        }

        new TagWorkerThread(newTags, parser.get("comment")).start();

        rsp.sendRedirect(".");
    }

    /**
     * The thread that performs tagging operation asynchronously.
     */
    public final class TagWorkerThread extends TaskThread {
        private final Map<String, String> tagSet;
        /**
         * If the user provided a separate credential, this object represents that.
         */
        private final String comment;

        public TagWorkerThread(Map<String, String> tagSet,String comment) {
            super(GitTagAction.this, ListenerAndText.forMemory());
            this.tagSet = tagSet;
            this.comment = comment;
        }

        @Override
        protected void perform(final TaskListener listener) throws Exception {
            final EnvVars environment = build.getEnvironment(listener);
            for (final String b : tagSet.keySet()) {
                try {
                    final FilePath workspace = new FilePath(new File(ws));
                    Object returnData = workspace.act(new FilePath.FileCallable<Object[]>() {
                        private static final long serialVersionUID = 1L;

                        public Object[] invoke(File localWorkspace, VirtualChannel channel)
                                throws IOException {

                            GitClient git = Git.with(listener, environment)
                                    .in(localWorkspace)
                                    .getClient();

                            String buildNum = "jenkins-" 
                                             + build.getProject().getName().replace(" ", "_") 
                                             + "-" + tagSet.get(b);
                            git.tag(tagSet.get(b), "Jenkins Build #" + buildNum);
                            return new Object[]{null, build};
                        }
                    });
                    for (Map.Entry<String, String> e : tagSet.entrySet())
                        GitTagAction.this.tags.get(e.getKey()).add(e.getValue());

                     getBuild().save();
                    workerThread = null;
                }
                catch (GitException ex) {
                    ex.printStackTrace(listener.error("Error taggin repo '%s' : %s", b, ex.getMessage()));
                    // Failed. Try the next one
                    listener.getLogger().println("Trying next branch");
                }
            }
        }
    }


    @Override
    public Permission getPermission() {
        return GitSCM.TAG;
    }

    /**
     * Just for assisting form related stuff.
     */
    @Extension
    public static class DescriptorImpl extends Descriptor<GitTagAction> {
        public String getDisplayName() {
            return "Tag";
        }
    }
}
