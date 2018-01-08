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

import hudson.plugins.git.browser.BitbucketWeb;
import hudson.plugins.git.browser.GitLab;
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

    private static Class<? extends GitRepositoryBrowser> expectedClass(String url) {
        if (url.contains("bitbucket.org")) {
            return BitbucketWeb.class;
        }
        if (url.contains("gitlab.com")) {
            return GitLab.class;
        }
        if (url.contains("github.com")) {
            return GithubWeb.class;
        }
        return null;
    }

    private static final String REPO_PATH = "jenkinsci/git-plugin";

    private static String expectedURL(String url) {
        if (url.contains("bitbucket.org")) {
            return "https://bitbucket.org/" + REPO_PATH + "/";
        }
        if (url.contains("gitlab.com")) {
            return "https://gitlab.com/" + REPO_PATH + "/";
        }
        if (url.contains("github.com")) {
            return "https://github.com/" + REPO_PATH + "/";
        }
        return null;

    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection permuteRepositoryURL() {
        /* Systematically formed test URLs */
        String[] protocols = {"https", "ssh", "git"};
        String[] usernames = {"git:password@", "git@", "bob@", ""};
        String[] hostnames = {"github.com", "bitbucket.org", "gitlab.com", "example.com"};
        String[] suffixes = {".git/", ".git", "/", ""};
        String[] slashes = {"//", "/", ""};
        List<Object[]> values = new ArrayList<>();
        for (String protocol : protocols) {
            for (String username : usernames) {
                for (String hostname : hostnames) {
                    for (String suffix : suffixes) {
                        String url = protocol + "://" + username + hostname + "/" + REPO_PATH + suffix;
                        Object[] testCase = {url, expectedClass(url), expectedURL(url)};
                        values.add(testCase);
                    }
                }
            }
        }
        /* Secure shell URL with embedded port number */
        String protocol = "ssh";
        for (String username : usernames) {
            for (String hostname : hostnames) {
                for (String suffix : suffixes) {
                    String url = protocol + "://" + username + hostname + ":22/" + REPO_PATH + suffix;
                    Object[] testCase = {url, expectedClass(url), expectedURL(url)};
                    values.add(testCase);
                }
            }
        }
        /* ssh alternate syntax */
        for (String hostname : hostnames) {
            for (String suffix : suffixes) {
                for (String slash : slashes) {
                    String url = "git@" + hostname + ":" + slash + REPO_PATH + suffix;
                    Object[] testCase = {url, expectedClass(url), expectedURL(url)};
                    values.add(testCase);
                }
            }
        }
        return values;
    }

    @Test
    public void guessedBrowser() {
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
