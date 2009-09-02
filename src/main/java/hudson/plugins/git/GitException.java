package hudson.plugins.git;

public class GitException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public GitException() {
		super();
	}

	public GitException(String message) {
		super(message);
	}

	public GitException(Throwable cause) {
		super(cause);
	}

	public GitException(String message, Throwable cause) {
		super(message, cause);
	}
}
