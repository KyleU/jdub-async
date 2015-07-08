package jdub.async.queries

import jdub.async.{ Row, SingleRowQuery, Statement }

object DdlQueries {
  case class DoesTableExist(tableName: String) extends SingleRowQuery[Boolean] {
    override val sql = "select exists (select * from information_schema.tables WHERE table_name = ?);"
    override val values = tableName :: Nil
    override def map(row: Row) = row.as[Boolean]("exists")
  }

  case class TruncateTables(tableNames: Seq[String]) extends Statement {
    override val sql = s"truncate ${tableNames.mkString(", ")}"
  }

  def trim(s: String) = s.replaceAll("""[\s]+""", " ").trim
}
