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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.datastore.Cursor;
import com.google.cloud.datastore.DoubleValue;
import com.google.cloud.datastore.GqlQuery;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.KeyFactory;
import com.google.cloud.datastore.KeyValue;
import com.google.cloud.datastore.LongValue;
import com.google.cloud.datastore.Value;
import com.google.cloud.spring.data.datastore.core.DatastoreResultsIterable;
import com.google.cloud.spring.data.datastore.core.DatastoreTemplate;
import com.google.cloud.spring.data.datastore.core.convert.DatastoreCustomConversions;
import com.google.cloud.spring.data.datastore.core.convert.DatastoreEntityConverter;
import com.google.cloud.spring.data.datastore.core.convert.ReadWriteConversions;
import com.google.cloud.spring.data.datastore.core.convert.TwoStepsConversions;
import com.google.cloud.spring.data.datastore.core.mapping.DatastoreMappingContext;
import com.google.cloud.spring.data.datastore.core.mapping.Entity;
import com.google.cloud.spring.data.datastore.core.mapping.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.Parameter;
import org.springframework.data.repository.query.Parameters;
import org.springframework.data.repository.query.QueryMethodEvaluationContextProvider;
import org.springframework.data.repository.query.QueryMethodValueEvaluationContextAccessor;
import org.springframework.data.repository.query.ValueExpressionDelegate;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.support.StandardEvaluationContext;

/** Tests for the GQL Query Method. */
class GqlDatastoreQueryTests {

  /** Constant for which if two doubles are within DELTA, they are considered equal. */
  private static final Offset<Double> DELTA = Offset.offset(0.00001);

  private final DatastoreMappingContext datastoreMappingContext = new DatastoreMappingContext();

  private DatastoreTemplate datastoreTemplate;

  private DatastoreEntityConverter datastoreEntityConverter;

  private ReadWriteConversions readWriteConversions;

  private DatastoreQueryMethod queryMethod;

  private ValueExpressionDelegate valueExpressionDelegate;

  private QueryMethodEvaluationContextProvider evaluationContextProvider;

  @BeforeEach
  void initMocks() {
    this.queryMethod = mock(DatastoreQueryMethod.class);
    this.datastoreTemplate = mock(DatastoreTemplate.class);
    this.datastoreEntityConverter = mock(DatastoreEntityConverter.class);
    this.readWriteConversions =
        new TwoStepsConversions(
            new DatastoreCustomConversions(), null, this.datastoreMappingContext);
    when(this.datastoreTemplate.getDatastoreEntityConverter())
        .thenReturn(this.datastoreEntityConverter);
    when(this.datastoreEntityConverter.getConversions()).thenReturn(this.readWriteConversions);
    this.valueExpressionDelegate = mock(ValueExpressionDelegate.class);
    when(valueExpressionDelegate.getEvaluationContextAccessor()).thenReturn(mock(QueryMethodValueEvaluationContextAccessor.class));
    this.evaluationContextProvider = mock(QueryMethodEvaluationContextProvider.class);
  }

  private GqlDatastoreQuery<Trade> createQuerySpy(
      String gql, boolean isPageQuery, boolean isSliceQuery, boolean useValueExpressionDelegate) {
    GqlDatastoreQuery<Trade> spy;
    if (useValueExpressionDelegate) {
      spy =
          spy(
              new GqlDatastoreQuery<>(
                  Trade.class,
                  this.queryMethod,
                  this.datastoreTemplate,
                  gql,
                  this.valueExpressionDelegate,
                  this.datastoreMappingContext));
    } else {
      spy = spy(new GqlDatastoreQuery<>(
          Trade.class,
          this.queryMethod,
          this.datastoreTemplate,
          gql,
          this.evaluationContextProvider,
          this.datastoreMappingContext));
    }
    return spy;
  }

  private GqlDatastoreQuery<Trade> createQuery(
      String gql, boolean isPageQuery, boolean isSliceQuery, boolean useValueExpressionDelegate) {
    GqlDatastoreQuery<Trade> spy = createQuerySpy(gql, isPageQuery, isSliceQuery, useValueExpressionDelegate);
    doReturn(isPageQuery).when(spy).isPageQuery();
    doReturn(isSliceQuery).when(spy).isSliceQuery();
    return spy;
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void compoundNameConventionTest(boolean useValueExpressionDelegate) {

    String gql =
        "SELECT * FROM "
            + "|com.google.cloud.spring.data.datastore."
            + "repository.query.GqlDatastoreQueryTests$Trade|"
            + " WHERE price=:#{#tag6 * -1} AND price<>:#{#tag6 * -1} OR "
            + "price<>:#{#tag7 * -1} AND "
            + "( action=@tag0 AND ticker=@tag1 ) OR "
            + "( trader_id=@tag2 AND price<@tag3 ) OR ( price>=@tag4 AND id<>NULL AND "
            + "trader_id=NULL AND trader_id LIKE %@tag5 AND price=TRUE AND price=FALSE AND "
            + "price>@tag6 AND price<=@tag7 AND trade_ref = @tag8) ORDER BY id DESC LIMIT 3;";

    String entityResolvedGql =
        "SELECT * FROM trades"
            + " WHERE price=@SpELtag1 AND price<>@SpELtag2 OR price<>@SpELtag3 AND "
            + "( action=@tag0 AND ticker=@tag1 ) OR "
            + "( trader_id=@tag2 AND price<@tag3 ) OR ( price>=@tag4 AND id<>NULL AND "
            + "trader_id=NULL AND trader_id LIKE %@tag5 AND price=TRUE AND price=FALSE AND "
            + "price>@tag6 AND price<=@tag7 AND trade_ref = @tag8) ORDER BY id DESC LIMIT 3";

    Trade trade = new Trade();
    trade.id = "tradeId1";

    Object[] paramVals =
        new Object[] {
          "BUY",
          "abcd",
          // this is an array param of the non-natively supported type and will need conversion
          new int[] {1, 2},
          new double[] {8.88, 9.99},
          3, // this parameter is a simple int, which is not a directly supported type and uses
          // conversions
          "blahblah",
          1.11,
          2.22,
          trade
        };

    String[] paramNames =
        new String[] {"tag0", "tag1", "tag2", "tag3", "tag4", "tag5", "tag6", "tag7", "tag8"};

    buildParameters(paramVals, paramNames);

    KeyFactory keyFactory = new KeyFactory("proj");
    keyFactory.setKind("kind");
    Key key = keyFactory.newKey("tradeid1-key");

    doReturn(key).when(this.datastoreTemplate).getKey(any());

    // to be used when using a query method evaluation context
    EvaluationContext evaluationContext = new StandardEvaluationContext();
    for (int i = 0; i < paramVals.length; i++) {
      evaluationContext.setVariable(paramNames[i], paramVals[i]);
    }
    when(this.evaluationContextProvider.getEvaluationContext(any(), any()))
        .thenReturn(evaluationContext);
    when(this.evaluationContextProvider.getEvaluationContext(any(), any(), any()))
        .thenReturn(evaluationContext);

    // to be used when testing with a value expression delegate
    this.valueExpressionDelegate = ValueExpressionDelegate.create();

    GqlDatastoreQuery gqlDatastoreQuery = createQuery(gql, false, false, useValueExpressionDelegate);

    doAnswer(
            invocation -> {
              GqlQuery statement = invocation.getArgument(0);

              assertThat(statement.getQueryString()).isEqualTo(entityResolvedGql);

              Map<String, Value> paramMap = statement.getNamedBindings();

              assertThat(paramMap.get("tag0").get()).isEqualTo(paramVals[0]);
              assertThat(paramMap.get("tag1").get()).isEqualTo(paramVals[1]);

              // custom conversion is expected to have been used in this param
              assertThat((long) ((LongValue) (((List) paramMap.get("tag2").get()).get(0))).get())
                  .isEqualTo(1L);
              assertThat((long) ((LongValue) (((List) paramMap.get("tag2").get()).get(1))).get())
                  .isEqualTo(2L);

              double actual = ((DoubleValue) (((List) paramMap.get("tag3").get()).get(0))).get();
              assertThat(actual).isEqualTo(((double[]) paramVals[3])[0], DELTA);

              actual = ((DoubleValue) (((List) paramMap.get("tag3").get()).get(1))).get();
              assertThat(actual).isEqualTo(((double[]) paramVals[3])[1], DELTA);

              // 3L is expected even though 3 int was the original param due to custom conversions
              assertThat(paramMap.get("tag4").get()).isEqualTo(3L);
              assertThat(paramMap.get("tag5").get()).isEqualTo(paramVals[5]);
              assertThat(paramMap.get("tag6").get()).isEqualTo(paramVals[6]);
              assertThat(paramMap.get("tag7").get()).isEqualTo(paramVals[7]);

              assertThat((double) paramMap.get("SpELtag1").get())
                  .isEqualTo(-1 * (double) paramVals[6], DELTA);
              assertThat((double) paramMap.get("SpELtag2").get())
                  .isEqualTo(-1 * (double) paramVals[6], DELTA);
              assertThat((double) paramMap.get("SpELtag3").get())
                  .isEqualTo(-1 * (double) paramVals[7], DELTA);

              assertThat(((KeyValue) paramMap.get("tag8")).get()).isSameAs(key);

              return null;
            })
        .when(this.datastoreTemplate)
        .queryKeysOrEntities(any(), eq(Trade.class));

    doReturn(false).when(gqlDatastoreQuery).isNonEntityReturnedType(any());

    gqlDatastoreQuery.execute(paramVals);

    verify(this.datastoreTemplate, times(1)).queryKeysOrEntities(any(), eq(Trade.class));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void pageableTest(boolean useValueExpressionDelegate) {

    String gql = "SELECT * FROM trades WHERE price=@price";

    Object[] paramVals = new Object[] {1, PageRequest.of(0, 2)};

    String[] paramNames = new String[] {"price", null};

    Parameters parameters = buildParameters(paramVals, paramNames);

    when(parameters.hasPageableParameter()).thenReturn(true);
    when(parameters.getPageableIndex()).thenReturn(1);

    GqlDatastoreQuery gqlDatastoreQuery = createQuery(gql, false, false, useValueExpressionDelegate);

    doAnswer(
            invocation -> {
              GqlQuery statement = invocation.getArgument(0);

              assertThat(statement.getQueryString())
                  .isEqualTo("SELECT * FROM trades WHERE price=@price LIMIT @limit OFFSET @offset");

              Map<String, Value> paramMap = statement.getNamedBindings();

              assertThat(paramMap.get("price").get()).isEqualTo(1L);
              assertThat(paramMap.get("limit").get()).isEqualTo(2L);
              assertThat(paramMap.get("offset").get()).isEqualTo(0L);

              return null;
            })
        .when(this.datastoreTemplate)
        .queryKeysOrEntities(any(), eq(Trade.class));

    doReturn(false).when(gqlDatastoreQuery).isNonEntityReturnedType(any());

    gqlDatastoreQuery.execute(paramVals);

    verify(this.datastoreTemplate, times(1)).queryKeysOrEntities(any(), eq(Trade.class));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void pageableTestSort(boolean useValueExpressionDelegate) {

    String gql = "SELECT * FROM trades WHERE price=@price";

    Object[] paramVals = new Object[] {1, Sort.by(Sort.Order.asc("p1"), Sort.Order.desc("p2"))};

    String[] paramNames = new String[] {"price", null};

    Parameters parameters = buildParameters(paramVals, paramNames);

    when(parameters.hasSortParameter()).thenReturn(true);
    when(parameters.getSortIndex()).thenReturn(1);

    GqlDatastoreQuery gqlDatastoreQuery = createQuery(gql, false, false, useValueExpressionDelegate);

    doAnswer(
            invocation -> {
              GqlQuery statement = invocation.getArgument(0);

              assertThat(statement.getQueryString())
                  .isEqualTo("SELECT * FROM trades WHERE price=@price ORDER BY p1 ASC, p2 DESC");

              Map<String, Value> paramMap = statement.getNamedBindings();

              assertThat(paramMap.get("price").get()).isEqualTo(1L);

              return null;
            })
        .when(this.datastoreTemplate)
        .queryKeysOrEntities(any(), eq(Trade.class));

    doReturn(false).when(gqlDatastoreQuery).isNonEntityReturnedType(any());

    gqlDatastoreQuery.execute(paramVals);

    verify(this.datastoreTemplate, times(1)).queryKeysOrEntities(any(), eq(Trade.class));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void pageableTestSlice(boolean useValueExpressionDelegate) {

    String gql = "SELECT * FROM trades WHERE price=@price";

    Object[] paramVals = new Object[] {1, PageRequest.of(0, 2)};

    String[] paramNames = new String[] {"price", null};

    Parameters parameters = buildParameters(paramVals, paramNames);

    Mockito.<Class>when(this.queryMethod.getReturnedObjectType()).thenReturn(Trade.class);
    when(parameters.hasPageableParameter()).thenReturn(true);
    when(parameters.getPageableIndex()).thenReturn(1);

    GqlDatastoreQuery gqlDatastoreQuery = createQuery(gql, false, true, useValueExpressionDelegate);

    Cursor cursor = Cursor.copyFrom("abc".getBytes());
    List<Map> params = new ArrayList<>();
    doAnswer(
            invocation -> {
              GqlQuery statement = invocation.getArgument(0);

              assertThat(statement.getQueryString())
                  .isEqualTo("SELECT * FROM trades WHERE price=@price LIMIT @limit OFFSET @offset");

              Map paramMap = statement.getNamedBindings();

              params.add(paramMap);

              return new DatastoreResultsIterable(Collections.emptyList(), cursor);
            })
        .when(this.datastoreTemplate)
        .queryKeysOrEntities(any(), eq(Trade.class));

    doReturn(false).when(gqlDatastoreQuery).isNonEntityReturnedType(any());
    doAnswer(invocation -> invocation.getArgument(0))
        .when(gqlDatastoreQuery)
        .processRawObjectForProjection(any());

    Slice result = (Slice) gqlDatastoreQuery.execute(paramVals);

    assertThat(((DatastorePageable) result.getPageable()).toCursor()).isEqualTo(cursor);

    verify(this.datastoreTemplate, times(2)).queryKeysOrEntities(any(), eq(Trade.class));

    assertThat(((Value) params.get(0).get("price")).get()).isEqualTo(1L);
    assertThat(((Value) params.get(0).get("limit")).get()).isEqualTo(2L);
    assertThat(((Value) params.get(0).get("offset")).get()).isEqualTo(0L);

    assertThat(((Value) params.get(1).get("price")).get()).isEqualTo(1L);
    assertThat(((Value) params.get(1).get("limit")).get()).isEqualTo(1L);
    assertThat(params.get(1)).containsEntry("offset", cursor);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void pageableTestPage(boolean useValueExpressionDelegate) {

    String gql = "SELECT * FROM trades WHERE price=@price";
    String expected = "SELECT * FROM trades WHERE price=@price LIMIT @limit OFFSET @offset";

    Object[] paramVals = new Object[] {1, PageRequest.of(0, 2)};

    String[] paramNames = new String[] {"price", null};

    Parameters parameters = buildParameters(paramVals, paramNames);

    Mockito.<Class>when(this.queryMethod.getReturnedObjectType()).thenReturn(Trade.class);
    when(parameters.hasPageableParameter()).thenReturn(true);
    when(parameters.getPageableIndex()).thenReturn(1);

    GqlDatastoreQuery gqlDatastoreQuery = createQuery(gql, true, true, useValueExpressionDelegate);

    Cursor cursor = Cursor.copyFrom("abc".getBytes());

    doAnswer(
            invocation -> {
              GqlQuery statement = invocation.getArgument(0);

              assertThat(
                      statement.getQueryString().equals(gql)
                          || statement.getQueryString().equals(expected))
                  .isTrue();
              Map<String, Value> paramMap = statement.getNamedBindings();

              if (statement.getQueryString().equals(expected)) {
                assertThat(paramMap).hasSize(3);
                assertThat(paramMap.get("price").get()).isEqualTo(1L);
                assertThat(paramMap.get("limit").get()).isEqualTo(2L);
                assertThat(paramMap.get("offset").get()).isEqualTo(0L);
                return new DatastoreResultsIterable(Collections.emptyList(), cursor);
              } else if (statement.getQueryString().equals(gql)) {
                assertThat(paramMap).hasSize(1);
                assertThat(paramMap.get("price").get()).isEqualTo(1L);
                return new DatastoreResultsIterable(Arrays.asList(1L, 2L), cursor);
              }
              return null;
            })
        .when(this.datastoreTemplate)
        .queryKeysOrEntities(any(), eq(Trade.class));

    doReturn(false).when(gqlDatastoreQuery).isNonEntityReturnedType(any());
    doAnswer(invocation -> invocation.getArgument(0))
        .when(gqlDatastoreQuery)
        .processRawObjectForProjection(any());

    Slice result = (Page) gqlDatastoreQuery.execute(paramVals);

    assertThat(((DatastorePageable) result.getPageable()).toCursor()).isEqualTo(cursor);
    assertThat(((DatastorePageable) result.getPageable()).getTotalCount()).isEqualTo(2L);

    assertThat(((Page) result).getTotalElements()).isEqualTo(2L);

    verify(this.datastoreTemplate, times(2)).queryKeysOrEntities(any(), eq(Trade.class));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void pageableTestPageCursor(boolean useValueExpressionDelegate) {
    String gql = "SELECT * FROM trades WHERE price=@price";
    String expected = "SELECT * FROM trades WHERE price=@price LIMIT @limit OFFSET @offset";

    Cursor cursorInPageable = Cursor.copyFrom("cde".getBytes());
    long countInPageable = 123L;
    Object[] paramVals =
        new Object[] {
          1, new DatastorePageable(PageRequest.of(0, 2), cursorInPageable, countInPageable)
        };

    String[] paramNames = new String[] {"price", null};

    Parameters parameters = buildParameters(paramVals, paramNames);

    Mockito.<Class>when(this.queryMethod.getReturnedObjectType()).thenReturn(Trade.class);
    when(parameters.hasPageableParameter()).thenReturn(true);
    when(parameters.getPageableIndex()).thenReturn(1);

    GqlDatastoreQuery gqlDatastoreQuery = createQuery(gql, true, true, useValueExpressionDelegate);

    Cursor cursor = Cursor.copyFrom("abc".getBytes());

    doAnswer(
            invocation -> {
              GqlQuery statement = invocation.getArgument(0);

              assertThat(statement.getQueryString()).isEqualTo(expected);
              Map<String, Object> paramMap = statement.getNamedBindings();

              assertThat(paramMap).hasSize(3);
              assertThat(((Value) paramMap.get("price")).get()).isEqualTo(1L);
              assertThat(((Value) paramMap.get("limit")).get()).isEqualTo(2L);
              assertThat(paramMap).containsEntry("offset", cursorInPageable);
              return new DatastoreResultsIterable(Collections.emptyList(), cursor);
            })
        .when(this.datastoreTemplate)
        .queryKeysOrEntities(any(), eq(Trade.class));

    doReturn(false).when(gqlDatastoreQuery).isNonEntityReturnedType(any());
    doAnswer(invocation -> invocation.getArgument(0))
        .when(gqlDatastoreQuery)
        .processRawObjectForProjection(any());

    Slice result = (Page) gqlDatastoreQuery.execute(paramVals);

    assertThat(((DatastorePageable) result.getPageable()).toCursor()).isEqualTo(cursor);
    assertThat(((DatastorePageable) result.getPageable()).getTotalCount())
        .isEqualTo(countInPageable);

    assertThat(((Page) result).getTotalElements()).isEqualTo(countInPageable);

    verify(this.datastoreTemplate, times(1)).queryKeysOrEntities(any(), eq(Trade.class));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void streamResultTest(boolean useValueExpressionDelegate) {
    Mockito.<Class>when(this.queryMethod.getReturnedObjectType()).thenReturn(Trade.class);
    Parameters parameters = mock(Parameters.class);
    when(this.queryMethod.getParameters()).thenReturn(parameters);
    when(parameters.getNumberOfParameters()).thenReturn(0);
    when(this.queryMethod.isStreamQuery()).thenReturn(true);

    Trade tradeA = new Trade();
    tradeA.id = "a";
    Trade tradeB = new Trade();
    tradeB.id = "b";
    doAnswer(
            invocation -> {
              GqlQuery statement = invocation.getArgument(0);
              assertThat(statement.getQueryString()).isEqualTo("unusedGqlString");

              Cursor cursor = Cursor.copyFrom("abc".getBytes());
              DatastoreResultsIterable datastoreResultsIterable =
                  new DatastoreResultsIterable(Arrays.asList(tradeA, tradeB), cursor);
              return datastoreResultsIterable;
            })
        .when(this.datastoreTemplate)
        .queryKeysOrEntities(any(), eq(Trade.class));

    GqlDatastoreQuery gqlDatastoreQuery = createQuery("unusedGqlString", false, false, useValueExpressionDelegate);

    Object result = gqlDatastoreQuery.execute(new Parameters[0]);
    assertThat(result).isInstanceOf(Stream.class);
    assertThat((Stream) result).hasSize(2).containsExactly(tradeA, tradeB);
  }

  private Parameters buildParameters(Object[] params, String[] paramNames) {
    Parameters parameters = mock(Parameters.class);

    Mockito.<Parameters>when(this.queryMethod.getParameters()).thenReturn(parameters);

    final List<Parameter> parameterList = new ArrayList<>();
    for (int index = 0; index < params.length; index++) {

      Parameter param = mock(Parameter.class);
      parameterList.add(param);
      when(param.getName())
          .thenReturn(
              paramNames[index] == null ? Optional.empty() : Optional.of(paramNames[index]));

      Mockito.<Class>when(param.getType()).thenReturn(params[index].getClass());
      when(param.isNamedParameter()).thenReturn(true);
      when(param.getRequiredName()).thenReturn(paramNames[index]);
      when(param.getIndex()).thenReturn(index);

      when(parameters.getParameter(eq(index))).thenAnswer(invocation -> param);
    }
    when(parameters.getNumberOfParameters()).thenReturn(paramNames.length);
    // we return a new iterator each time. This is because foreach loops call iterable.iterator()
    // once per foreach loop.
    when(parameters.iterator())
        .thenAnswer(
            new Answer<Iterator>() {
              @Override
              public Iterator answer(InvocationOnMock invocation) throws Throwable {
                return parameterList.iterator();
              }
            });
    return parameters;
  }

  @Entity(name = "trades")
  private static class Trade {
    @Id String id;

    String action;

    Double price;

    Double shares;

    @Field(name = "ticker")
    String symbol;

    @Field(name = "trader_id")
    String traderId;
  }
}
