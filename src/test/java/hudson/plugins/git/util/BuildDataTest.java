package hudson.plugins.git.util;

import hudson.plugins.git.AbstractGitTestCase;

import hudson.plugins.git.util.BuildData;

/**
 * @author Mark Waite
 */
public class BuildDataTest extends AbstractGitTestCase {
    /**
     * Verifies that the display name is "Git Build Data".
     */
    public void testDisplayNameWithoutSCM() throws Exception {
        final BuildData data = new BuildData();
        assertEquals(data.getDisplayName(), "Git Build Data");
    }

    /**
     * Verifies that the display name is "Git Build Data:scmName".
     */
    public void testDisplayNameWithSCM() throws Exception {
        final String scmName = "testSCM";
        final BuildData data = new BuildData(scmName);
        assertEquals("Git Build Data:" + scmName, data.getDisplayName());
    }
}
