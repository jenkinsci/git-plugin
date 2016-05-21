package hudson.plugins.git;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.ConversionException;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.reflection.SerializableConverter;
import com.thoughtworks.xstream.core.util.CustomObjectInputStream;
import com.thoughtworks.xstream.core.util.HierarchicalStreams;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.mapper.Mapper;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.transport.RemoteConfig;

import java.io.*;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Remote config converter that handles unmarshaling legacy externalization of
 * JGit RemoteConfig class.
 */
public class RemoteConfigConverter implements Converter {

    /**
     * Remote config proxy
     */
    private static class RemoteConfigProxy extends Config implements
            Externalizable {

        private static final String KEY_URL = "url";

        private static final String KEY_FETCH = "fetch";

        private static final String KEY_PUSH = "push";

        private static final String KEY_UPLOADPACK = "uploadpack";

        private static final String KEY_RECEIVEPACK = "receivepack";

        private static final String KEY_TAGOPT = "tagopt";

        private String name;
        private String[] uris;
        private String[] fetch;
        private String[] push;
        private String uploadpack;
        private String receivepack;
        private String tagopt;

        /**
         * Create remote config proxy
         */
        public RemoteConfigProxy() {
            uris = new String[0];
            fetch = new String[0];
            push = new String[0];
            uploadpack = "git-upload-pack";
            receivepack = "git-receive-pack";
        }

        public String getString(String section, String subsection, String name) {
            if (KEY_UPLOADPACK.equals(name))
                return uploadpack;
            if (KEY_RECEIVEPACK.equals(name))
                return receivepack;
            if (KEY_TAGOPT.equals(name))
                return tagopt;
            return super.getString(section, subsection, name);
        }

        public String[] getStringList(String section, String subsection,
                String name) {
            if (KEY_URL.equals(name))
                return uris;
            if (KEY_FETCH.equals(name))
                return fetch;
            if (KEY_PUSH.equals(name))
                return push;
            return super.getStringList(section, subsection, name);
        }

        private void fromMap(Map<String, Collection<String>> map) {
            for (Entry<String, Collection<String>> entry : map.entrySet()) {
                String key = entry.getKey();
                Collection<String> values = entry.getValue();
                if (null != key)
                    switch (key) {
                    case KEY_URL:
                        uris = values.toArray(new String[values.size()]);
                        break;
                    case KEY_FETCH:
                        fetch = values.toArray(new String[values.size()]);
                        break;
                    case KEY_PUSH:
                        push = values.toArray(new String[values.size()]);
                        break;
                    case KEY_UPLOADPACK:
                        for (String value : values)
                            uploadpack = value;
                        break;
                    case KEY_RECEIVEPACK:
                        for (String value : values)
                            receivepack = value;
                        break;
                    case KEY_TAGOPT:
                        for (String value : values)
                            tagopt = value;
                        break;
                    default:
                        break;
                }
            }
        }

        public void readExternal(ObjectInput in) throws IOException,
                ClassNotFoundException {
            name = in.readUTF();
            final int items = in.readInt();
            Map<String, Collection<String>> map = new HashMap<>();
            for (int i = 0; i < items; i++) {
                String key = in.readUTF();
                String value = in.readUTF();
                Collection<String> values = map.get(key);
                if (values == null) {
                    values = new ArrayList<>();
                    map.put(key, values);
                }
                values.add(value);
            }
            fromMap(map);
        }

        public void writeExternal(ObjectOutput out) throws IOException {
            throw new IOException("writeExternal not supported");
        }

        /**
         * @return remote config
         * @throws URISyntaxException
         */
        public RemoteConfig toRemote() throws URISyntaxException {
            return new RemoteConfig(this, name);
        }
    }

    private final Mapper mapper;
    private final SerializableConverter converter;

    /**
     * Create remote config converter
     * 
     * @param xStream
     */
    public RemoteConfigConverter(XStream xStream) {
        mapper = xStream.getMapper();
        converter = new SerializableConverter(mapper,
                xStream.getReflectionProvider());
    }

    public boolean canConvert(@SuppressWarnings("rawtypes") Class type) {
        return RemoteConfig.class == type;
    }

    public void marshal(Object source, HierarchicalStreamWriter writer,
            MarshallingContext context) {
        converter.marshal(source, writer, context);
    }

    /**
     * Is the current reader node a legacy node?
     * 
     * @param reader
     * @param context
     * @return true if legacy, false otherwise
     */
    protected boolean isLegacyNode(HierarchicalStreamReader reader,
            final UnmarshallingContext context) {
        return reader.getNodeName().startsWith("org.spearce");
    }

    /**
     * Legacy unmarshalling of remote config
     * 
     * @param reader
     * @param context
     * @return remote config
     */
    protected Object legacyUnmarshal(final HierarchicalStreamReader reader,
            final UnmarshallingContext context) {
        final RemoteConfigProxy proxy = new RemoteConfigProxy();
        CustomObjectInputStream.StreamCallback callback = new CustomObjectInputStream.StreamCallback() {
            public Object readFromStream() {
                reader.moveDown();
                @SuppressWarnings("rawtypes")
                Class type = HierarchicalStreams.readClassType(reader, mapper);
                Object streamItem = context.convertAnother(proxy, type);
                reader.moveUp();
                return streamItem;
            }

            @SuppressWarnings("rawtypes")
            public Map readFieldsFromStream() {
                throw new UnsupportedOperationException();
            }

            public void defaultReadObject() {
                throw new UnsupportedOperationException();
            }

            public void registerValidation(ObjectInputValidation validation,
                    int priority) throws NotActiveException {
                throw new NotActiveException();
            }

            public void close() {
                throw new UnsupportedOperationException();
            }
        };
        try {
            CustomObjectInputStream objectInput = CustomObjectInputStream
                    .getInstance(context, callback);
            proxy.readExternal(objectInput);
            objectInput.popCallback();
            return proxy.toRemote();
        } catch (IOException e) {
            throw new ConversionException("Unmarshal failed", e);
        } catch (ClassNotFoundException e) {
            throw new ConversionException("Unmarshal failed", e);
        } catch (URISyntaxException e) {
            throw new ConversionException("Unmarshal failed", e);
        }
    }

    public Object unmarshal(final HierarchicalStreamReader reader,
            final UnmarshallingContext context) {
        if (isLegacyNode(reader, context))
            return legacyUnmarshal(reader, context);
        return converter.unmarshal(reader, context);
    }
}
