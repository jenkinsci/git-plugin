package hudson.plugins.git.util;

import hudson.plugins.git.browser.GithubWeb;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class BuildDataBrowserTest {

    @Test
    public void testBrowserPersistence() {
        BuildData data = new BuildData();
        assertNull(data.getBrowser());

        GithubWeb browser = new GithubWeb("https://github.com/jenkinsci/git-plugin");
        data.setBrowser(browser);
        assertEquals(browser, data.getBrowser());
    }
}
