/*
 * Copyright 2020-2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openmrs.analytics;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** This class manages to create resources on Thrift Server post each pipeline run. */
public class HiveTableManager {

  private static final Logger logger = LoggerFactory.getLogger(HiveTableManager.class.getName());
  private final String jdbcUrl;
  private final String user;
  private final String password;

  private static final String THRIFT_CONTAINER_PARQUET_PATH_PREFIX = "/dwh";

  public HiveTableManager(String jdbcUrl, String user, String password) {
    this.jdbcUrl = jdbcUrl;
    this.user = user;
    this.password = password;
  }

  /**
   * Method to create resources on Thrift Server Hive.
   *
   * @param resourceList Comma separated list of resources such as Patient, Observation, Encounter
   * @param timestamp Timestamp suffix to be used in table name.
   * @param thriftServerParquetPath location of parquet files in Thrift Server
   * @throws SQLException
   */
  public void createResourceTables(
      String resourceList, String timestamp, String thriftServerParquetPath) throws SQLException {
    if (resourceList == null || resourceList.isEmpty()) {
      return;
    }
    String[] resources = resourceList.split(",");
    if (resources == null || resources.length == 0) {
      return;
    }

    // TODO: Make use of JdbcConnectionUtil to create jdbc connection
    //  (https://github.com/google/fhir-data-pipes/issues/483)
    try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
      for (String resource : resources) {
        createResourceAndCanonicalTables(connection, resource, timestamp, thriftServerParquetPath);
      }
    }
  }

  /**
   * This method will create table 'encounter_2023_01_24t18_42_54_302111z' if the given resource is
   * Encounter and the timestamp suffix is 2023_01_24t18_42_54_302111z
   *
   * <p>wrt PARQUET LOCATION, THRIFT_CONTAINER_PARQUET_PATH_PREFIX is the directory hosting parquet
   * files, thriftServerParquetPath is the exact path for parquet files and resource shall be the
   * respective resource name e.g. Patient
   */
  private void createResourceAndCanonicalTables(
      Connection connection, String resource, String timestamp, String thriftServerParquetPath)
      throws SQLException {

    String sql =
        String.format(
            "CREATE TABLE IF NOT EXISTS default.%s_%s USING PARQUET LOCATION '%s/%s/%s'",
            resource,
            timestamp,
            THRIFT_CONTAINER_PARQUET_PATH_PREFIX,
            thriftServerParquetPath,
            resource);
    executeSql(connection, sql);

    // Drop canonical table if exists.
    sql = String.format("DROP TABLE IF EXISTS default.%s", resource);
    executeSql(connection, sql);

    // Create canonical table with latest parquet files.
    sql =
        String.format(
            "CREATE TABLE IF NOT EXISTS default.%s USING PARQUET LOCATION '%s/%s/%s'",
            resource, THRIFT_CONTAINER_PARQUET_PATH_PREFIX, thriftServerParquetPath, resource);
    executeSql(connection, sql);
  }

  /**
   * The method creates resource tables with names suffixed with given timestamp if not present. It
   * excepts complete parquet files path.
   *
   * @param resource FHIR resource type.
   * @param timestamp timestamp string to be used for resource table name.
   * @param thriftServerParquetPath directory path having output parquet files.
   * @throws SQLException
   */
  public void createResourceTable(String resource, String timestamp, String thriftServerParquetPath)
      throws SQLException {

    String sql =
        String.format(
            "CREATE TABLE IF NOT EXISTS default.%s_%s USING PARQUET LOCATION '%s'",
            resource, timestamp, thriftServerParquetPath);
    try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
      executeSql(connection, sql);
    }
  }

  /**
   * This method creates canonical resource tables if not present. It excepts complete parquet files
   * path.
   *
   * @param resource FHIR resource type.
   * @param thriftServerParquetPath directory path having output parquet files.
   * @throws SQLException
   */
  public void createResourceCanonicalTable(String resource, String thriftServerParquetPath)
      throws SQLException {

    String sql =
        String.format(
            "CREATE TABLE IF NOT EXISTS default.%s USING PARQUET LOCATION '%s'",
            resource, thriftServerParquetPath);
    try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
      executeSql(connection, sql);
    }
  }

  private void executeSql(Connection connection, String sql) throws SQLException {
    try (Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }
}
