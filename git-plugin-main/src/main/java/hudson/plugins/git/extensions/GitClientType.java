package hudson.plugins.git.extensions;

/**
* @author <a href="mailto:nicolas.deloof@gmail.com">Nicolas De Loof</a>
*/
public enum GitClientType {
    JGIT {
        @Override
        public GitClientType combine(GitClientType c) throws GitClientConflictException {
            if (c == GITCLI) throw new GitClientConflictException();
            return this;
        }
    }, GITCLI {
        @Override
        public GitClientType combine(GitClientType c) throws GitClientConflictException {
            if (c == JGIT) throw new GitClientConflictException();
            return this;
        }
    }, ANY {
        @Override
        public GitClientType combine(GitClientType c) {
            return c;
        }
    };

    public abstract GitClientType combine(GitClientType c) throws GitClientConflictException;
}
