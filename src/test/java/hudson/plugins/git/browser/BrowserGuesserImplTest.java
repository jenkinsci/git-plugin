package hudson.plugins.git.browser;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class BrowserGuesserImplTest {

    BrowserGuesser guesser;
    @Before
    public void setUp(){
        guesser = new BrowserGuesserImpl();
    }

    @Test
    public void guessBrowser(){
        assertEquals(guesser.guessBrowser("https://bitbucket.org/").getClass(),BitbucketWeb.class);
        assertEquals(guesser.guessBrowser("https://gitlab.com/").getClass(), GitLab.class);
        assertEquals(guesser.guessBrowser("https://github.com/").getClass(),GithubWeb.class);
        assertEquals(guesser.guessBrowser("https://github.com"),null);
    }
}
