/*
 * The MIT License
 *
 * Copyright (c) 2016 CloudBees, Inc.
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
 *
 */

package jenkins.plugins.git;

import hudson.model.TaskListener;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.BuildChooserSetting;
import hudson.plugins.git.extensions.impl.LocalBranch;
import hudson.util.StreamTaskListener;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import jenkins.scm.api.SCMFile;
import jenkins.scm.api.SCMFileSystem;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link AbstractGitSCMSource}
 */
public class GitSCMFileSystemTest {

    @ClassRule
    public static JenkinsRule r = new JenkinsRule();
    @Rule
    public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    private final Random random = new Random();
    private final String[] implementations = {"git", "jgit", "jgitapache"};
    String gitImplName = null;

    @Before
    public void setUp() throws Exception {
        sampleRepo.init();
        sampleRepo.git("checkout", "-b", "dev");
        gitImplName = implementations[random.nextInt(implementations.length)];
    }

    @Test
    public void ofSource_Smokes() throws Exception {
        sampleRepo.init();
        sampleRepo.git("checkout", "-b", "dev");
        sampleRepo.write("file", "modified");
        sampleRepo.git("commit", "--all", "--message=dev");
        SCMSource source = new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true);
        SCMFileSystem fs = SCMFileSystem.of(source, new SCMHead("dev"));
        assertThat(fs, notNullValue());
        assertThat(fs.getRoot(), notNullValue());
        Iterable<SCMFile> children = fs.getRoot().children();
        Iterator<SCMFile> iterator = children.iterator();
        assertThat(iterator.hasNext(), is(true));
        SCMFile file = iterator.next();
        assertThat(iterator.hasNext(), is(false));
        assertThat(file.getName(), is("file"));
        assertThat(file.contentAsString(), is("modified"));
    }

    @Test
    public void ofSourceRevision() throws Exception {
        sampleRepo.init();
        sampleRepo.git("checkout", "-b", "dev");
        SCMSource source = new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true);
        SCMRevision revision = source.fetch(new SCMHead("dev"), null);
        sampleRepo.write("file", "modified");
        sampleRepo.git("commit", "--all", "--message=dev");
        SCMFileSystem fs = SCMFileSystem.of(source, new SCMHead("dev"), revision);
        assertThat(fs, notNullValue());
        assertThat(fs.getRoot(), notNullValue());
        Iterable<SCMFile> children = fs.getRoot().children();
        Iterator<SCMFile> iterator = children.iterator();
        assertThat(iterator.hasNext(), is(true));
        SCMFile file = iterator.next();
        assertThat(iterator.hasNext(), is(false));
        assertThat(file.getName(), is("file"));
        assertThat(file.contentAsString(), is(""));
    }

    @Test
    public void testGetRoot() throws Exception {
        File gitDir = new File(".");
        GitClient client = Git.with(TaskListener.NULL, new EnvVars()).in(gitDir).using(gitImplName).getClient();
        ObjectId head = client.revParse("HEAD");
        AbstractGitSCMSource.SCMRevisionImpl rev = new AbstractGitSCMSource.SCMRevisionImpl(new SCMHead("origin"), head.getName());
        GitSCMFileSystem fileSystem = new GitSCMFileSystem(client, "origin", head.getName(), rev);
        SCMFile root = fileSystem.getRoot();
        // assertFalse(root.isFile()); // IllegalArgumentException
        // assertTrue(root.isDirectory()); // IllegalArgumentException
        assertTrue(root.isRoot());
        // assertTrue(root.exists());  // IllegalArgumentException
    }

    @Test
    public void lastModified_Smokes() throws Exception {
        sampleRepo.init();
        sampleRepo.git("checkout", "-b", "dev");
        SCMSource source = new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true);
        SCMRevision revision = source.fetch(new SCMHead("dev"), null);
        sampleRepo.write("file", "modified");
        sampleRepo.git("commit", "--all", "--message=dev");
        SCMFileSystem fs = SCMFileSystem.of(source, new SCMHead("dev"), revision);
        assertThat(fs.lastModified(), allOf(greaterThanOrEqualTo(System.currentTimeMillis() - 2000), lessThanOrEqualTo(System.currentTimeMillis() + 2000)));
        SCMFile file = fs.getRoot().child("file");
        assertThat(file.lastModified(), allOf(greaterThanOrEqualTo(System.currentTimeMillis() - 2000),
                lessThanOrEqualTo(System.currentTimeMillis() + 2000)));
    }

    @Test
    public void directoryTraversal() throws Exception {
        sampleRepo.init();
        sampleRepo.git("checkout", "-b", "dev");
        sampleRepo.mkdirs("dir/subdir");
        sampleRepo.git("mv", "file", "dir/subdir/file");
        sampleRepo.write("dir/subdir/file", "modified");
        sampleRepo.git("commit", "--all", "--message=dev");
        SCMSource source = new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true);
        SCMFileSystem fs = SCMFileSystem.of(source, new SCMHead("dev"));
        assertThat(fs, notNullValue());
        assertThat(fs.getRoot(), notNullValue());
        Iterable<SCMFile> children = fs.getRoot().children();
        Iterator<SCMFile> iterator = children.iterator();
        assertThat(iterator.hasNext(), is(true));
        SCMFile dir = iterator.next();
        assertThat(iterator.hasNext(), is(false));
        assertThat(dir.getName(), is("dir"));
        assertThat(dir.getType(), is(SCMFile.Type.DIRECTORY));
        children = dir.children();
        iterator = children.iterator();
        assertThat(iterator.hasNext(), is(true));
        SCMFile subdir = iterator.next();
        assertThat(iterator.hasNext(), is(false));
        assertThat(subdir.getName(), is("subdir"));
        assertThat(subdir.getType(), is(SCMFile.Type.DIRECTORY));
        children = subdir.children();
        iterator = children.iterator();
        assertThat(iterator.hasNext(), is(true));
        SCMFile file = iterator.next();
        assertThat(iterator.hasNext(), is(false));
        assertThat(file.getName(), is("file"));
        assertThat(file.contentAsString(), is("modified"));
    }

    @Test
    public void mixedContent() throws Exception {
        sampleRepo.init();
        sampleRepo.git("checkout", "-b", "dev");
        sampleRepo.mkdirs("dir/subdir");
        sampleRepo.write("file", "modified");
        sampleRepo.write("file2", "new");
        sampleRepo.git("add", "file2");
        sampleRepo.write("dir/file3", "modified");
        sampleRepo.git("add", "file", "dir/file3");
        sampleRepo.git("commit", "--all", "--message=dev");
        SCMSource source = new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true);
        SCMFileSystem fs = SCMFileSystem.of(source, new SCMHead("dev"));
        assertThat(fs, notNullValue());
        assertThat(fs.getRoot(), notNullValue());
        Iterable<SCMFile> children = fs.getRoot().children();
        Set<String> names = new TreeSet<String>();
        SCMFile file = null;
        SCMFile file2 = null;
        SCMFile dir = null;
        for (SCMFile f: children) {
            names.add(f.getName());
            if ("file".equals(f.getName())) {
                file = f;
            } else if ("file2".equals(f.getName())) {
                file2 = f;
            } else if ("dir".equals(f.getName())) {
                dir = f;
            }
        }
        assertThat(names, containsInAnyOrder(is("file"), is("file2"), is("dir")));
        assertThat(file.getType(), is(SCMFile.Type.REGULAR_FILE));
        assertThat(file2.getType(), is(SCMFile.Type.REGULAR_FILE));
        assertThat(dir.getType(), is(SCMFile.Type.DIRECTORY));
        assertThat(file.contentAsString(), is("modified"));
        assertThat(file2.contentAsString(), is("new"));
    }

}
