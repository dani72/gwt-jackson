/*
 * Copyright 2014 Nicolas Morel
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

package com.github.nmorel.gwtjackson.rebind.property;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.nmorel.gwtjackson.rebind.CreatorUtils;
import com.google.gwt.core.ext.typeinfo.HasAnnotations;
import com.google.gwt.core.ext.typeinfo.JField;
import com.google.gwt.core.ext.typeinfo.JMethod;
import com.google.gwt.core.ext.typeinfo.JParameter;
import java.util.Optional;

/**
 * Used to aggregate field, getter method and setter method of the same field
 *
 * @author Nicolas Morel
 */
final class PropertyAccessorsBuilder {

    private final String fieldName;

    private String propertyName;

    private Optional<JField> field = Optional.empty();

    private List<JField> fields = new ArrayList<>();

    private Optional<JMethod> getter = Optional.empty();

    private List<JMethod> getters = new ArrayList<>();

    private Optional<JMethod> setter = Optional.empty();

    private List<JMethod> setters = new ArrayList<>();

    private Optional<JParameter> parameter = Optional.empty();

    private List<HasAnnotations> accessors = new ArrayList<>();

    PropertyAccessorsBuilder( String fieldName ) {
        this.fieldName = fieldName;
    }

    Optional<JField> getField() {
        return field;
    }

    String computePropertyName() {
        Optional<JsonProperty> jsonProperty = CreatorUtils.getAnnotation( JsonProperty.class, accessors );
        if ( jsonProperty.isPresent() && jsonProperty.get().value() != null && !jsonProperty.get().value().isEmpty() && !JsonProperty.USE_DEFAULT_NAME
                .equals( jsonProperty.get().value() ) ) {
            propertyName = jsonProperty.get().value();
        } else {
            propertyName = fieldName;
        }
        return propertyName;
    }

    void addField( JField field, boolean mixin ) {
        if ( this.fields.size() > 1 || (mixin && !this.field.isPresent() && this.fields.size() == 1) || (!mixin && this.field
                .isPresent()) ) {
            // we already found one mixin and one field type hierarchy
            // or we want to add a mixin but we have already one
            // or we want to add a field but we have already one
            return;
        }
        if ( !mixin ) {
            this.field = Optional.of( field );
        }
        this.fields.add( field );
        this.accessors.add( field );
    }

    void addGetter( JMethod getter, boolean mixin ) {
        if ( !mixin && !this.getter.isPresent() ) {
            this.getter = Optional.of( getter );
        }
        this.getters.add( getter );
        this.accessors.add( getter );
    }

    void addSetter( JMethod setter, boolean mixin ) {
        if ( !mixin && !this.setter.isPresent() ) {
            this.setter = Optional.of( setter );
        }
        this.setters.add( setter );
        this.accessors.add( setter );
    }

    void setParameter( JParameter parameter ) {
        this.parameter = Optional.of( parameter );
        this.accessors.add( parameter );
    }

    PropertyAccessors build() {
        return new PropertyAccessors( propertyName, field, getter, setter, parameter, List.copyOf( fields ),
                List.copyOf( getters ), List.copyOf( setters ), List.copyOf( accessors ) );
    }
}
