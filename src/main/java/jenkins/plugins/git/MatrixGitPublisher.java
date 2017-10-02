package jenkins.plugins.git;

import hudson.Extension;
import hudson.Launcher;
import hudson.matrix.MatrixAggregatable;
import hudson.matrix.MatrixAggregator;
import hudson.matrix.MatrixBuild;
import hudson.model.BuildListener;
import hudson.plugins.git.GitPublisher;

import java.io.IOException;

@Extension
public class MatrixGitPublisher implements MatrixAggregatable {
    /**
     * For a matrix project, push should only happen once.
     */
    public MatrixAggregator createAggregator(MatrixBuild build, Launcher launcher, BuildListener listener) {
        return new MatrixAggregator(build,launcher,listener) {
            @Override
            public boolean endBuild() throws InterruptedException, IOException {
                GitPublisher publisher = build.getParent().getPublishersList().get(GitPublisher.class);
                if (publisher != null) {
                    return publisher.perform(build, launcher, listener);
                }
                return true;
            }
        };
    }
}
