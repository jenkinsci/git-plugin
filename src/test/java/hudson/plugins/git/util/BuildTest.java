package hudson.plugins.git.util;

import hudson.model.Result;
import hudson.plugins.git.Revision;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

public class BuildTest {

    private final int BUILD_NUMBER = 1;
    private final Result BUILD_RESULT = Result.NOT_BUILT;
    private final ObjectId objectId = ObjectId.fromString("b04752abaaa9ee3112b0c9d1910093c977d4e583");
    private final Revision revision = new Revision(objectId);
    private final Build build = new Build(revision, BUILD_NUMBER, BUILD_RESULT);
    private final Build nullRevisionBuild = new Build(null, BUILD_NUMBER, BUILD_RESULT);

    @Test
    public void testGetSHA1() {
        assertThat(build.getSHA1(), is(revision.getSha1()));
    }

    @Test
    public void testGetRevision() {
        assertThat(build.getRevision(), is(revision));
    }

    @Test
    public void testGetMarked() {
        assertThat(build.getMarked(), is(revision));
    }

    @Test
    public void testGetMarkedDifferentRevision() {
        ObjectId differentId = ObjectId.fromString("deedbeadfeedcededeafc9d1910093c977d4e583");
        Revision markedRevision = new Revision(differentId);
        Build differentBuild = new Build(markedRevision, revision, BUILD_NUMBER, BUILD_RESULT);
        assertThat(differentBuild.getMarked(), is(markedRevision));
        assertThat(build.getMarked(), is(revision));
    }

    @Test
    public void testGetBuildNumber() {
        assertThat(build.getBuildNumber(), is(BUILD_NUMBER));
    }

    @Test
    public void testGetBuildResult() {
        assertThat(build.getBuildResult(), is(BUILD_RESULT));
    }

    @Test
    public void testToString() {
        assertThat(build.toString(), is("Build #" + BUILD_NUMBER + " of Revision " + revision.getSha1String() + " ()"));

    }

    @Test
    public void testClone() {
        Build clonedBuild = build.clone();
        assertThat(clonedBuild.getSHA1(), is(build.getSHA1()));
    }

    @Test
    public void testCloneNullRevision() {
        Build clonedBuild = nullRevisionBuild.clone();
        assertThat(clonedBuild.getRevision(), is(nullValue()));
    }

    @Test
    public void testIsFor() {
        assertThat(build.isFor(revision.getSha1String()), is(true));
    }

    @Test
    public void testIsForNullRevision() {
        assertThat(nullRevisionBuild.isFor(revision.getSha1String()), is(false));
    }

    @Test
    public void testReadResolve() throws Exception {
        assertThat(build.readResolve(), is(instanceOf(build.getClass())));
    }

    @Test
    public void testReadResolveNullRevision() throws Exception {
        Object readObject = nullRevisionBuild.readResolve();
        assertThat(readObject, is(instanceOf(build.getClass())));
        Build readBuild = (Build) readObject;
        assertThat(readBuild.getMarked(), is(readBuild.getRevision()));
        assertThat(nullRevisionBuild.readResolve(), is(instanceOf(build.getClass())));
    }

    @Test
    public void equalsContract() {
        EqualsVerifier.forClass(Build.class)
                .usingGetClass()
                .suppress(Warning.NONFINAL_FIELDS)
                .withIgnoredFields("hudsonBuildResult")
                .verify();
    }
}
