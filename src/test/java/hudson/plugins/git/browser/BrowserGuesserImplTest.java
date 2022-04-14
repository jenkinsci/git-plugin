package hudson.plugins.git.browser;

import hudson.ExtensionList;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class BrowserGuesserImplTest {

    @ClassRule
    public static JenkinsRule j = new JenkinsRule();

    private ExtensionList<BrowserGuesser> extensions(){
        return BrowserGuesser.all();
    }

    @Test
    public void guessBrowser() {
        System.out.println(extensions());
        for (BrowserGuesser ext : extensions()) {
            assertEquals(ext.guessBrowser("https://bitbucket.org/").getClass(), BitbucketWeb.class);
            assertEquals(ext.guessBrowser("https://gitlab.com/").getClass(), GitLab.class);
            assertEquals(ext.guessBrowser("https://github.com/").getClass(), GithubWeb.class);
            assertEquals(ext.guessBrowser("https://github.com"), null);
        }
    }
}
