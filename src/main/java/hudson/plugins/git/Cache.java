package hudson.plugins.git;

import hudson.FilePath;
import hudson.Util;
import hudson.model.Node;

import jenkins.model.Jenkins;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class Cache {

    /**
     * Keep one lock per cache directory. Lazy populated, but never purge, except on restart.
     */
    private static final ConcurrentMap<String, ConcurrentMap<String, Lock>> cacheLocks = new ConcurrentHashMap<>();

    private static final Logger LOGGER = Logger.getLogger(Cache.class.getName());

    private static String getCacheEntry(String remoteURL) {
        return "git-" + Util.getDigestOf(remoteURL);
    }

    private static String getNodeEntry(Node node) {
        return Util.getDigestOf(node.getNodeName().isEmpty() ? node.getNodeName() : "master");
    }

    public static FilePath getCacheDir(Node node, String remoteURL)
            throws IOException, InterruptedException {
        String cacheEntry = getCacheEntry(remoteURL);
        FilePath cacheDir = new FilePath(new FilePath(node.getRootPath(), "caches"), cacheEntry);
        cacheDir.mkdirs(); // ensure it exists
        return cacheDir;
    }

    public static File getCacheDir(String remoteURL) {
        Jenkins jenkins = Jenkins.getInstance();
        // TODO: Remove redundant null check after update to Jenkins 2.60 core
        if (jenkins == null) {
            LOGGER.severe("Jenkins instance is null in Cache.getCacheDir");
            return null;
        }
        File cacheDir = new File(new File(jenkins.getRootDir(), "caches"), getCacheEntry(remoteURL));
        if (!cacheDir.isDirectory()) {
            boolean ok = cacheDir.mkdirs();
            if (!ok) {
                LOGGER.log(Level.WARNING, "Failed mkdirs of {0}", cacheDir);
            }
        }
        return cacheDir;
    }

    // Get node lock
    private static Lock getCacheLock(Node node, String cacheEntry) {
        ConcurrentMap<String, Lock> cacheNodeLocks;
        Lock cacheLock;
        String nodeEntry = getNodeEntry(node);
        while (null == (cacheNodeLocks = cacheLocks.get(nodeEntry))) {
            cacheLocks.putIfAbsent(nodeEntry, new ConcurrentHashMap<String, Lock>());
        }
        while (null == (cacheLock = cacheNodeLocks.get(cacheEntry))) {
            cacheNodeLocks.putIfAbsent(cacheEntry, new ReentrantLock());
        }
        return cacheLock;
    }

    // Lock/unlock master
    public static void lock(String remoteURL) {
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins != null) {
            lock(jenkins, remoteURL);
        } else {
            LOGGER.severe("Jenkins instance is null in Cache.lock");
        }
    }

    // TODO: Remove redundant null check after update to Jenkins 2.60 core
    @SuppressFBWarnings(value="NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE", justification="Jenkins.getInstance() is not null")
    public static void unlock(String remoteURL) {
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins != null) {
            unlock(Jenkins.getInstance(), remoteURL);
        } else {
            LOGGER.severe("Jenkins instance is null in Cache.unlock");
        }
    }

    // Lock/unlock other nodes
    public static void lock(Node node, String remoteURL) {
        getCacheLock(node, getCacheEntry(remoteURL)).lock();
    }

    public static void unlock(Node node, String remoteURL) {
        getCacheLock(node, getCacheEntry(remoteURL)).unlock();
    }
}
