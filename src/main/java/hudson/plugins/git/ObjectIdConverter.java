package hudson.plugins.git;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.core.util.Base64Encoder;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Object id converter. This is required for legacy support for originally
 * serializing an ObjectId as a byte[]. The new format supports marshalling as a
 * standard lower-case SHA1 hexadecimal string but unmarshalling both byte[] and
 * String types.
 */
public class ObjectIdConverter implements Converter {

    private final Base64Encoder base64;

    /**
     * Create ObjectId converter
     */
    public ObjectIdConverter() {
        base64 = new Base64Encoder();
    }

    public boolean canConvert(@SuppressWarnings("rawtypes") Class type) {
        return ObjectId.class == type;
    }

    public void marshal(Object source, HierarchicalStreamWriter writer,
            MarshallingContext context) {
        writer.setValue(((ObjectId) source).name());
    }

    /**
     * Is the current reader node a legacy node?
     * 
     * @param reader
     * @param context
     * @return true if legacy, false otherwise
     */
    protected boolean isLegacyNode(HierarchicalStreamReader reader,
            UnmarshallingContext context) {
        return reader.hasMoreChildren()
                && "byte-array".equals(reader.peekNextChild());
    }

    /**
     * Legacy unmarshalling of object id
     * 
     * @param reader
     * @param context
     * @return object id
     */
    protected Object legacyUnmarshal(HierarchicalStreamReader reader,
            UnmarshallingContext context) {
        reader.moveDown();
        ObjectId sha1 = ObjectId.fromRaw(base64.decode(reader.getValue()));
        reader.moveUp();
        return sha1;
    }

    public Object unmarshal(HierarchicalStreamReader reader,
            UnmarshallingContext context) {
        if (isLegacyNode(reader, context))
            return legacyUnmarshal(reader, context);
        return ObjectId.fromString(reader.getValue());
    }
}
