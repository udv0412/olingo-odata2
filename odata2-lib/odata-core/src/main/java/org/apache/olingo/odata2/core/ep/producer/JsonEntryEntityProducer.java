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
package org.apache.olingo.odata2.core.ep.producer;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.olingo.odata2.api.ODataCallback;
import org.apache.olingo.odata2.api.edm.Edm;
import org.apache.olingo.odata2.api.edm.EdmEntitySet;
import org.apache.olingo.odata2.api.edm.EdmEntityType;
import org.apache.olingo.odata2.api.edm.EdmException;
import org.apache.olingo.odata2.api.edm.EdmMapping;
import org.apache.olingo.odata2.api.edm.EdmMultiplicity;
import org.apache.olingo.odata2.api.edm.EdmNavigationProperty;
import org.apache.olingo.odata2.api.ep.EntityProviderException;
import org.apache.olingo.odata2.api.ep.EntityProviderWriteProperties;
import org.apache.olingo.odata2.api.ep.callback.OnWriteEntryContent;
import org.apache.olingo.odata2.api.ep.callback.OnWriteFeedContent;
import org.apache.olingo.odata2.api.ep.callback.WriteCallbackContext;
import org.apache.olingo.odata2.api.ep.callback.WriteEntryCallbackContext;
import org.apache.olingo.odata2.api.ep.callback.WriteEntryCallbackResult;
import org.apache.olingo.odata2.api.ep.callback.WriteFeedCallbackContext;
import org.apache.olingo.odata2.api.ep.callback.WriteFeedCallbackResult;
import org.apache.olingo.odata2.api.exception.ODataApplicationException;
import org.apache.olingo.odata2.core.commons.ContentType;
import org.apache.olingo.odata2.core.commons.Encoder;
import org.apache.olingo.odata2.core.ep.aggregator.EntityInfoAggregator;
import org.apache.olingo.odata2.core.ep.util.FormatJson;
import org.apache.olingo.odata2.core.ep.util.JsonStreamWriter;

/**
 * Producer for writing an entity in JSON, also usable for function imports
 * returning a single instance of an entity type.
 * 
 */
public class JsonEntryEntityProducer {

  private final EntityProviderWriteProperties properties;
  private String eTag;
  private String location;
  private JsonStreamWriter jsonStreamWriter;

  public JsonEntryEntityProducer(final EntityProviderWriteProperties properties) throws EntityProviderException {
    this.properties = properties == null ? EntityProviderWriteProperties.serviceRoot(null).build() : properties;
  }

  public void append(final Writer writer, final EntityInfoAggregator entityInfo, final Map<String, Object> data,
      final boolean isRootElement) throws EntityProviderException {
    final EdmEntityType type = entityInfo.getEntityType();

    try {
      jsonStreamWriter = new JsonStreamWriter(writer);
      if (isRootElement) {
        jsonStreamWriter.beginObject().name(FormatJson.D);
      }

      jsonStreamWriter.beginObject();

      writeMetadata(entityInfo, data, type);

      writeProperties(entityInfo, data, type);

      writeNavigationProperties(writer, entityInfo, data, type);

      jsonStreamWriter.endObject();

      if (isRootElement) {
        jsonStreamWriter.endObject();
      }

      writer.flush();

    } catch (final IOException e) {
      throw new EntityProviderException(EntityProviderException.EXCEPTION_OCCURRED.addContent(e.getClass()
          .getSimpleName()), e);
    } catch (final EdmException e) {
      throw new EntityProviderException(EntityProviderException.EXCEPTION_OCCURRED.addContent(e.getClass()
          .getSimpleName()), e);
    }
  }

  private void writeNavigationProperties(final Writer writer, final EntityInfoAggregator entityInfo,
      final Map<String, Object> data,
      final EdmEntityType type) throws EdmException, EntityProviderException, IOException {
    for (final String navigationPropertyName : type.getNavigationPropertyNames()) {
      if (entityInfo.getSelectedNavigationPropertyNames().contains(navigationPropertyName)) {
        jsonStreamWriter.separator();
        jsonStreamWriter.name(navigationPropertyName);
        if (entityInfo.getExpandedNavigationPropertyNames().contains(navigationPropertyName)) {
          if (properties.getCallbacks() != null && properties.getCallbacks().containsKey(navigationPropertyName)) {
            writeExpandedNavigationProperty(writer, entityInfo, data, type, navigationPropertyName);
          } else {
            writeDeferredUri(navigationPropertyName);
          }
        } else {
          writeDeferredUri(navigationPropertyName);
        }
      }
    }
  }

  private void writeExpandedNavigationProperty(final Writer writer, final EntityInfoAggregator entityInfo,
      final Map<String, Object> data,
      final EdmEntityType type, final String navigationPropertyName) throws EdmException, EntityProviderException,
      IOException {
    final EdmNavigationProperty navigationProperty = (EdmNavigationProperty) type.getProperty(navigationPropertyName);
    final boolean isFeed = navigationProperty.getMultiplicity() == EdmMultiplicity.MANY;
    final EdmEntitySet entitySet = entityInfo.getEntitySet();
    final EdmEntitySet inlineEntitySet = entitySet.getRelatedEntitySet(navigationProperty);

    WriteCallbackContext context = isFeed ? new WriteFeedCallbackContext() : new WriteEntryCallbackContext();
    context.setSourceEntitySet(entitySet);
    context.setNavigationProperty(navigationProperty);
    context.setEntryData(data);
    context.setCurrentExpandSelectTreeNode(properties.getExpandSelectTree().getLinks().get(
        navigationPropertyName));

    ODataCallback callback = properties.getCallbacks().get(navigationPropertyName);
    if (callback == null) {
      throw new EntityProviderException(EntityProviderException.EXPANDNOTSUPPORTED);
    }
    try {
      if (isFeed) {
        final WriteFeedCallbackResult result =
            ((OnWriteFeedContent) callback).retrieveFeedResult((WriteFeedCallbackContext) context);
        List<Map<String, Object>> inlineData = result.getFeedData();
        if (inlineData == null) {
          inlineData = new ArrayList<Map<String, Object>>();
        }
        final EntityProviderWriteProperties inlineProperties = result.getInlineProperties();
        final EntityInfoAggregator inlineEntityInfo =
            EntityInfoAggregator.create(inlineEntitySet, inlineProperties.getExpandSelectTree());
        new JsonFeedEntityProducer(inlineProperties).append(writer, inlineEntityInfo, inlineData, false);

      } else {
        final WriteEntryCallbackResult result =
            ((OnWriteEntryContent) callback).retrieveEntryResult((WriteEntryCallbackContext) context);
        Map<String, Object> inlineData = result.getEntryData();
        if (inlineData != null && !inlineData.isEmpty()) {
          final EntityProviderWriteProperties inlineProperties = result.getInlineProperties();
          final EntityInfoAggregator inlineEntityInfo =
              EntityInfoAggregator.create(inlineEntitySet, inlineProperties.getExpandSelectTree());
          new JsonEntryEntityProducer(inlineProperties).append(writer, inlineEntityInfo, inlineData, false);
        } else {
          jsonStreamWriter.unquotedValue("null");
        }
      }
    } catch (final ODataApplicationException e) {
      throw new EntityProviderException(EntityProviderException.EXCEPTION_OCCURRED.addContent(e.getClass()
          .getSimpleName()), e);
    }
  }

  private void writeProperties(final EntityInfoAggregator entityInfo, final Map<String, Object> data,
      final EdmEntityType type) throws EdmException, EntityProviderException, IOException {
    for (final String propertyName : type.getPropertyNames()) {
      if (entityInfo.getSelectedPropertyNames().contains(propertyName)) {
        jsonStreamWriter.separator();
        jsonStreamWriter.name(propertyName);
        JsonPropertyEntityProducer.appendPropertyValue(jsonStreamWriter, entityInfo.getPropertyInfo(propertyName),
            data.get(propertyName));
      }
    }
  }

  private void writeMetadata(final EntityInfoAggregator entityInfo, final Map<String, Object> data,
      final EdmEntityType type) throws IOException, EntityProviderException, EdmException {
    jsonStreamWriter.name(FormatJson.METADATA);
    jsonStreamWriter.beginObject();
    final String self = AtomEntryEntityProducer.createSelfLink(entityInfo, data, null);
    location = (properties.getServiceRoot() == null ? "" : properties.getServiceRoot().toASCIIString()) + self;
    jsonStreamWriter.namedStringValue(FormatJson.ID, location);
    jsonStreamWriter.separator();
    jsonStreamWriter.namedStringValue(FormatJson.URI, location);
    jsonStreamWriter.separator();
    jsonStreamWriter.namedStringValueRaw(FormatJson.TYPE, type.getNamespace() + Edm.DELIMITER + type.getName());
    eTag = AtomEntryEntityProducer.createETag(entityInfo, data);
    if (eTag != null) {
      jsonStreamWriter.separator();
      jsonStreamWriter.namedStringValue(FormatJson.ETAG, eTag);
    }
    if (type.hasStream()) {
      jsonStreamWriter.separator();

      // We have to support the media resource mime type at the properties till version 1.2 then this can be refactored
      String mediaResourceMimeType = properties.getMediaResourceMimeType();
      EdmMapping entityTypeMapping = entityInfo.getEntityType().getMapping();
      String mediaSrc = null;

      if (entityTypeMapping != null) {
        String mediaResourceSourceKey = entityTypeMapping.getMediaResourceSourceKey();
        if (mediaResourceSourceKey != null) {
          mediaSrc = (String) data.get(mediaResourceSourceKey);
        }
        if (mediaSrc == null) {
          mediaSrc = self + "/$value";
        }
        if (mediaResourceMimeType == null) {
          mediaResourceMimeType =
              entityTypeMapping.getMimeType() != null ? (String) data.get(entityTypeMapping.getMimeType())
                  : (String) data.get(entityTypeMapping.getMediaResourceMimeTypeKey());
          if (mediaResourceMimeType == null) {
            mediaResourceMimeType = ContentType.APPLICATION_OCTET_STREAM.toString();
          }
        }
      } else {
        mediaSrc = self + "/$value";
        if (mediaResourceMimeType == null) {
          mediaResourceMimeType = ContentType.APPLICATION_OCTET_STREAM.toString();
        }
      }

      jsonStreamWriter.namedStringValueRaw(FormatJson.CONTENT_TYPE, mediaResourceMimeType);
      jsonStreamWriter.separator();

      jsonStreamWriter.namedStringValue(FormatJson.MEDIA_SRC, mediaSrc);
      jsonStreamWriter.separator();
      jsonStreamWriter.namedStringValue(FormatJson.EDIT_MEDIA, location + "/$value");
    }
    jsonStreamWriter.endObject();
  }

  private void writeDeferredUri(final String navigationPropertyName) throws IOException {
    jsonStreamWriter.beginObject()
        .name(FormatJson.DEFERRED);
    JsonLinkEntityProducer.appendUri(jsonStreamWriter, location + "/" + Encoder.encode(navigationPropertyName));
    jsonStreamWriter.endObject();
  }

  public String getETag() {
    return eTag;
  }

  public String getLocation() {
    return location;
  }

  private String extractKey(final Map<String, Object> data, final String mediaResourceMimeTypeKey)
      throws EntityProviderException {
    Object key = data.get(mediaResourceMimeTypeKey);
    if (key == null || key instanceof String) {
      return (String) key;
    } else {
      throw new EntityProviderException(EntityProviderException.ILLEGAL_ARGUMENT
          .addContent("Key must be of type String"));
    }
  }
}
