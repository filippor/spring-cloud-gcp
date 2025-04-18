/*
 * Copyright 2017-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.spring.data.datastore.core.mapping;

import org.springframework.data.mapping.Association;
import org.springframework.data.mapping.PersistentEntity;
import org.springframework.data.mapping.model.AnnotationBasedPersistentProperty;
import org.springframework.data.mapping.model.FieldNamingStrategy;
import org.springframework.data.mapping.model.Property;
import org.springframework.data.mapping.model.PropertyNameFieldNamingStrategy;
import org.springframework.data.mapping.model.SimpleTypeHolder;
import org.springframework.util.StringUtils;

/**
 * Persistent property metadata implementation for Datastore.
 *
 * @since 1.1
 */
public class DatastorePersistentPropertyImpl
    extends AnnotationBasedPersistentProperty<DatastorePersistentProperty>
    implements DatastorePersistentProperty {

  private static final String KEY_FIELD_NAME = "__key__";

  private final FieldNamingStrategy fieldNamingStrategy;

  private final boolean isSkipNullValue;

  /**
   * Constructor.
   *
   * @param property the property to store
   * @param owner the entity to which this property belongs
   * @param simpleTypeHolder the type holder
   * @param fieldNamingStrategy the naming strategy used to get the column name of this property
   */
  DatastorePersistentPropertyImpl(
      Property property,
      PersistentEntity<?, DatastorePersistentProperty> owner,
      SimpleTypeHolder simpleTypeHolder,
      FieldNamingStrategy fieldNamingStrategy,
      boolean isSkipNullValue) {
    super(property, owner, simpleTypeHolder);
    this.fieldNamingStrategy =
        (fieldNamingStrategy != null)
            ? fieldNamingStrategy
            : PropertyNameFieldNamingStrategy.INSTANCE;
    this.isSkipNullValue = isSkipNullValue;
    verify();
  }

  private void verify() {
    if (hasFieldAnnotation() && (isDescendants() || isAssociation())) {
      throw new DatastoreDataException(
          "Property cannot be annotated as @Field if it is annotated @Descendants or @Reference: "
              + getFieldName());
    }
    if (isDescendants() && isAssociation()) {
      throw new DatastoreDataException(
          "Property cannot be annotated both @Descendants and @Reference: " + getFieldName());
    }
    if (isDescendants() && !isCollectionLike()) {
      throw new DatastoreDataException(
          "Only collection-like properties can contain the "
              + "descendant entity objects can be annotated @Descendants.");
    }
  }

  @Override
  public String getFieldName() {
    if (isIdProperty()) {
      return KEY_FIELD_NAME;
    }
    if (StringUtils.hasText(getAnnotatedFieldName())) {
      return getAnnotatedFieldName();
    }
    return this.fieldNamingStrategy.getFieldName(this);
  }

  private boolean hasFieldAnnotation() {
    return findAnnotation(Field.class) != null;
  }

  @Override
  public boolean isDescendants() {
    return findAnnotation(Descendants.class) != null;
  }

  @Override
  public boolean isUnindexed() {
    return findAnnotation(Unindexed.class) != null;
  }

  @Override
  public boolean isColumnBacked() {
    return !isDescendants() && !isAssociation();
  }

  @Override
  public EmbeddedType getEmbeddedType() {
    return EmbeddedType.of(getTypeInformation());
  }

  @Override
  protected Association<DatastorePersistentProperty> createAssociation() {
    return new Association<>(this, null);
  }

  private String getAnnotatedFieldName() {

    Field annotation = findAnnotation(Field.class);

    if (annotation != null && StringUtils.hasText(annotation.name())) {
      return annotation.name();
    }

    return null;
  }

  @Override
  public boolean isLazyLoaded() {
    return findAnnotation(LazyReference.class) != null;
  }

  @Override
  public boolean isSkipNullValue() {
    return isSkipNullValue;
  }
}
