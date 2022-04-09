package hudson.plugins.git.extensions.impl;

import hudson.plugins.git.browser.BitbucketWeb;
import hudson.plugins.git.browser.GitLab;
import hudson.plugins.git.browser.GitRepositoryBrowser;
import hudson.plugins.git.browser.GithubWeb;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class BrowserGuesserTest {

    BrowserGuesser guesser;
    @Before
    public void setUp(){
        guesser = new BrowserGuesser();
    }

    @Test
    public void guessBrowser(){
        assertEquals(guesser.guessBrowser("https://bitbucket.org/").getClass(),BitbucketWeb.class);
        assertEquals(guesser.guessBrowser("https://gitlab.com/").getClass(), GitLab.class);
        assertEquals(guesser.guessBrowser("https://github.com/").getClass(),GithubWeb.class);
        assertEquals(guesser.guessBrowser("https://github.com"),null);
    }
}
