package jenkins.plugins.git.maintenance;

import jenkins.model.Jenkins;
import jenkins.plugins.git.AbstractGitSCMSource;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;

public class GitMaintenanceSCM extends AbstractGitSCMSource {

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
        return null;
    }

    public static List<Cache> getCaches(){
            Jenkins jenkins = Jenkins.getInstanceOrNull();

            if(jenkins == null){
                // Throw error;
            }

            List<Cache> caches = new ArrayList<>();
            for (String cacheEntry : getCacheEntries()) {
                File cacheDir = getCacheDir(cacheEntry);
                Lock cacheLock = getCacheLock(cacheEntry);
                caches.add(new Cache(cacheDir,cacheLock));
            }

            return caches;
    }
}
