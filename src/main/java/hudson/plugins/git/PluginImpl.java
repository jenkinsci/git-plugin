package hudson.plugins.git;

import hudson.Plugin;

import java.io.IOException;

/**
 * Plugin entry point.
 *
 * @author Nigel Magnay
 * @plugin
 */
public class PluginImpl extends Plugin {
    @Override
    public void postInitialize() throws IOException {
        GitTool.onLoaded();
    }
}
