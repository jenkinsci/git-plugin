package hudson.plugins.git;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.jvnet.hudson.test.Issue;

/**
 * JENKINS-30073 reports that the timestamp returns -1 for the typical timestamp
 * reported by the +%ci format to git log and git whatchanged. This test
 * duplicates the bug and tests many other date formatting cases.
 * See JENKINS-55693 for more details on joda time replacement.
 *
 * @author Mark Waite
 */
@RunWith(Parameterized.class)
public class GitChangeSetTimestampTest {

    private final String normalizedTimestamp;
    private final long millisecondsSinceEpoch;

    private final GitChangeSet changeSet;

    public GitChangeSetTimestampTest(String timestamp, String normalizedTimestamp, long millisecondsSinceEpoch) {
        this.normalizedTimestamp = normalizedTimestamp == null ? timestamp : normalizedTimestamp;
        this.millisecondsSinceEpoch = millisecondsSinceEpoch;
        changeSet = genChangeSet(timestamp);
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection createSampleChangeSets() {
        Object[][] samples = {
            /* git whatchanged dates from various time zones, months, & days */
            {"2015-10-06 19:29:47 +0300", null, 1444148987000L},
            {"2017-10-23 23:43:29 +0100", null, 1508798609000L},
            {"2017-09-21 17:35:24 -0400", null, 1506029724000L},
            {"2017-07-18 08:34:48 -0800", null, 1500395688000L},
            {"2007-12-19 01:59:25 +0000", null, 1198029565000L},
            {"2007-12-19 01:59:25 -0000", null, 1198029565000L},
            {"2017-01-13 16:20:12 -0500", null, 1484342412000L},
            {"2016-12-24 20:08:55 +0900", null, 1482577735000L},
            /* nearly ISO 8601 formatted dates from various time zones, months, & days */
            {"2013-03-21T15:16:44+0100", null, 1363875404000L},
            {"2014-11-13T01:42:14-0700", null, 1415868134000L},
            {"2010-06-24T20:08:27+0200", null, 1277402907000L},
            /* Seconds since epoch dates from various time zones, months, & days */
            {"1363879004 +0100", "2013-03-21T15:16:44+0100", 1363875404000L},
            {"1415842934 -0700", "2014-11-13T01:42:14-0700", 1415868134000L},
            {"1277410107 +0200", "2010-06-24T20:08:27+0200", 1277402907000L},
            {"1234567890 +0000", "2009-02-13T23:31:30+0000", 1234567890000L},
            /* ISO 8601 formatted dates from various time zones, months, & days */
            {"2013-03-21T15:16:44+01:00", null, 1363875404000L},
            {"2014-11-13T01:42:14-07:00", null, 1415868134000L},
            {"2010-06-24T20:08:27+02:00", null, 1277402907000L},
            /* Invalid date */
            {"2010-06-24 20:08:27am +02:00", null, -1L}
        };
        List<Object[]> values = new ArrayList<>(samples.length);
        values.addAll(Arrays.asList(samples));
        return values;
    }

    @Test
    public void testChangeSetDate() {
        assertThat(changeSet.getDate(), is(normalizedTimestamp));
    }

    @Test
    @Issue("JENKINS-30073")
    public void testChangeSetTimeStamp() {
        assertThat(changeSet.getTimestamp(), is(millisecondsSinceEpoch));
    }

    private final Random random = new Random();

    private GitChangeSet genChangeSet(String timestamp) {
        boolean authorOrCommitter = random.nextBoolean();
        String[] linesArray = {
            "commit 302548f75c3eb6fa1db83634e4061d0ded416e5a",
            "tree e1bd430d3f45b7aae54a3061b7895ee1858ec1f8",
            "parent c74f084d8f9bc9e52f0b3fe9175ad27c39947a73",
            "author Viacheslav Kopchenin <vkopchenin@odin.com> " + timestamp,
            "committer Viacheslav Kopchenin <vkopchenin@odin.com> " + timestamp,
            "",
            "    pom.xml",
            "    ",
            "    :100644 100644 bb32d78c69a7bf79849217bc02b1ba2c870a5a66 343a844ad90466d8e829896c1827ca7511d0d1ef M	modules/platform/pom.xml",
            ""
        };
        ArrayList<String> lines = new ArrayList<>(linesArray.length);
        lines.addAll(Arrays.asList(linesArray));
        return new GitChangeSet(lines, authorOrCommitter);
    }
}
