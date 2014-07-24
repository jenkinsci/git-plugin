package hudson.plugins.git;

import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.plugins.git.extensions.impl.EnforceGitClient;
import hudson.scm.PollingResult;
import hudson.triggers.SCMTrigger;
import hudson.util.IOUtils;
import hudson.util.RunList;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.FileUtils;
import org.jvnet.hudson.test.TemporaryDirectoryAllocator;

public abstract class SCMTriggerTest extends AbstractGitTestCase
{
    
    private TemporaryDirectoryAllocator tempAllocator;
    private ZipFile namespaceRepoZip;
    private Properties namespaceRepoCommits;
    private ExecutorService singleThreadExecutor;
        
    @Override
    protected void tearDown() throws Exception
    {
        try { //Avoid test failures due to failed cleanup tasks
            super.tearDown();
            singleThreadExecutor.shutdownNow();
            tempAllocator.dispose();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        namespaceRepoZip = new ZipFile("src/test/resources/namespaceBranchRepo.zip");
        namespaceRepoCommits = parseLsRemote(new File("src/test/resources/namespaceBranchRepo.ls-remote"));
        tempAllocator = new TemporaryDirectoryAllocator();
        singleThreadExecutor = Executors.newSingleThreadExecutor();
    }
    
    protected abstract EnforceGitClient getGitClient();
    
    protected abstract boolean isDisableRemotePoll();
    
    public void testNamespaces_with_refsHeadsMaster() throws Exception {
        check(namespaceRepoZip, namespaceRepoCommits,
            "refs/heads/master",
            namespaceRepoCommits.getProperty("refs/heads/master"),
            "origin/master");
    }

    public void testNamespaces_with_remotesOriginMaster() throws Exception {
        check(namespaceRepoZip, namespaceRepoCommits,
            "remotes/origin/master", 
            namespaceRepoCommits.getProperty("refs/heads/master"),
            "origin/master");
    }

    public void testNamespaces_with_refsRemotesOriginMaster() throws Exception {
        check(namespaceRepoZip, namespaceRepoCommits,
            "refs/remotes/origin/master", 
            namespaceRepoCommits.getProperty("refs/heads/master"),
            "origin/master");
    }

    public void testNamespaces_with_master() throws Exception {
        check(namespaceRepoZip, namespaceRepoCommits,
            "master",
            namespaceRepoCommits.getProperty("refs/heads/master"),
            "origin/master");
    }

    public void testNamespaces_with_namespace1Master() throws Exception {
        check(namespaceRepoZip, namespaceRepoCommits,
            "a_tests/b_namespace1/master",
            namespaceRepoCommits.getProperty("refs/heads/a_tests/b_namespace1/master"),
            "origin/a_tests/b_namespace1/master");
    }

    public void testNamespaces_with_refsHeadsNamespace1Master() throws Exception {
        check(namespaceRepoZip, namespaceRepoCommits,
            "refs/heads/a_tests/b_namespace1/master", 
            namespaceRepoCommits.getProperty("refs/heads/a_tests/b_namespace1/master"),
            "origin/a_tests/b_namespace1/master");
    }

    public void testNamespaces_with_namespace2Master() throws Exception {
        check(namespaceRepoZip, namespaceRepoCommits,
            "a_tests/b_namespace2/master",
            namespaceRepoCommits.getProperty("refs/heads/a_tests/b_namespace2/master"),
            "origin/a_tests/b_namespace2/master");
    }

    public void testNamespaces_with_refsHeadsNamespace2Master() throws Exception {
        check(namespaceRepoZip, namespaceRepoCommits,
            "refs/heads/a_tests/b_namespace2/master", 
            namespaceRepoCommits.getProperty("refs/heads/a_tests/b_namespace2/master"),
            "origin/a_tests/b_namespace2/master");
    }
    
    public void testTags_with_TagA() throws Exception {
        check(namespaceRepoZip, namespaceRepoCommits,
            "TagA",
            namespaceRepoCommits.getProperty("refs/tags/TagA"),
            "TagA"); //TODO: What do we expect!?
    }

    public void testTags_with_TagBAnnotated() throws Exception {
        check(namespaceRepoZip, namespaceRepoCommits,
            "TagBAnnotated", 
            namespaceRepoCommits.getProperty("refs/tags/TagBAnnotated^{}"),
            "TagBAnnotated"); //TODO: What do we expect!?
    }

    public void testTags_with_refsTagsTagA() throws Exception {
        check(namespaceRepoZip, namespaceRepoCommits,
            "refs/tags/TagA",
            namespaceRepoCommits.getProperty("refs/tags/TagA"),
            "refs/tags/TagA"); //TODO: What do we expect!?
    }

    public void testTags_with_refsTagsTagBAnnotated() throws Exception {
        check(namespaceRepoZip, namespaceRepoCommits,
            "refs/tags/TagBAnnotated",
            namespaceRepoCommits.getProperty("refs/tags/TagBAnnotated^{}"),
            "refs/tags/TagBAnnotated");
    }

    public void testCommitAsBranchSpec() throws Exception {
        check(namespaceRepoZip, namespaceRepoCommits,
            namespaceRepoCommits.getProperty("refs/heads/b_namespace3/master"), 
            namespaceRepoCommits.getProperty("refs/heads/b_namespace3/master"),
            "detached");
    }

    
    public void check(ZipFile repoZip, Properties commits, String branchSpec, 
            String expected_GIT_COMMIT, String expected_GIT_BRANCH) throws Exception {
        File tempRemoteDir = tempAllocator.allocate();
        extract(repoZip, tempRemoteDir);
        final String remote = tempRemoteDir.getAbsolutePath();
       
        FreeStyleProject project = setupProject(asList(new UserRemoteConfig(remote, null, null, null)),
                    asList(new BranchSpec(branchSpec)),
                    //empty scmTriggerSpec, SCMTrigger triggered manually
                    "", isDisableRemotePoll(), getGitClient()); 
        
        //Speedup test - avoid waiting 1 minute
        triggerSCMTrigger(project.getTrigger(SCMTrigger.class));
        
        FreeStyleBuild build1 = waitForBuildFinished(project, 1, 60000);
        assertNotNull("Job has not been triggered", build1);

        PollingResult poll = project.poll(listener);
        assertFalse("Polling found new changes although nothing new", poll.hasChanges());
        
        //Speedup test - avoid waiting 1 minute
        triggerSCMTrigger(project.getTrigger(SCMTrigger.class)).get(20, SECONDS);
        
        FreeStyleBuild build2 = waitForBuildFinished(project, 2, 2000);
        assertNull("Found build 2 although no new changes and no multi candidate build", build2);
        
        assertEquals("Unexpected GIT_COMMIT", 
                    expected_GIT_COMMIT, build1.getEnvironment(null).get("GIT_COMMIT"));
        assertEquals("Unexpected GIT_BRANCH", 
                    expected_GIT_BRANCH, build1.getEnvironment(null).get("GIT_BRANCH"));
    }
 
    private Future<Void> triggerSCMTrigger(final SCMTrigger trigger)
    {
        if(trigger == null) return null;
        Callable<Void> callable = new Callable<Void>() {
            public Void call() throws Exception
            {
                trigger.run();
                return null;
            }
        };
        return singleThreadExecutor.submit(callable);
    }

    private FreeStyleBuild waitForBuildFinished(FreeStyleProject project, int expectedBuildNumber, long timeout)
                throws Exception
    {
        long endTime = System.currentTimeMillis() + timeout;
        while(System.currentTimeMillis() < endTime) {
            RunList<FreeStyleBuild> builds = project.getBuilds();
            for(FreeStyleBuild build : builds) {
                if(build.getNumber() == expectedBuildNumber) {
                    if(build.getResult() != null) return build;
                    break; //Wait until build finished
                }
            }
            Thread.sleep(10);
        }
        return null;
    }

    private Properties parseLsRemote(File file) throws IOException
    {
        Properties properties = new Properties();
        Pattern pattern = Pattern.compile("([a-f0-9]{40})\\s*(.*)");
        for(Object lineO : FileUtils.readLines(file)) {
            String line = ((String)lineO).trim();
            Matcher matcher = pattern.matcher(line);
            if(matcher.matches()) {
                properties.setProperty(matcher.group(2), matcher.group(1));
            } else {
                System.err.println("ls-remote pattern does not match '" + line + "'");
            }
        }
        return properties;
    }
    
    private void extract(ZipFile zipFile, File outputDir) throws IOException
    {
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            File entryDestination = new File(outputDir,  entry.getName());
            entryDestination.getParentFile().mkdirs();
            if (entry.isDirectory())
                entryDestination.mkdirs();
            else {
                InputStream in = zipFile.getInputStream(entry);
                OutputStream out = new FileOutputStream(entryDestination);
                IOUtils.copy(in, out);
                IOUtils.closeQuietly(in);
                IOUtils.closeQuietly(out);
            }
        }
    }

}