package pt.isec.common.util;

import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;

/** Central allow-list and resource limits for legacy Java serialization. */
public final class SerializationPolicy {
    private static final String FILTER_PATTERN = String.join(";",
            "maxdepth=32",
            "maxrefs=100000",
            "maxarray=1000000",
            "maxbytes=67108864",
            "pt.isec.common.messages.**",
            "pt.isec.common.dto.**",
            "pt.isec.common.model.**",
            "java.lang.*",
            "java.time.**",
            "java.util.**",
            "!*");

    private static final ObjectInputFilter FILTER =
            ObjectInputFilter.Config.createFilter(FILTER_PATTERN);

    private SerializationPolicy() {
    }

    public static void apply(ObjectInputStream input) {
        input.setObjectInputFilter(FILTER);
    }
}
