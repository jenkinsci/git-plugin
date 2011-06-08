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

    private Base64Encoder base64;

    /**
     * Create ObjectId converter
     */
    public ObjectIdConverter() {
        base64 = new Base64Encoder();
    }

    public boolean canConvert(Class type) {
        return ObjectId.class == type;
    }

    public void marshal(Object source, HierarchicalStreamWriter writer,
            MarshallingContext context) {
        writer.setValue(((ObjectId) source).name());
    }

    public Object unmarshal(HierarchicalStreamReader reader,
            final UnmarshallingContext context) {
        if (reader.hasMoreChildren()
                && "byte-array".equals(reader.peekNextChild())) {
            reader.moveDown();
            ObjectId sha1 = ObjectId.fromRaw(base64.decode(reader.getValue()));
            reader.moveUp();
            return sha1;
        } else
            return ObjectId.fromString(reader.getValue());
    }
}
