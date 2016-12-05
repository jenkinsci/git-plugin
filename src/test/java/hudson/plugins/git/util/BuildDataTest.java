package hudson.plugins.git.util;

import hudson.model.Api;
import hudson.model.Result;
import hudson.plugins.git.Branch;
import hudson.plugins.git.Revision;

import java.util.ArrayList;
import java.util.Collection;

import org.eclipse.jgit.lib.ObjectId;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Mark Waite
 */
public class BuildDataTest {

    private BuildData data;
    private final ObjectId sha1 = ObjectId.fromString("929e92e3adaff2e6e1d752a8168c1598890fe84c");
    private final String remoteUrl = "git://github.com/jenkinsci/git-plugin.git";

    @Before
    public void setUp() throws Exception {
        data = new BuildData();
        data.addRemoteUrl(remoteUrl);
    }

    @Test
    public void testGetDisplayName() throws Exception {
        assertEquals(data.getDisplayName(), "Git Build Data");
        String scmName = "";
        BuildData dataWithSCM = new BuildData(scmName);
        assertEquals(data.getDisplayName(), "Git Build Data");
    }

    @Test
    public void testGetDisplayNameWithSCM() throws Exception {
        final String scmName = "testSCM";
        final BuildData dataWithSCM = new BuildData(scmName);
        assertEquals("Git Build Data:" + scmName, dataWithSCM.getDisplayName());
    }

    @Test
    public void testGetIconFileName() {
        assertTrue(data.getIconFileName().endsWith("/plugin/git/icons/git-32x32.png"));
    }

    @Test
    public void testGetUrlName() {
        assertEquals("git", data.getUrlName());
    }

    @Test
    public void testHasBeenBuilt() {
        assertFalse(data.hasBeenBuilt(sha1));
    }

    @Test
    public void testGetLastBuild() {
        assertEquals(null, data.getLastBuild(sha1));
    }

    @Test
    public void testSaveBuild() {
        Revision revision = new Revision(sha1);
        Build build = new Build(revision, 1, Result.SUCCESS);
        data.saveBuild(build);
        assertEquals(build, data.getLastBuild(sha1));
    }

    @Test
    public void testGetLastBuildOfBranch() {
        String branchName = "origin/master";
        assertEquals(null, data.getLastBuildOfBranch(branchName));

        Collection<Branch> branches = new ArrayList<>();
        Branch branch = new Branch(branchName, sha1);
        branches.add(branch);
        Revision revision = new Revision(sha1, branches);
        Build build = new Build(revision, 13, Result.FAILURE);
        data.saveBuild(build);
        assertEquals(build, data.getLastBuildOfBranch(branchName));
    }

    @Test
    public void testGetLastBuiltRevision() {
        Revision revision = new Revision(sha1);
        Build build = new Build(revision, 1, Result.SUCCESS);
        data.saveBuild(build);
        assertEquals(revision, data.getLastBuiltRevision());
    }

    @Test
    public void testGetBuildsByBranchName() {
        assertTrue(data.getBuildsByBranchName().isEmpty());
    }

    @Test
    public void testSetScmName() {
        assertEquals("", data.getScmName());

        final String scmName = "Some SCM name";
        data.setScmName(scmName);
        assertEquals(scmName, data.getScmName());
    }

    @Test
    public void testAddRemoteUrl() {
        BuildData empty = new BuildData();
        assertTrue(empty.getRemoteUrls().isEmpty());

        assertEquals(1, data.getRemoteUrls().size());

        String remoteUrl2 = "https://github.com/jenkinsci/git-plugin.git/";
        data.addRemoteUrl(remoteUrl2);
        assertFalse(data.getRemoteUrls().isEmpty());
        assertTrue("Second URL not found in remote URLs", data.getRemoteUrls().contains(remoteUrl2));
        assertEquals(2, data.getRemoteUrls().size());
    }

    @Test
    public void testHasBeenReferenced() {
        assertTrue(data.hasBeenReferenced(remoteUrl));
        assertFalse(data.hasBeenReferenced(remoteUrl + "xxx"));
    }

    @Test
    public void testGetApi() {
        Api api = data.getApi();
        Api apiClone = data.clone().getApi();
        assertEquals(api, api);
        assertEquals(api.getSearchUrl(), apiClone.getSearchUrl());
    }

    @Test
    public void testToString() {
        assertEquals(data.toString(), data.clone().toString());
        BuildData empty = new BuildData();
        assertTrue("Wrong empty BuildData toString '" + empty.toString() + "'",
                empty.toString().endsWith("[scmName=<null>,remoteUrls=[],buildsByBranchName={},lastBuild=null]"));
    }

    @Test
    public void testEquals() {
        // Null object not equal non-null
        BuildData nullData = null;
        assertFalse("Null object not equal non-null", data.equals(nullData));

        // Object should equal itself
        assertEquals("Object not equal itself", data, data);
        assertTrue("Object not equal itself", data.equals(data));
        assertEquals("Object hashCode not equal itself", data.hashCode(), data.hashCode());

        // Cloned object equals original object
        BuildData data1 = data.clone();
        assertEquals("Cloned objects not equal", data1, data);
        assertTrue("Cloned objects not equal", data1.equals(data));
        assertTrue("Cloned objects not equal", data.equals(data1));
        assertEquals("Cloned object hashCodes not equal", data.hashCode(), data1.hashCode());

        // Saved build makes object unequal
        Revision revision1 = new Revision(sha1);
        Build build1 = new Build(revision1, 1, Result.SUCCESS);
        data1.saveBuild(build1);
        assertFalse("Distinct objects shouldn't be equal", data.equals(data1));
        assertFalse("Distinct objects shouldn't be equal", data1.equals(data));

        // Same saved build makes objects equal
        BuildData data2 = data.clone();
        data2.saveBuild(build1);
        assertTrue("Objects with same saved build not equal", data2.equals(data1));
        assertTrue("Objects with same saved build not equal", data1.equals(data2));
        assertEquals("Objects with same saved build not equal hashCodes", data2.hashCode(), data1.hashCode());

        // Add remote URL makes objects unequal
        final String remoteUrl2 = "git://github.com/jenkinsci/git-client-plugin.git";
        data1.addRemoteUrl(remoteUrl2);
        assertFalse("Distinct objects shouldn't be equal", data.equals(data1));
        assertFalse("Distinct objects shouldn't be equal", data1.equals(data));

        // Add same remote URL makes objects equal
        data2.addRemoteUrl(remoteUrl2);
        assertTrue("Objects with same remote URL not equal", data2.equals(data1));
        assertTrue("Objects with same remote URL not equal", data1.equals(data2));
        assertEquals("Objects with same remote URL not equal hashCodes", data2.hashCode(), data1.hashCode());

        // Another saved build still keeps objects equal
        String branchName = "origin/master";
        Collection<Branch> branches = new ArrayList<>();
        Branch branch = new Branch(branchName, sha1);
        branches.add(branch);
        Revision revision2 = new Revision(sha1, branches);
        Build build2 = new Build(revision2, 1, Result.FAILURE);
        assertEquals(build1, build2); // Surprising, since build1 result is SUCCESS, build2 result is FAILURE
        data1.saveBuild(build2);
        data2.saveBuild(build2);
        assertTrue(data1.equals(data2));
        assertEquals(data1.hashCode(), data2.hashCode());

        // Saving different build results still equal BuildData,
        // because the different build results are equal
        data1.saveBuild(build1);
        data2.saveBuild(build2);
        assertTrue(data1.equals(data2));
        assertEquals(data1.hashCode(), data2.hashCode());

        // Set SCM name doesn't change equality or hashCode
        data1.setScmName("scm 1");
        assertTrue(data1.equals(data2));
        assertEquals(data1.hashCode(), data2.hashCode());
        data2.setScmName("scm 2");
        assertTrue(data1.equals(data2));
        assertEquals(data1.hashCode(), data2.hashCode());
    }
}
