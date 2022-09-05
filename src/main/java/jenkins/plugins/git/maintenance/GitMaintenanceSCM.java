package jenkins.plugins.git.maintenance;

import jenkins.model.Jenkins;
import jenkins.plugins.git.AbstractGitSCMSource;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * GitMaintenanceSCM is responsible for fetching all caches along with locks on Jenkins controller. It extends {@link AbstractGitSCMSource}.
 *
 * @author Hrushikesh Rao
 */
public class GitMaintenanceSCM extends AbstractGitSCMSource {

    String remote;

    private static Logger LOGGER = Logger.getLogger(GitMaintenanceSCM.class.getName());
    protected GitMaintenanceSCM(String remote){
        this.remote = remote;
    }

    /**
     * Stores the File object and lock for cache.
     */
    static class Cache {

        File cache;
        Lock lock;
        Cache(File cache, Lock lock){
            this.cache = cache;
            this.lock = lock;
        }

        /**
         * Return the File object of a cache.
         * @return File object of a cache.
         */
        public File getCacheFile(){
            return cache;
        }

        /**
         * Returns the lock for a cache.
         *
         * @return lock for a cache.
         */
        public Lock getLock(){
            return lock;
        }

    }
    @Override
    public String getCredentialsId() {
        return null;
    }

    @Override
    public String getRemote() {
        return remote;
    }

    /**
     * Returns a list of {@link Cache}.
     * @return A list of {@link Cache}.
     */
    public static List<Cache> getCaches(){
            Jenkins jenkins = Jenkins.getInstanceOrNull();

            List<Cache> caches = new ArrayList<>();
            if(jenkins == null){
                LOGGER.log(Level.WARNING,"Internal error. Couldn't get instance of Jenkins.");
                return caches;
            }

            for (String cacheEntry : getCacheEntries()) {
                File cacheDir = getCacheDir(cacheEntry,false);
                Lock cacheLock = getCacheLock(cacheEntry);
                LOGGER.log(Level.FINE,"Cache Entry " + cacheEntry);
                caches.add(new Cache(cacheDir,cacheLock));
            }

            return caches;
    }

    String getCacheEntryForTest(){
        return getCacheEntry();
    }
}
