/*
 * Copyright 2017-2018 the original author or authors.
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

package com.google.cloud.spring.data.datastore.repository.query;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.spring.data.datastore.core.DatastoreTemplate;
import com.google.cloud.spring.data.datastore.core.mapping.DatastoreMappingContext;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.data.repository.core.NamedQueries;
import org.springframework.data.repository.query.Parameter;
import org.springframework.data.repository.query.Parameters;
import org.springframework.data.repository.query.QueryMethodEvaluationContextProvider;
import org.springframework.data.repository.query.QueryMethodValueEvaluationContextAccessor;
import org.springframework.data.repository.query.ValueExpressionDelegate;

/** Tests for the Query Method lookup class. */
class DatastoreQueryLookupStrategyTests {

  private DatastoreTemplate datastoreTemplate;

  private DatastoreMappingContext datastoreMappingContext;

  private DatastoreQueryMethod queryMethod;

  private ValueExpressionDelegate valueExpressionDelegate;

  private QueryMethodEvaluationContextProvider evaluationContextProvider;

  @BeforeEach
  void initMocks() {
    this.datastoreTemplate = mock(DatastoreTemplate.class);
    this.datastoreMappingContext = new DatastoreMappingContext();
    this.queryMethod = mock(DatastoreQueryMethod.class);
    this.valueExpressionDelegate = mock(ValueExpressionDelegate.class);
    this.evaluationContextProvider = mock(QueryMethodEvaluationContextProvider.class);
  }

  /**
   * int parameters are used as indexes of the two lookup strategies we use
   */
  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void resolveSqlQueryTest(boolean useValueExpressionDelegate) {
    DatastoreQueryLookupStrategy lookupStrategy = getDatastoreQueryLookupStrategy(useValueExpressionDelegate);
    String queryName = "fakeNamedQueryName";
    String query = "fake query";
    when(this.queryMethod.getNamedQueryName()).thenReturn(queryName);
    Query queryAnnotation = mock(Query.class);
    when(this.queryMethod.getQueryAnnotation()).thenReturn(queryAnnotation);
    NamedQueries namedQueries = mock(NamedQueries.class);

    Parameters parameters = mock(Parameters.class);

    Mockito.<Parameters>when(this.queryMethod.getParameters()).thenReturn(parameters);

    when(parameters.getNumberOfParameters()).thenReturn(1);
    when(parameters.getParameter(anyInt()))
        .thenAnswer(
            invocation -> {
              Parameter param = mock(Parameter.class);
              when(param.getName()).thenReturn(Optional.of("tag"));
              Mockito.<Class>when(param.getType()).thenReturn(Object.class);
              return param;
            });

    when(namedQueries.hasQuery(queryName)).thenReturn(true);
    when(namedQueries.getQuery(queryName)).thenReturn(query);
    when(valueExpressionDelegate.getEvaluationContextAccessor()).thenReturn(mock(QueryMethodValueEvaluationContextAccessor.class));

    lookupStrategy.resolveQuery(null, null, null, namedQueries);

    verify(lookupStrategy, times(1))
        .createGqlDatastoreQuery(eq(Object.class), same(this.queryMethod), eq(query));
  }

  private DatastoreQueryLookupStrategy getDatastoreQueryLookupStrategy(boolean useValueExpressionDelegate) {
    return useValueExpressionDelegate
        ? getDatastoreQueryLookupStrategy(this.valueExpressionDelegate)
        : getDatastoreQueryLookupStrategy(this.evaluationContextProvider);
  }

  private DatastoreQueryLookupStrategy getDatastoreQueryLookupStrategy(ValueExpressionDelegate valueExpressionDelegate) {
    DatastoreQueryLookupStrategy lookupStrategy =
        spy(
            new DatastoreQueryLookupStrategy(
                this.datastoreMappingContext,
                this.datastoreTemplate,
                valueExpressionDelegate));
    return prepareDatastoreQueryLookupStrategy(lookupStrategy);
  }

  private DatastoreQueryLookupStrategy getDatastoreQueryLookupStrategy(QueryMethodEvaluationContextProvider evaluationContextProvider) {
    DatastoreQueryLookupStrategy lookupStrategy =
        spy(
            new DatastoreQueryLookupStrategy(
                this.datastoreMappingContext,
                this.datastoreTemplate,
                evaluationContextProvider));
    return prepareDatastoreQueryLookupStrategy(lookupStrategy);
  }

  private DatastoreQueryLookupStrategy prepareDatastoreQueryLookupStrategy(DatastoreQueryLookupStrategy base) {
    doReturn(Object.class).when(base).getEntityType(any());
    doReturn(this.queryMethod)
        .when(base)
        .createQueryMethod(any(), any(), any());
    return base;
  }
}
