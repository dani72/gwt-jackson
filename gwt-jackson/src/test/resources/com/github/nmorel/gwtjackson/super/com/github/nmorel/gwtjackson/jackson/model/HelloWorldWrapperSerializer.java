/*
 * Copyright 2017 Nicolas Morel
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

package com.github.nmorel.gwtjackson.jackson.model;

import com.github.nmorel.gwtjackson.client.JsonSerializationContext;
import com.github.nmorel.gwtjackson.client.JsonSerializer;
import com.github.nmorel.gwtjackson.client.JsonSerializerParameters;
import com.github.nmorel.gwtjackson.client.stream.JsonWriter;
import com.github.nmorel.gwtjackson.shared.model.HelloWorldWrapper;

/**
 * Created by nicolasmorel on 23/05/2017.
 */
public class HelloWorldWrapperSerializer extends JsonSerializer<HelloWorldWrapper> {

    private static final HelloWorldWrapperSerializer INSTANCE = new HelloWorldWrapperSerializer();

    public static HelloWorldWrapperSerializer getInstance() {
        return INSTANCE;
    }

    private HelloWorldWrapperSerializer() { }

    @Override
    public void doSerialize( JsonWriter writer, HelloWorldWrapper value, JsonSerializationContext ctx, JsonSerializerParameters params ) {
        writer.value( value.toString() );
    }
}