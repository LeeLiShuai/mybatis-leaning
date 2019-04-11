/**
 *    Copyright 2009-2019 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.transaction.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.session.TransactionIsolationLevel;
import org.apache.ibatis.transaction.Transaction;
import org.apache.ibatis.transaction.TransactionException;

/**
 * 它直接使用JDBC提交和回滚功能。
 * 它依赖于从dataSource检索的连接来管理事务的范围。
 * 延迟连接检索，直到调用getConnection（）。
 * 启用自动提交时忽略提交或回滚请求。
 *
 * @author Clinton Begin
 *
 * @see JdbcTransactionFactory
 */
public class JdbcTransaction implements Transaction {

  private static final Log log = LogFactory.getLog(JdbcTransaction.class);
  /**
   * 数据库连接
   */
  protected Connection connection;
  /**
   * DataSource
   */
  protected DataSource dataSource;
  /**
   * 事务隔离级别
   */
  protected TransactionIsolationLevel level;
  /**
   * 是否自动提交
   */
  protected boolean autoCommit;

  /**
   * 不带connection的构造函数
   * @param ds
   * @param desiredLevel
   * @param desiredAutoCommit
   */
  public JdbcTransaction(DataSource ds, TransactionIsolationLevel desiredLevel, boolean desiredAutoCommit) {
    dataSource = ds;
    level = desiredLevel;
    autoCommit = desiredAutoCommit;
  }

  /**
   * 只有connection的构造函数
   * @param connection
   */
  public JdbcTransaction(Connection connection) {
    this.connection = connection;
  }

  /**
   * 获取连接，如果不是使用connection初始化的，则根据DataSource获取连接
   * @return
   * @throws SQLException
   */
  @Override
  public Connection getConnection() throws SQLException {
    if (connection == null) {
      openConnection();
    }
    return connection;
  }

  /**
   * 提交
   * @throws SQLException
   */
  @Override
  public void commit() throws SQLException {
    //连接存在且不会自动提交，就提交
    if (connection != null && !connection.getAutoCommit()) {
      if (log.isDebugEnabled()) {
        log.debug("Committing JDBC Connection [" + connection + "]");
      }
      connection.commit();
    }
  }

  /**
   * 回滚
   * @throws SQLException
   */
  @Override
  public void rollback() throws SQLException {
    //连接存在且不会自动提交，就回滚
    if (connection != null && !connection.getAutoCommit()) {
      if (log.isDebugEnabled()) {
        log.debug("Rolling back JDBC Connection [" + connection + "]");
      }
      connection.rollback();
    }
  }

  /**
   * 关闭事务
   * @throws SQLException
   */
  @Override
  public void close() throws SQLException {
    if (connection != null) {
      //设置自动提交
      resetAutoCommit();
      if (log.isDebugEnabled()) {
        log.debug("Closing JDBC Connection [" + connection + "]");
      }
      //关闭连接
      connection.close();
    }
  }

  /**
   * 设置是否自动提交
   * @param desiredAutoCommit
   */
  protected void setDesiredAutoCommit(boolean desiredAutoCommit) {
    try {
      //如果连接中的是否自动提交属性和传入的不同，就设置为传入的
      if (connection.getAutoCommit() != desiredAutoCommit) {
        if (log.isDebugEnabled()) {
          log.debug("Setting autocommit to " + desiredAutoCommit + " on JDBC Connection [" + connection + "]");
        }
        connection.setAutoCommit(desiredAutoCommit);
      }
    } catch (SQLException e) {
      // Only a very poorly implemented driver would fail here,
      // and there's not much we can do about that.
      throw new TransactionException("Error configuring AutoCommit.  "
          + "Your driver may not support getAutoCommit() or setAutoCommit(). "
          + "Requested setting: " + desiredAutoCommit + ".  Cause: " + e, e);
    }
  }

  /**
   * 设置自动提交
   */
  protected void resetAutoCommit() {
    try {
      if (!connection.getAutoCommit()) {
        //如果只执行了选择，MyBatis不会在连接上调用commit / rollback
        //某些数据库使用select语句启动事务.
        //他们在关闭连接之前要求提交/回滚.
        //解决方法是在关闭连接之前将自动提交设置为true.
        // Sybase在这里抛出一个异常.
        if (log.isDebugEnabled()) {
          log.debug("Resetting autocommit to true on JDBC Connection [" + connection + "]");
        }
        connection.setAutoCommit(true);
      }
    } catch (SQLException e) {
      if (log.isDebugEnabled()) {
        log.debug("Error resetting autocommit to true "
            + "before closing the connection.  Cause: " + e);
      }
    }
  }

  /**
   * 打开连接，根据DataSource获取连接，并设置事务隔离级别和是否自动提交
   * @throws SQLException
   */
  protected void openConnection() throws SQLException {
    if (log.isDebugEnabled()) {
      log.debug("Opening JDBC Connection");
    }
    //从DataSource获取连接
    connection = dataSource.getConnection();
    //设置事务隔离级别
    if (level != null) {
      connection.setTransactionIsolation(level.getLevel());
    }
    //设置自动提交
    setDesiredAutoCommit(autoCommit);
  }

  @Override
  public Integer getTimeout() throws SQLException {
    return null;
  }

}
