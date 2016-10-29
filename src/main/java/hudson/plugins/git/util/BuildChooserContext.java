package hudson.plugins.git.util;

import hudson.EnvVars;
import hudson.model.Job;
import hudson.model.Run;
import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;

import java.io.IOException;
import java.io.Serializable;

/**
 * Provides access to the model object on the master for {@link BuildChooser}.
 *
 * <p>
 * {@link BuildChooser} runs on a node that has the workspace, which means it can run on a slave.
 * This interface provides access for {@link BuildChooser} to send a closure to the master and execute code there.
 *
 * @author Kohsuke Kawaguchi
 */
public interface BuildChooserContext {
    <T> T actOnBuild(ContextCallable<Run<?,?>,T> callable) throws IOException,InterruptedException;
    <T> T actOnProject(ContextCallable<Job<?,?>,T> callable) throws IOException,InterruptedException;

    Run<?,?> getBuild();

    EnvVars getEnvironment();

    public static interface ContextCallable<P,T> extends Serializable {
        /**
         * Performs the computational task on the node where the data is located.
         *
         * <p>
         * All the exceptions are forwarded to the caller.
         *
         * @param param
         *      Context object.
         * @param channel
         *      The "back pointer" of the {@link Channel} that represents the communication
         *      with the node from where the code was sent.
         * @return result of dispatching the method on the channel, with param parameters
         * @throws IOException on I/O error
         * @throws InterruptedException when interrupted
         */
        T invoke(P param, VirtualChannel channel) throws IOException, InterruptedException;
    }
}
