/*
 * The MIT License
 *
 * Copyright 2017 Mark Waite.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.git;

import hudson.plugins.git.browser.GitRepositoryBrowser;
import hudson.plugins.git.browser.GithubWeb;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class GitSCMBrowserTest {

    private final String gitURI;
    private final Class<? extends GitRepositoryBrowser> expectedClass;
    private final String expectedURI;

    public GitSCMBrowserTest(String gitURI,
            Class<? extends GitRepositoryBrowser> expectedClass,
            String expectedURI) {
        this.gitURI = gitURI;
        this.expectedClass = expectedClass;
        this.expectedURI = expectedURI;
    }

    private static final String GITHUB_EXPECTED = "https://github.com/jenkinsci/git-plugin/";

    private static boolean guessBrowserExpectedToReturnNull(String url) {
        if (!url.contains("github.com")) { // Only github.com currently as autobrowser
            return true;
        }
        if (url.startsWith("git://")) { // No git protocol autobrowser currently
            return true;
        }
        if (url.startsWith("ssh://") && url.contains(":22")) { // No ssh with embedded port number currently
            return true;
        }
        return url.startsWith("https://") && url.contains("@");
    }

    private static Class<? extends GitRepositoryBrowser> expectedClass(String url) {
        if (guessBrowserExpectedToReturnNull(url)) {
            return null;
        }
        return GithubWeb.class;
    }

    private static String expectedURL(String url) {
        if (guessBrowserExpectedToReturnNull(url)) {
            return null;
        }
        return GITHUB_EXPECTED;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection permuteRepositoryURL() {
        /* Systematically formed test URLs */
        String[] protocols = {"https", "ssh", "git"};
        String[] userNames = {"git:password@", "git@", ""};
        String[] hostnames = {"github.com", "bitbucket.org", "gitlab.com"};
        String[] suffixes = {".git/", ".git", "/", ""};
        String owner = "jenkinsci";
        String repo = "git-plugin";
        List<Object[]> values = new ArrayList<>();
        for (String protocol : protocols) {
            for (String userName : userNames) {
                for (String hostname : hostnames) {
                    for (String suffix : suffixes) {
                        String url = protocol + "://" + userName + hostname + "/" + owner + "/" + repo + suffix;
                        Object[] testCase = {url, expectedClass(url), expectedURL(url)};
                        values.add(testCase);
                    }
                }
            }
        }
        /* Secure shell URL with embedded port number */
        String protocol = "ssh";
        for (String userName : userNames) {
            for (String hostname : hostnames) {
                for (String suffix : suffixes) {
                    String url = protocol + "://" + userName + hostname + ":22/" + owner + "/" + repo + suffix;
                    Object[] testCase = {url, expectedClass(url), expectedURL(url)};
                    values.add(testCase);
                }
            }
        }
        /* ssh alternate syntax */
        for (String hostname : hostnames) {
            for (String suffix : suffixes) {
                String url = "git@" + hostname + ":jenkinsci/git-plugin" + suffix;
                Object[] testCase = {url, expectedClass(url), expectedURL(url)};
                values.add(testCase);
            }
        }
        return values;
    }

    @Test
    public void autoBrowser() {
        GitSCM gitSCM = new GitSCM(gitURI);
        GitRepositoryBrowser browser = (GitRepositoryBrowser) gitSCM.guessBrowser();
        if (expectedClass == null || expectedURI == null) {
            assertThat(browser, is(nullValue()));
        } else {
            assertThat(browser, is(instanceOf(expectedClass)));
            assertThat(browser.getRepoUrl(), is(expectedURI));
        }
    }
}
