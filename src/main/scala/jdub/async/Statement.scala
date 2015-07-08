package jdub.async

trait Statement {
  def sql: String
  def values: Seq[Any] = Seq.empty
}
