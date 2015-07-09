package jdub.async

import com.github.mauricio.async.db.pool.{ ConnectionPool, PoolConfiguration }
import com.github.mauricio.async.db.postgresql.pool.PostgreSQLConnectionFactory
import com.github.mauricio.async.db.{ Configuration, Connection, QueryResult }
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }

case class Database(configuration: Configuration) {
  private[this] val log = LoggerFactory.getLogger(Database.getClass)
  private[this] val factory = new PostgreSQLConnectionFactory(configuration)
  private[this] val poolConfig = new PoolConfiguration(maxObjects = 100, maxIdle = 10, maxQueueSize = 1000)
  private[this] val pool = new ConnectionPool(factory, poolConfig)
  private[this] def prependComment(obj: Object, sql: String) = s"/* ${obj.getClass.getSimpleName.replace("$", "")} */ $sql"

  def this(username: String, host: String = "localhost", port: Int = 5432, password: Option[String] = None, database: Option[String] = None) = {
    this(Configuration(username, host, port, password, database))
  }

  def open() = {
    val healthCheck = pool.sendQuery("select now()")
    healthCheck.onFailure {
      case x => throw new IllegalStateException("Cannot connect to database.", x)
    }
    Await.result(healthCheck.map(r => r.rowsAffected == 1.toLong), 5.seconds)
    this
  }

  def transaction[A](f: (Connection) => Future[A], conn: Connection = pool): Future[A] = conn.inTransaction(c => f(c))

  def execute(statement: Statement, conn: Option[Connection] = None): Future[Int] = {
    val name = statement.getClass.getSimpleName.replaceAllLiterally("$", "")
    log.debug(s"Executing statement [$name] with SQL [${statement.sql}] with values [${statement.values.mkString(", ")}].")
    val ret = conn.getOrElse(pool).sendPreparedStatement(prependComment(statement, statement.sql), statement.values).map(_.rowsAffected.toInt)
    ret.onFailure {
      case x: Throwable => log.error(s"Error [${x.getClass.getSimpleName}] encountered while executing statement [$name].", x)
    }
    ret
  }

  def query[A](query: RawQuery[A], conn: Option[Connection] = None): Future[A] = {
    val name = query.getClass.getSimpleName.replaceAllLiterally("$", "")
    log.debug(s"Executing query [$name] with SQL [${query.sql}] with values [${query.values.mkString(", ")}].")
    val ret = conn.getOrElse(pool).sendPreparedStatement(prependComment(query, query.sql), query.values).map { r =>
      query.handle(r.rows.getOrElse(throw new IllegalStateException()))
    }
    ret.onFailure {
      case x: Throwable => log.error(s"Error [${x.getClass.getSimpleName}] encountered while executing query [$name].", x)
    }
    ret
  }

  def raw(name: String, sql: String, conn: Option[Connection] = None): Future[QueryResult] = {
    log.debug(s"Executing raw query [$name] with SQL [$sql].")
    val ret = conn.getOrElse(pool).sendQuery(prependComment(name, sql))
    ret.onFailure {
      case x: Throwable => log.error(s"Error [${x.getClass.getSimpleName}] encountered while executing raw query [$name].", x)
    }
    ret
  }

  def close() = {
    Await.result(pool.close, 5.seconds)
    true
  }
}
