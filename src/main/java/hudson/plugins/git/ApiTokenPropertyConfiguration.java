package hudson.plugins.git;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.model.PersistentDescriptor;
import hudson.util.HttpResponses;
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import jenkins.model.Jenkins;
import net.jcip.annotations.GuardedBy;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.interceptor.RequirePOST;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;


@Extension
@Restricted(NoExternalUse.class)
@Symbol("apiTokenProperty")
public class ApiTokenPropertyConfiguration extends GlobalConfiguration implements PersistentDescriptor {

    private static final Logger LOGGER = Logger.getLogger(ApiTokenPropertyConfiguration.class.getName());
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String HASH_ALGORITHM = "SHA-256";

    @GuardedBy("this")
    private final List<HashedApiToken> apiTokens;

    public ApiTokenPropertyConfiguration() {
        this.apiTokens = new ArrayList<>();
    }

    public static ApiTokenPropertyConfiguration get() {
        return GlobalConfiguration.all().get(ApiTokenPropertyConfiguration.class);
    }

    @NonNull
    @Override
    public GlobalConfigurationCategory getCategory() {
        return GlobalConfigurationCategory.get(GlobalConfigurationCategory.Security.class);
    }

    @RequirePOST
    public HttpResponse doGenerate(StaplerRequest req) {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        String apiTokenName = req.getParameter("apiTokenName");
        JSONObject json = this.generateApiToken(apiTokenName);
        save();

        return HttpResponses.okJSON(json);
    }

    public JSONObject generateApiToken(@NonNull String name) {
        byte[] random = new byte[16];
        RANDOM.nextBytes(random);

        String plainTextApiToken = Util.toHexString(random);
        assert plainTextApiToken.length() == 32;

        String apiTokenValueHashed = Util.toHexString(hashedBytes(plainTextApiToken.getBytes(StandardCharsets.US_ASCII)));
        HashedApiToken apiToken = new HashedApiToken(name, apiTokenValueHashed);

        synchronized (this) {
            this.apiTokens.add(apiToken);
        }

        JSONObject json = new JSONObject();
        json.put("uuid", apiToken.getUuid());
        json.put("name", apiToken.getName());
        json.put("value", plainTextApiToken);

        return json;
    }

    @NonNull
    private static byte[] hashedBytes(byte[] tokenBytes) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(HASH_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("There is no " + HASH_ALGORITHM + " available in this system", e);
        }
        return digest.digest(tokenBytes);
    }

    @RequirePOST
    public HttpResponse doRevoke(StaplerRequest req) {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        String apiTokenUuid = req.getParameter("apiTokenUuid");
        if (StringUtils.isBlank(apiTokenUuid)) {
            return HttpResponses.errorWithoutStack(400, "API token UUID cannot be empty");
        }

        synchronized (this) {
            this.apiTokens.removeIf(apiToken -> apiToken.getUuid().equals(apiTokenUuid));
        }
        save();

        return HttpResponses.ok();
    }

    public synchronized Collection<HashedApiToken> getApiTokens() {
        return Collections.unmodifiableList(new ArrayList<>(this.apiTokens));
    }

    public boolean isValidApiToken(String plainApiToken) {
        if (StringUtils.isBlank(plainApiToken)) {
            return false;
        }

        return this.hasMatchingApiToken(plainApiToken);
    }

    public synchronized boolean hasMatchingApiToken(@NonNull String plainApiToken) {
        byte[] hash = hashedBytes(plainApiToken.getBytes(StandardCharsets.US_ASCII));
        return this.apiTokens.stream().anyMatch(apiToken -> apiToken.match(hash));
    }

    public static class HashedApiToken implements Serializable {

        private static final long serialVersionUID = 1L;

        private final String uuid;
        private final String name;
        private final String hash;

        private HashedApiToken(String name, String hash) {
            this.uuid = UUID.randomUUID().toString();
            this.name = name;
            this.hash = hash;
        }

        private HashedApiToken(String uuid, String name, String hash) {
            this.uuid = uuid;
            this.name = name;
            this.hash = hash;
        }

        public String getUuid() {
            return uuid;
        }

        public String getName() {
            return name;
        }

        public String getHash() {
            return hash;
        }

        private boolean match(byte[] hashedBytes) {
            byte[] hashFromHex;
            try {
                hashFromHex = Util.fromHexString(hash);
            } catch (NumberFormatException e) {
                LOGGER.log(Level.INFO, "The API token with name=[{0}] is not in hex-format and so cannot be used", name);
                return false;
            }

            return MessageDigest.isEqual(hashFromHex, hashedBytes);
        }
    }
}
