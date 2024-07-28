package jenkins.plugins.git.junit.jupiter;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import jenkins.plugins.git.GitSampleRepoRule;

/**
 * A Junit5 extension to use {@link GitSampleRepoRule} with Junit5
 * See {@link WithGitSampleRepo} for details
 */
public class GitSampleRepoExtension implements ParameterResolver, AfterEachCallback {

    private static final String KEY = "git-sample-repo";
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(GitSampleRepoExtension.class);

    @Override
    public void afterEach(ExtensionContext context) {
        var rule = context.getStore(NAMESPACE).remove(KEY, GitSampleRepoRule.class);
        if (rule != null) {
            rule.after();
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType().equals(GitSampleRepoRule.class);
    }

    @Override
    public GitSampleRepoRule resolveParameter(ParameterContext parameterContext, ExtensionContext context) {
        var rule = context.getStore(NAMESPACE).getOrComputeIfAbsent(KEY, key -> new GitSampleRepoRule(), GitSampleRepoRule.class);
        if (rule != null) {
            try {
                rule.before();
            } catch (Throwable t) {
                throw new ParameterResolutionException(t.getMessage(), t);
            }
        }
        return rule;
    }
}
