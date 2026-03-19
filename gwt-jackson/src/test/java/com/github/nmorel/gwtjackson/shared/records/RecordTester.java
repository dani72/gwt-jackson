/*
 * Copyright 2024 Nicolas Morel
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

package com.github.nmorel.gwtjackson.shared.records;

import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.nmorel.gwtjackson.shared.AbstractTester;
import com.github.nmorel.gwtjackson.shared.ObjectMapperTester;
import com.github.nmorel.gwtjackson.shared.ObjectReaderTester;
import com.github.nmorel.gwtjackson.shared.ObjectWriterTester;

/**
 * Tests for Java record serialization/deserialization support.
 */
public final class RecordTester extends AbstractTester {

    public static final RecordTester INSTANCE = new RecordTester();

    public record SimpleRecord(String name, int age) {}

    public record RecordWithNullableField(String name, String nickname) {}

    public record RecordWithPropertyOverride(@JsonProperty("full_name") String name, int age) {}

    public record RecordWithList(String name, List<String> tags) {}

    public record NestedRecord(String label, SimpleRecord inner) {}

    public record EmptyRecord() {}

    // Serialization tests

    public void testSerializeSimpleRecord( ObjectWriterTester<SimpleRecord> writer ) {
        SimpleRecord record = new SimpleRecord( "John", 30 );
        String json = writer.write( record );
        assertEquals( "{\"name\":\"John\",\"age\":30}", json );
    }

    public void testDeserializeSimpleRecord( ObjectReaderTester<SimpleRecord> reader ) {
        String json = "{\"name\":\"John\",\"age\":30}";
        SimpleRecord record = reader.read( json );
        assertEquals( "John", record.name() );
        assertEquals( 30, record.age() );
    }

    public void testRoundTripSimpleRecord( ObjectMapperTester<SimpleRecord> mapper ) {
        SimpleRecord original = new SimpleRecord( "Jane", 25 );
        String json = mapper.write( original );
        SimpleRecord result = mapper.read( json );
        assertEquals( original.name(), result.name() );
        assertEquals( original.age(), result.age() );
    }

    public void testSerializeRecordWithNullField( ObjectWriterTester<RecordWithNullableField> writer ) {
        RecordWithNullableField record = new RecordWithNullableField( "John", null );
        String json = writer.write( record );
        assertEquals( "{\"name\":\"John\",\"nickname\":null}", json );
    }

    public void testDeserializeRecordWithNullField( ObjectReaderTester<RecordWithNullableField> reader ) {
        String json = "{\"name\":\"John\",\"nickname\":null}";
        RecordWithNullableField record = reader.read( json );
        assertEquals( "John", record.name() );
        assertNull( record.nickname() );
    }

    public void testSerializeRecordWithPropertyOverride( ObjectWriterTester<RecordWithPropertyOverride> writer ) {
        RecordWithPropertyOverride record = new RecordWithPropertyOverride( "John Doe", 30 );
        String json = writer.write( record );
        assertEquals( "{\"full_name\":\"John Doe\",\"age\":30}", json );
    }

    public void testDeserializeRecordWithPropertyOverride( ObjectReaderTester<RecordWithPropertyOverride> reader ) {
        String json = "{\"full_name\":\"John Doe\",\"age\":30}";
        RecordWithPropertyOverride record = reader.read( json );
        assertEquals( "John Doe", record.name() );
        assertEquals( 30, record.age() );
    }

    public void testSerializeRecordWithList( ObjectWriterTester<RecordWithList> writer ) {
        RecordWithList record = new RecordWithList( "item", Arrays.asList( "a", "b", "c" ) );
        String json = writer.write( record );
        assertEquals( "{\"name\":\"item\",\"tags\":[\"a\",\"b\",\"c\"]}", json );
    }

    public void testDeserializeRecordWithList( ObjectReaderTester<RecordWithList> reader ) {
        String json = "{\"name\":\"item\",\"tags\":[\"a\",\"b\",\"c\"]}";
        RecordWithList record = reader.read( json );
        assertEquals( "item", record.name() );
        assertEquals( Arrays.asList( "a", "b", "c" ), record.tags() );
    }

    public void testSerializeNestedRecord( ObjectWriterTester<NestedRecord> writer ) {
        NestedRecord record = new NestedRecord( "parent", new SimpleRecord( "child", 5 ) );
        String json = writer.write( record );
        assertEquals( "{\"label\":\"parent\",\"inner\":{\"name\":\"child\",\"age\":5}}", json );
    }

    public void testDeserializeNestedRecord( ObjectReaderTester<NestedRecord> reader ) {
        String json = "{\"label\":\"parent\",\"inner\":{\"name\":\"child\",\"age\":5}}";
        NestedRecord record = reader.read( json );
        assertEquals( "parent", record.label() );
        assertEquals( "child", record.inner().name() );
        assertEquals( 5, record.inner().age() );
    }

    public void testRoundTripEmptyRecord( ObjectMapperTester<EmptyRecord> mapper ) {
        EmptyRecord original = new EmptyRecord();
        String json = mapper.write( original );
        assertEquals( "{}", json );
        EmptyRecord result = mapper.read( json );
        assertNotNull( result );
    }
}
