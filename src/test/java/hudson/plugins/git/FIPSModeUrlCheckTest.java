package hudson.plugins.git;

import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.github.sparsick.testcontainers.gitserver.GitServerVersions;
import com.github.sparsick.testcontainers.gitserver.http.GitHttpServerContainer;
import com.github.sparsick.testcontainers.gitserver.plain.GitServerContainer;
import com.github.sparsick.testcontainers.gitserver.plain.SshIdentity;
import hudson.ExtensionList;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.util.FormValidation;
import jenkins.branch.MultiBranchProject;
import jenkins.plugins.git.GitSCMSource;
import jenkins.security.FIPS140;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.StoredConfig;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.jetbrains.annotations.NotNull;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.FlagRule;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

public class FIPSModeUrlCheckTest {

    @ClassRule
    public static final FlagRule<String> FIPS_FLAG = FlagRule.systemProperty(FIPS140.class.getName() + ".COMPLIANCE", "true");

    @Rule public JenkinsRule r = new JenkinsRule();

    @Rule
    public TemporaryFolder directory = new TemporaryFolder();

    @Test
    public void testGitSCMSourceCheck() throws Throwable {
        SystemCredentialsProvider.getInstance().getCredentials().add(new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "mycreds", null, "jenkins", "s3cr3t"));
        SystemCredentialsProvider.getInstance().save();
        MultiBranchProject<?,?> mbp = r.createProject(WorkflowMultiBranchProject.class, "mbp");
        GitSCMSource.DescriptorImpl descriptor = ExtensionList.lookupSingleton(GitSCMSource.DescriptorImpl.class);

        {
            // http with creds rejected
            FormValidation validation = descriptor.doCheckRemote(mbp, "mycreds", "http://github.com/foo/beer");
            assertThat(validation.kind, is(FormValidation.Kind.ERROR));
            assertThat(validation.getMessage(), containsString(Messages.git_fips_url_notsecured()));
        }

        {
            // https with creds ok
            FormValidation validation = descriptor.doCheckRemote(mbp, "mycreds", "https://github.com/foo/vegemite");
            assertThat(validation.kind, is(FormValidation.Kind.OK));
        }

        {
            // ssh with creds ok
            FormValidation validation = descriptor.doCheckRemote(mbp, "mycreds", "git@github.com:foo/wine.git");
            assertThat(validation.kind, is(FormValidation.Kind.OK));
        }


        {
            // http without creds ok
            FormValidation validation = descriptor.doCheckRemote(mbp, null, "http://github.com/foo/cheese");
            assertThat(validation.kind, is(FormValidation.Kind.OK));
        }
    }

    @Test
    public void testUserRemoteConfigCheck() throws Throwable {
        SystemCredentialsProvider.getInstance().getCredentials().add(new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "mycreds", null, "jenkins", "s3cr3t"));
        SystemCredentialsProvider.getInstance().save();
        FreeStyleProject p = r.createProject(FreeStyleProject.class, "mbp");
        UserRemoteConfig.DescriptorImpl descriptor = ExtensionList.lookupSingleton(UserRemoteConfig.DescriptorImpl.class);

        {
            // http with credentials rejected
            FormValidation validation = descriptor.doCheckUrl(p, "mycreds", "http://github.com/olamy/beer");
            assertThat(validation.kind, is(FormValidation.Kind.ERROR));
            assertThat(validation.getMessage(), containsString(Messages.git_fips_url_notsecured()));
        }
        {
            // https without credentials all good
            FormValidation validation = descriptor.doCheckUrl(p, null, "https://github.com/jenkinsci/git-plugin");
            assertThat(validation.kind, is(FormValidation.Kind.OK));
        }


        {
            // ssh with credentials all good
            try (GitServerContainer containerUnderTest = new GitServerContainer(
                    GitServerVersions.V2_45.getDockerImageName())
                    .withGitRepo("someRepo")
                    .withSshKeyAuth()) {
                containerUnderTest.start();
                SshIdentity sshIdentity = containerUnderTest.getSshClientIdentity();
                BasicSSHUserPrivateKey sshUserPrivateKey = getBasicSSHUserPrivateKey(sshIdentity);
                SystemCredentialsProvider.getInstance().getCredentials().add(sshUserPrivateKey);
                String repoUrl = containerUnderTest
                        .getGitRepoURIAsSSH()
                        .toString();
                // ssh://git@localhost:33011/srv/git/someRepo.git
                // we don't want the user part of the uri or jgit will use this user
                // and we want to be sure to test our implementation with dynamic user
                repoUrl = StringUtils.remove(repoUrl, "git@");
                FormValidation validation = descriptor.doCheckUrl(p, sshUserPrivateKey.getId(), repoUrl);
                assertThat(validation.kind, is(FormValidation.Kind.OK));
            }
        }

        {
            // http without credentials all good
            try (GitHttpServerContainer containerUnderTest =
                         new GitHttpServerContainer(GitServerVersions.V2_43.getDockerImageName())) {
                containerUnderTest.start();
                // no TLS is fine without credentials
                FormValidation validation = descriptor.doCheckUrl(p, null, containerUnderTest.getGitRepoURIAsHttp().toString());
                assertThat(validation.kind, is(FormValidation.Kind.OK));
            }
        }
    }


    private static BasicSSHUserPrivateKey getBasicSSHUserPrivateKey(SshIdentity sshIdentity) {
        BasicSSHUserPrivateKey.PrivateKeySource privateKeySource = new BasicSSHUserPrivateKey.PrivateKeySource() {
            @NotNull
            @Override
            public List<String> getPrivateKeys() {
                return List.of(new String(sshIdentity.getPrivateKey()));
            }
        };
        return new BasicSSHUserPrivateKey(
                CredentialsScope.GLOBAL,
                "some-id",
                "git",
                privateKeySource,
                new String(sshIdentity.getPassphrase()),
                "description");
    }

    @Test
    public void gitStepTLSCheck() throws Throwable {
        WorkflowJob p = r.createProject(WorkflowJob.class, "some project");
        {
            // http with creds rejected
            p.setDefinition(new CpsFlowDefinition(
                    "node {\n" +
                            "    dir('foo') {\n" +
                            "        git url: 'http://foo.com/beer.git', credentialsId: 'yup'\n" +
                            "    }\n" +
                            "}", true));
            WorkflowRun b = r.buildAndAssertStatus(Result.FAILURE, p);
            r.assertLogContains(Messages.git_fips_url_notsecured(), b);
        }

        {
            // http without creds not rejected
            try (GitHttpServerContainer containerUnderTest =
                         new GitHttpServerContainer(GitServerVersions.V2_43.getDockerImageName())){
                containerUnderTest.start();
                // need to have at least on revision to avoid build failure
                File tmp = directory.newFolder();
                Git git = Git.cloneRepository()
                        .setURI(containerUnderTest.getGitRepoURIAsHttp().toString())
                        .setDirectory(tmp)
                        .call();
                StoredConfig storedConfig =  git.getRepository().getConfig();
                storedConfig.setBoolean("commit", null,"gpgsign", false);
                storedConfig.setBoolean("tag", null, "gpgSign", false);
                storedConfig.save();
                Files.writeString(new File(tmp, "foo.txt").toPath(), "nothing too see here");
                git.add().addFilepattern("foo.txt").call();
                git.commit().setMessage("add foo").call();
                git.push().call();

                // http with creds rejected
                p.setDefinition(new CpsFlowDefinition(
                        "node {\n" +
                                "    dir('foo') {\n" +
                                "        git url: '" + containerUnderTest.getGitRepoURIAsHttp() + "', changelog: false\n" +
                                "    }\n" +
                                "}", true));
                WorkflowRun b = r.buildAndAssertStatus(Result.SUCCESS, p);
            }
        }
    }

}
