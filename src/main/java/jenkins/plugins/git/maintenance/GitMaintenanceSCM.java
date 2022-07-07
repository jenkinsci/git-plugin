package jenkins.plugins.git.maintenance;

import jenkins.model.Jenkins;
import jenkins.plugins.git.AbstractGitSCMSource;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GitMaintenanceSCM extends AbstractGitSCMSource {

    String remote;

    private static Logger LOGGER = Logger.getLogger(GitMaintenanceSCM.class.getName());
    protected GitMaintenanceSCM(String remote){
        this.remote = remote;
    }

    static class Cache {

        File cache;
        Lock lock;
        Cache(File cache, Lock lock){
            this.cache = cache;
            this.lock = lock;
        }

        public File getCacheFile(){
            return cache;
        }

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

    public static List<Cache> getCaches(){
            Jenkins jenkins = Jenkins.getInstanceOrNull();

            if(jenkins == null){
                // Throw error;
            }

            List<Cache> caches = new ArrayList<>();
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
