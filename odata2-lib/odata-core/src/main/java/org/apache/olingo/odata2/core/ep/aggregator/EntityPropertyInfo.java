/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 ******************************************************************************/
package org.apache.olingo.odata2.core.ep.aggregator;

import org.apache.olingo.odata2.api.edm.EdmCustomizableFeedMappings;
import org.apache.olingo.odata2.api.edm.EdmException;
import org.apache.olingo.odata2.api.edm.EdmFacets;
import org.apache.olingo.odata2.api.edm.EdmMapping;
import org.apache.olingo.odata2.api.edm.EdmProperty;
import org.apache.olingo.odata2.api.edm.EdmType;

/**
 * Collects informations about a property of an entity.
 * 
 */
public class EntityPropertyInfo {
  private final String name;
  private final EdmType type;
  private final EdmFacets facets;
  private final EdmCustomizableFeedMappings customMapping;
  private final String mimeType;
  private final EdmMapping mapping;

  EntityPropertyInfo(final String name, final EdmType type, final EdmFacets facets,
      final EdmCustomizableFeedMappings customizableFeedMapping, final String mimeType, final EdmMapping mapping) {
    this.name = name;
    this.type = type;
    this.facets = facets;
    customMapping = customizableFeedMapping;
    this.mimeType = mimeType;
    this.mapping = mapping;
  }

  static EntityPropertyInfo create(final EdmProperty property) throws EdmException {
    return new EntityPropertyInfo(
        property.getName(),
        property.getType(),
        property.getFacets(),
        property.getCustomizableFeedMappings(),
        property.getMimeType(),
        property.getMapping());
  }

  public boolean isMandatory() {
    return !(facets == null || facets.isNullable() == null || facets.isNullable());
  }

  public boolean isComplex() {
    return false;
  }

  public String getName() {
    return name;
  }

  public EdmType getType() {
    return type;
  }

  public EdmFacets getFacets() {
    return facets;
  }

  public EdmCustomizableFeedMappings getCustomMapping() {
    return customMapping;
  }

  public String getMimeType() {
    return mimeType;
  }

  public EdmMapping getMapping() {
    return mapping;
  }

  @Override
  public String toString() {
    return name;
  }
}
