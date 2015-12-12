package com.sksamuel.avro4s

import org.apache.avro.generic.GenericRecord
import shapeless._
import shapeless.labelled.FieldType
import scala.collection.JavaConverters._

import scala.language.implicitConversions
import scala.reflect.ClassTag

trait ToValue[A] {
  def apply(value: A): Option[Any] = Some(value)
}

object ToValue {

  implicit object BooleanToValue extends ToValue[Boolean]

  implicit object StringToValue extends ToValue[String]

  implicit object DoubleToValue extends ToValue[Double]

  implicit object FloatToValue extends ToValue[Float]

  implicit object IntToValue extends ToValue[Int]

  implicit object LongToValue extends ToValue[Long]

  implicit object HNilToValue extends ToValue[HNil] {
    override def apply(value: HNil): Option[AnyRef] = None
  }

  implicit def EitherWriter[T, U](implicit leftWriter: ToValue[T], rightWriter: ToValue[U]) = new ToValue[Either[T, U]] {
    override def apply(value: Either[T, U]): Option[Any] = value match {
      case Left(left) => leftWriter.apply(left)
      case Right(right) => rightWriter.apply(right)
    }
  }

  implicit def OptionToValue[T](implicit tovalue: ToValue[T]) = new ToValue[Option[T]] {
    override def apply(value: Option[T]): Option[Any] = value.flatMap(tovalue.apply)
  }

  implicit def ArrayToValue[T] = new ToValue[Array[T]] {
    override def apply(value: Array[T]): Option[Any] = Some(value.toSeq.asJava)
  }

  implicit def SeqWriter[T](implicit writer: ToValue[T]): ToValue[Seq[T]] = new ToValue[Seq[T]] {
    override def apply(values: Seq[T]): Option[Any] = Some(values.flatMap(writer.apply).asJava)
  }

  implicit def ListWriter[T] = new ToValue[List[T]] {
    override def apply(value: List[T]): Option[java.util.List[T]] = Some(value.asJava)
  }

  implicit def MapWriter[T](implicit writer: Lazy[ToValue[T]]) = new ToValue[Map[String, T]] {
    override def apply(value: Map[String, T]): Option[java.util.Map[String, T]] = {
      Some(
        value.mapValues(writer.value.apply).collect {
          case (k, Some(t: T)) => k -> t
        }.asJava
      )
    }
  }

  implicit def GenericToValue[T](implicit ser: AvroSerializer[T]): ToValue[T] = new ToValue[T] {
    override def apply(value: T): Option[GenericRecord] = Some(ser.toRecord(value))
  }
}

trait Writes[L <: HList] extends Serializable {
  def write(record: GenericRecord, value: L): Unit
}

object Writes {

  implicit object HNilFields extends Writes[HNil] {
    override def write(record: GenericRecord, value: HNil): Unit = ()
  }

  implicit def HConsFields[Key <: Symbol, V, T <: HList](implicit key: Witness.Aux[Key],
                                                         writer: ToValue[V],
                                                         remaining: Writes[T],
                                                         tag: ClassTag[V]): Writes[FieldType[Key, V] :: T] = {
    new Writes[FieldType[Key, V] :: T] {
      override def write(record: GenericRecord, value: FieldType[Key, V] :: T): Unit = value match {
        case h :: t =>
          writer.apply(h).foreach(record.put(key.value.name, _))
          remaining.write(record, t)
      }
    }
  }
}

trait AvroSerializer[T] {
  def toRecord(t: T): GenericRecord
}

object AvroSerializer {

  implicit def GenericSer[T, Repr <: HList](implicit labl: LabelledGeneric.Aux[T, Repr],
                                            writes: Lazy[Writes[Repr]],
                                            schema: AvroSchema2[T]) = new AvroSerializer[T] {
    override def toRecord(t: T): GenericRecord = {
      val r = new org.apache.avro.generic.GenericData.Record(schema())
      writes.value.write(r, labl.to(t))
      r
    }
  }

  def apply[T](t: T)(implicit ser: AvroSerializer[T]): GenericRecord = ser.toRecord(t)
}