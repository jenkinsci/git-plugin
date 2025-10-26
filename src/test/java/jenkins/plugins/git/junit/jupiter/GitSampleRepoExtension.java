package jenkins.plugins.git.junit.jupiter;

import edu.umd.cs.findbugs.annotations.NonNull;
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

    private static final String KEY = "git-sample-repo-";
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(GitSampleRepoExtension.class);
    private static int counter = 0;

    @Override
    public void afterEach(@NonNull ExtensionContext context) {
        for (int i = counter; i >= 0; i--) {
            var rule = context.getStore(NAMESPACE).remove(KEY + i, GitSampleRepoRule.class);
            if (rule != null) {
                rule.after();
            }
        }
    }

    @Override
    public boolean supportsParameter(@NonNull ParameterContext parameterContext, @NonNull ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType().equals(GitSampleRepoRule.class);
    }

    @Override
    public GitSampleRepoRule resolveParameter(@NonNull ParameterContext parameterContext, @NonNull ExtensionContext context) {
        // TODO: Replace with non-deprecated method once consumers upgraded to JUnit 6.x
        var rule = context.getStore(NAMESPACE).getOrComputeIfAbsent(KEY + counter++, key -> new GitSampleRepoRule(), GitSampleRepoRule.class);
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
