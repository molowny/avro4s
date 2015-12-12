package com.sksamuel.avro4s

import org.scalatest.{WordSpec, Matchers}

class AvroSchema2Test extends WordSpec with Matchers {

  "SchemaGenerator.schemaFor" should {
    "accept booleans" in {
      case class Test(booly: Boolean)
      val expected = new org.apache.avro.Schema.Parser().parse(getClass.getResourceAsStream("/boolean.avsc"))
      val schema = AvroSchema2[Test]
      schema.toString(true) shouldBe expected.toString(true)
    }
    "accept bytes" in {
      case class Test(bytes: Array[Byte])
      val expected = new org.apache.avro.Schema.Parser().parse(getClass.getResourceAsStream("/bytes.avsc"))
      val schema = AvroSchema2[Test]
      schema.toString(true) shouldBe expected.toString(true)
    }
    "accept strings" in {
      case class Test(str: String)
      val expected = new org.apache.avro.Schema.Parser().parse(getClass.getResourceAsStream("/string.avsc"))
      val schema = AvroSchema2[Test]
      schema.toString(true) shouldBe expected.toString(true)
    }
    "accept integer" in {
      case class Test(inty: Int)
      val expected = new org.apache.avro.Schema.Parser().parse(getClass.getResourceAsStream("/integer.avsc"))
      val schema = AvroSchema2[Test]
      schema.toString(true) shouldBe expected.toString(true)
    }
    "accept longs" in {
      case class Test(foo: Long)
      val expected = new org.apache.avro.Schema.Parser().parse(getClass.getResourceAsStream("/long.avsc"))
      val schema = AvroSchema2[Test]
      schema.toString(true) shouldBe expected.toString(true)
    }
    "accept double" in {
      case class Test(double: Double)
      val expected = new org.apache.avro.Schema.Parser().parse(getClass.getResourceAsStream("/double.avsc"))
      val schema = AvroSchema2[Test]
      schema.toString(true) shouldBe expected.toString(true)
    }
    "accept float" in {
      case class Test(float: Float)
      val expected = new org.apache.avro.Schema.Parser().parse(getClass.getResourceAsStream("/float.avsc"))
      val schema = AvroSchema2[Test]
      schema.toString(true) shouldBe expected.toString(true)
    }
    "accept nested case classes" in {
      case class Nested(goo: String)
      case class Test(foo: String, nested: Nested)
      val expected = new org.apache.avro.Schema.Parser().parse(getClass.getResourceAsStream("/nested.avsc"))
      val schema = AvroSchema2[Test]
      schema.toString(true) shouldBe expected.toString(true)
    }
    "accept multiple nested case classes" in {
      case class Inner(goo: String)
      case class Middle(inner: Inner)
      case class Outer(middle: Middle)
      val expected = new org.apache.avro.Schema.Parser().parse(getClass.getResourceAsStream("/nested_multiple.avsc"))
      val schema = AvroSchema2[Outer]
      schema.toString(true) shouldBe expected.toString(true)
    }
    "generate option as Union[T, Null]" in {
      case class Test(option: Option[String])
      val expected = new org.apache.avro.Schema.Parser().parse(getClass.getResourceAsStream("/option.avsc"))
      val schema = AvroSchema2[Test]
      schema.toString(true) shouldBe expected.toString(true)
    }
    "generate array type for a scala.collection.immutable.Seq of primitives" in {
      case class Test(seq: Seq[String])
      val expected = new org.apache.avro.Schema.Parser().parse(getClass.getResourceAsStream("/seq.avsc"))
      val schema = AvroSchema2[Test]
      schema.toString(true) shouldBe expected.toString(true)
    }
    "generate array type for an Array of primitives" in {
      case class Test(array: Array[Boolean])
      val expected = new org.apache.avro.Schema.Parser().parse(getClass.getResourceAsStream("/array.avsc"))
      val schema = AvroSchema2[Test]
      schema.toString(true) shouldBe expected.toString(true)
    }
    "generate array type for a List of primitives" in {
      case class Test(list: List[String])
      val expected = new org.apache.avro.Schema.Parser().parse(getClass.getResourceAsStream("/list.avsc"))
      val schema = AvroSchema2[Test]
      schema.toString(true) shouldBe expected.toString(true)
    }
    "generate array type for a scala.collection.immutable.Seq of records" in {
      case class Nested(nested: String)
      case class Test(seq: Seq[Nested])
      val expected = new org.apache.avro.Schema.Parser().parse(getClass.getResourceAsStream("/seqrecords.avsc"))
      val schema = AvroSchema2[Test]
      schema.toString(true) shouldBe expected.toString(true)
    }
    "generate array type for an Array of records" in {
      case class Nested(str: String, bool: Boolean)
      case class Test(array: Array[Nested])
      val expected = new org.apache.avro.Schema.Parser().parse(getClass.getResourceAsStream("/arrayrecords.avsc"))
      val schema = AvroSchema2[Test]
      schema.toString(true) shouldBe expected.toString(true)
    }
    "generate array type for a List of records" in {
      case class Nested(nested: String, double: Double)
      case class Test(list: List[Nested])
      val expected = new org.apache.avro.Schema.Parser().parse(getClass.getResourceAsStream("/listrecords.avsc"))
      val schema = AvroSchema2[Test]
      schema.toString(true) shouldBe expected.toString(true)
    }
    "generate union:T,U for Either[T,U] of primitives" in {
      case class Test(either: Either[String, Double])
      val expected = new org.apache.avro.Schema.Parser().parse(getClass.getResourceAsStream("/either.avsc"))
      val schema = AvroSchema2[Test]
      schema.toString(true) shouldBe expected.toString(true)
    }
    "generate union:T,U for Either[T,U] of records" in {
      case class Nested1(a: String)
      case class Nested2(b: Boolean)
      case class Test(either: Either[Nested1, Nested2])
      val expected = new org.apache.avro.Schema.Parser().parse(getClass.getResourceAsStream("/eitherrecord.avsc"))
      val schema = AvroSchema2[Test]
      schema.toString(true) shouldBe expected.toString(true)
    }
    "generate map type for a scala.collection.immutable.Map of primitives" in {
      case class Test(map: Map[String, String])
      val expected = new org.apache.avro.Schema.Parser().parse(getClass.getResourceAsStream("/map.avsc"))
      val schema = AvroSchema2[Test]
      schema.toString(true) shouldBe expected.toString(true)
    }
    "generate map type for a scala.collection.immutable.Map of records" in {
      case class Nested(float: Float)
      case class Test(map: Map[String, Nested])
      val expected = new org.apache.avro.Schema.Parser().parse(getClass.getResourceAsStream("/maprecord.avsc"))
      val schema = AvroSchema2[Test]
      schema.toString(true) shouldBe expected.toString(true)
    }
    "generate map type for a scala.collection.immutable.Map of Option[Boolean]" in {
      case class Test(map: Map[String, Option[Boolean]])
      val expected = new org.apache.avro.Schema.Parser().parse(getClass.getResourceAsStream("/mapoption.avsc"))
      val schema = AvroSchema2[Test]
      schema.toString(true) shouldBe expected.toString(true)
    }
    "support maps of seqs of records" in {
      case class Nested(float: Float, double: Double)
      case class Test(map: Map[String, Seq[Nested]])
      val expected = new org.apache.avro.Schema.Parser().parse(getClass.getResourceAsStream("/map_seq_nested.avsc"))
      val schema = AvroSchema2[Test]
      schema.toString(true) shouldBe expected.toString(true)
    }
  }
}
