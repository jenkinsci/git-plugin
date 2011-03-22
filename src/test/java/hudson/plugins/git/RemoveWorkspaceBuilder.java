package hudson.plugins.git;

import hudson.Launcher;
import hudson.FilePath.FileCallable;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.remoting.VirtualChannel;
import hudson.tasks.Builder;

import java.io.File;
import java.io.IOException;

public final class RemoveWorkspaceBuilder extends Builder {
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException
    {
        build.getWorkspace().act(new FileCallable<Void>() {
            public Void invoke(File f, VirtualChannel channel) throws IOException, InterruptedException
            {
                delete(f);
                return null;
            }
            private void delete(File f) throws IOException {
                if (f.isDirectory()) {
                    for (File child : f.listFiles()) {
                        delete(child);
                    }
                }
                f.delete();
            }
        });
        return true;
    }
}