jdub-async
==========

*A damn simple postgres-async wrapper. Y'know. For asynchronous access to databases.*

Requirements
------------

* Java 6 or above
* Scala 2.11.x

How To Use
----------

**First**, specify Jdub-async as a dependency:

```scala
resolvers += Resolver.jcenterRepo
libraryDepenencies += "com.kyleu" %% "jdub-async" % "1.0"
```

(The postgres-async driver is automatically imported)

**Second**, connect to a database:

```scala
val db = new Database("localhost", "mydatabase", "myaccount", "mypassword").open()
```

**Third**, run some queries:

```scala
// Query returning an optional single result.
case class GetAge(name: String) extends FlatSingleRowQuery[Int] {
  override val sql = "SELECT age FROM people WHERE name = ?"
  override val values = Seq(name)
  override def flatMap(row: Row) = {
    row.as[Int]("age")
  }

}

val age = db.query(GetAge("Old Guy")).getOrElse(-1)
```

```scala
// Query returning a Person object for each row.
case object GetPeople extends Query[Seq[Person]] {
  override val sql = "SELECT name, email, age FROM people"
  override val values = Nil
  override def reduce(rows: Iterator[Row]) = rows.map { row =>
    val name = row.as[String]("name").getOrElse(throw new IllegalStateException())
    val email = row.as[String]("email").getOrElse("")
    val age = row.as[Int]("age").getOrElse(0)
    Person(name, email, age)
  }.toSeq
}

val people = db.query(GetPeople)
```


**Fourth**, execute some statements:

```scala
case class UpdateEmail(name: String, newEmail: String) extends Statement {
  override val sql = trim("UPDATE people SET email = ? WHERE name = ?")
  override val values = Seq(newEmail, name)
}

val affectedRowCount = db.execute(UpdateEmail("Old Guy", "oldguy@example.com"))
```

**Fifth**, extend BaseQueries for common classes:
```scala
case object UserQueries extends BaseQueries {
  override protected val tableName = "users"
  override protected val columns = Seq("id", "username", "full_name", "password")
  override protected val searchColumns = Seq("username", "full_name")

  val insert = Insert
  val getById = GetById
  val search = Search
}

Database.execute(UserQueries.Insert(User(1, "kyle", "Kyle U", "password")))
val searchResults = Database.query(UserQueries.search("kyle"))
```


License
-------

Copyright (c) 2015 Kyle Unverferth

Inspired by [jdub](https://github.com/SimpleFinance/jdub).

Published under The MIT License, see [LICENSE.md](LICENSE.md)
