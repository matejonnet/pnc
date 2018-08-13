/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.pnc.common.json.serializer;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import sun.security.provider.certpath.AdjacencyList;

import java.io.IOException;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class AdjacencyListSerializer extends StdSerializer<AdjacencyList> {

    public AdjacencyListSerializer() {
        this(null);
    }

    public AdjacencyListSerializer(Class<AdjacencyList> t) {
        super(t);
    }

    @Override
    public void serialize(AdjacencyList value, JsonGenerator jgen, SerializerProvider provider)
            throws IOException, JsonGenerationException {

        jgen.writeStartObject();
        jgen.writeStringField("mOrigList", value.toString());
        jgen.writeEndObject();
    }
}
