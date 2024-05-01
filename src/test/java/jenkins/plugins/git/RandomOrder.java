package jenkins.plugins.git;

import org.junit.runner.manipulation.Ordering;

import java.util.Random;

public class RandomOrder implements Ordering.Factory {
    private static final long SEED = new Random().nextLong();
    @Override
    public Ordering create(Ordering.Context context) {
        return Ordering.shuffledBy(new Random(SEED));
    }
}
