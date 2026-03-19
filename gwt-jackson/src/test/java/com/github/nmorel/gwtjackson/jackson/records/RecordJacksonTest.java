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

package com.github.nmorel.gwtjackson.jackson.records;

import com.github.nmorel.gwtjackson.jackson.AbstractJacksonTest;
import com.github.nmorel.gwtjackson.shared.records.RecordTester;
import org.junit.jupiter.api.Test;

/**
 * Tests for Java record support using standard Jackson.
 */
public class RecordJacksonTest extends AbstractJacksonTest {

    @Test
    public void testSerializeSimpleRecord() {
        RecordTester.INSTANCE.testSerializeSimpleRecord( createWriter( RecordTester.SimpleRecord.class ) );
    }

    @Test
    public void testDeserializeSimpleRecord() {
        RecordTester.INSTANCE.testDeserializeSimpleRecord( createReader( RecordTester.SimpleRecord.class ) );
    }

    @Test
    public void testRoundTripSimpleRecord() {
        RecordTester.INSTANCE.testRoundTripSimpleRecord( createMapper( RecordTester.SimpleRecord.class ) );
    }

    @Test
    public void testSerializeRecordWithNullField() {
        RecordTester.INSTANCE.testSerializeRecordWithNullField( createWriter( RecordTester.RecordWithNullableField.class ) );
    }

    @Test
    public void testDeserializeRecordWithNullField() {
        RecordTester.INSTANCE.testDeserializeRecordWithNullField( createReader( RecordTester.RecordWithNullableField.class ) );
    }

    @Test
    public void testSerializeRecordWithPropertyOverride() {
        RecordTester.INSTANCE.testSerializeRecordWithPropertyOverride( createWriter( RecordTester.RecordWithPropertyOverride.class ) );
    }

    @Test
    public void testDeserializeRecordWithPropertyOverride() {
        RecordTester.INSTANCE.testDeserializeRecordWithPropertyOverride( createReader( RecordTester.RecordWithPropertyOverride.class ) );
    }

    @Test
    public void testSerializeRecordWithList() {
        RecordTester.INSTANCE.testSerializeRecordWithList( createWriter( RecordTester.RecordWithList.class ) );
    }

    @Test
    public void testDeserializeRecordWithList() {
        RecordTester.INSTANCE.testDeserializeRecordWithList( createReader( RecordTester.RecordWithList.class ) );
    }

    @Test
    public void testSerializeNestedRecord() {
        RecordTester.INSTANCE.testSerializeNestedRecord( createWriter( RecordTester.NestedRecord.class ) );
    }

    @Test
    public void testDeserializeNestedRecord() {
        RecordTester.INSTANCE.testDeserializeNestedRecord( createReader( RecordTester.NestedRecord.class ) );
    }

    @Test
    public void testRoundTripEmptyRecord() {
        RecordTester.INSTANCE.testRoundTripEmptyRecord( createMapper( RecordTester.EmptyRecord.class ) );
    }
}
