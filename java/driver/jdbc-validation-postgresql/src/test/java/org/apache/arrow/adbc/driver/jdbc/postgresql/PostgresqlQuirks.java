/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.arrow.adbc.driver.jdbc.postgresql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import org.apache.arrow.adbc.core.AdbcDatabase;
import org.apache.arrow.adbc.core.AdbcDriver;
import org.apache.arrow.adbc.core.AdbcException;
import org.apache.arrow.adbc.driver.jdbc.JdbcDriver;
import org.apache.arrow.adbc.driver.jdbc.adapter.JdbcToArrowTypeConverters;
import org.apache.arrow.adbc.driver.testsuite.SqlValidationQuirks;
import org.apache.arrow.adbc.sql.SqlQuirks;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.vector.types.TimeUnit;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.junit.jupiter.api.Assumptions;

public class PostgresqlQuirks extends SqlValidationQuirks {
  static final String POSTGRESQL_URL_ENV_VAR = "ADBC_JDBC_POSTGRESQL_URL";
  static final String POSTGRESQL_USER_ENV_VAR = "ADBC_JDBC_POSTGRESQL_USER";
  static final String POSTGRESQL_PASSWORD_ENV_VAR = "ADBC_JDBC_POSTGRESQL_PASSWORD";

  static String makeJdbcUrl() {
    final String postgresUrl = System.getenv(POSTGRESQL_URL_ENV_VAR);
    final String user = System.getenv(POSTGRESQL_USER_ENV_VAR);
    final String password = System.getenv(POSTGRESQL_PASSWORD_ENV_VAR);
    Assumptions.assumeFalse(
        postgresUrl == null, "PostgreSQL not found, set " + POSTGRESQL_URL_ENV_VAR);
    Assumptions.assumeFalse(
        postgresUrl.isEmpty(), "PostgreSQL not found, set " + POSTGRESQL_URL_ENV_VAR);
    return String.format("jdbc:postgresql://%s?user=%s&password=%s", postgresUrl, user, password);
  }

  @Override
  public AdbcDatabase initDatabase(BufferAllocator allocator) throws AdbcException {
    String url = makeJdbcUrl();

    final Map<String, Object> parameters = new HashMap<>();
    parameters.put(AdbcDriver.PARAM_URL, url);
    parameters.put(
        AdbcDriver.PARAM_SQL_QUIRKS,
        SqlQuirks.builder()
            .arrowToSqlTypeNameMapping(
                (arrowType -> {
                  if (arrowType.getTypeID() == ArrowType.ArrowTypeID.Utf8) {
                    return "TEXT";
                  }
                  return SqlQuirks.DEFAULT_ARROW_TYPE_TO_SQL_TYPE_NAME_MAPPING.apply(arrowType);
                }))
            .build());
    parameters.put(JdbcDriver.PARAM_JDBC_TO_ARROW_TYPE, JdbcToArrowTypeConverters.POSTGRESQL);
    return new JdbcDriver(allocator).open(parameters);
  }

  @Override
  public void cleanupTable(String name) throws Exception {
    try (final Connection connection1 = DriverManager.getConnection(makeJdbcUrl())) {
      try (Statement statement = connection1.createStatement()) {
        statement.execute("DROP TABLE " + name);
      } catch (SQLException ignored) {
      }
    }
  }

  @Override
  public String defaultCatalog() {
    // XXX: this should really come from configuration
    return "postgres";
  }

  @Override
  public String caseFoldTableName(String name) {
    return name.toLowerCase();
  }

  @Override
  public String caseFoldColumnName(String name) {
    return name.toLowerCase();
  }

  @Override
  public TimeUnit defaultTimestampUnit() {
    return TimeUnit.MICROSECOND;
  }
}
