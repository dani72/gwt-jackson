/*
 * Copyright 2013 Nicolas Morel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.nmorel.gwtjackson.rebind.type;

import java.util.List;
import java.util.Objects;

import com.google.gwt.core.ext.typeinfo.JType;
import com.squareup.javapoet.CodeBlock;

/**
 * Contains informations about serializer like its type or the code to instantiate it.
 *
 * @author Nicolas Morel
 * @version $Id: $
 */
public final class JSerializerType extends JMapperType {

    public static final class Builder extends JMapperType.Builder<Builder, JSerializerType> {

        public JSerializerType build() {
            Objects.requireNonNull( instance, "instance is mandatory" );
            Objects.requireNonNull( type, "type is mandatory" );
            if ( null == parameters ) {
                parameters = List.of();
            }
            return new JSerializerType( beanMapper, type, instance, parameters );
        }
    }

    private JSerializerType( boolean beanMapper, JType type, CodeBlock instance, List<JSerializerType> parameters ) {
        super( beanMapper, type, instance, parameters );
    }
}
