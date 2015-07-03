package hudson.plugins.git.util;

import static org.junit.Assert.*;
import org.junit.Test;

/**
 * @author Mark Waite
 */
public class BuildDataTest {

    @Test
    public void testDisplayNameWithoutSCM() throws Exception {
        final BuildData data = new BuildData();
        assertEquals(data.getDisplayName(), "Git Build Data");
    }

    @Test
    public void testDisplayNameWithSCM() throws Exception {
        final String scmName = "testSCM";
        final BuildData data = new BuildData(scmName);
        assertEquals("Git Build Data:" + scmName, data.getDisplayName());
    }
}
