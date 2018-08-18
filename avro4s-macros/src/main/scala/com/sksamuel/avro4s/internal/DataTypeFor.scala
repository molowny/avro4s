package com.sksamuel.avro4s.internal

import java.sql.Timestamp
import java.time.{LocalDate, LocalDateTime}
import java.util.UUID

import com.sksamuel.avro4s.ScaleAndPrecisionAndRoundingMode
import shapeless.ops.coproduct.Reify
import shapeless.ops.hlist.ToList
import shapeless.{:+:, CNil, Coproduct, Generic, HList}

import scala.language.experimental.macros
import scala.math.BigDecimal.RoundingMode.UNNECESSARY
import scala.reflect.ClassTag
import scala.reflect.internal.{Definitions, StdNames, SymbolTable}
import scala.reflect.macros.whitebox
import scala.reflect.runtime.universe._

trait DataTypeFor[T] {
  def dataType: DataType
}

trait LowPriorityDataTypeFor {

  // A coproduct is a union, or a generalised either.
  // A :+: B :+: C :+: CNil is a type that is either an A, or a B, or a C.

  // Shapeless's implementation builds up the type recursively,
  // (i.e., it's actually A :+: (B :+: (C :+: CNil)))
  // so here we define the schema for the base case of the recursion, C :+: CNil
  implicit def coproductBaseSchema[S](implicit basefor: DataTypeFor[S]): DataTypeFor[S :+: CNil] = new DataTypeFor[S :+: CNil] {
    override def dataType: DataType = UnionType(Seq(basefor.dataType))
  }

  // And here we continue the recursion up.
  implicit def coproductSchema[S, T <: Coproduct](implicit basefor: DataTypeFor[S], coproductFor: DataTypeFor[T]): DataTypeFor[S :+: T] = new DataTypeFor[S :+: T] {
    // union schemas can't contain other union schemas as a direct
    // child, so whenever we create a union, we need to check if our
    // children are unions and flatten
    override def dataType: DataType = UnionType.flatten(basefor.dataType, coproductFor.dataType)
  }

  implicit def genCoproduct[T, C <: Coproduct](implicit gen: Generic.Aux[T, C],
                                               coproductFor: DataTypeFor[C]): DataTypeFor[T] = new DataTypeFor[T] {
    override def dataType: DataType = coproductFor.dataType
  }
}

object DataTypeFor extends LowPriorityDataTypeFor {

  implicit def apply[T]: DataTypeFor[T] = macro applyImpl[T]

  def applyImpl[T: c.WeakTypeTag](c: whitebox.Context): c.Expr[DataTypeFor[T]] = {

    import c.universe
    import c.universe._

    val reflect = ReflectHelper(c)
    val tType = weakTypeOf[T]

    // we can only encode concrete classes as the top level
    require(tType.typeSymbol.isClass, tType + " is not a class but is " + tType.typeSymbol.fullName)

    // if we have a value type then we will just summon an existing DataTypeFor implicitly
    // otherwise we will create a new instance of the DataTypeFor typeclass which will create
    // a StructType for each of the fields
    if (reflect.isValueClass(tType)) {
      val underlying = reflect.underlyingType(tType)
      c.Expr[DataTypeFor[T]](
        q"""
        new _root_.com.sksamuel.avro4s.internal.DataTypeFor[$tType] {
          override def dataType: com.sksamuel.avro4s.internal.DataType = implicitly[com.sksamuel.avro4s.internal.DataTypeFor[$underlying]].dataType
        }
      """)
    } else {

      val fields = reflect.fieldsOf(tType).zipWithIndex.map { case ((f, fieldTpe), index) =>

        // the simple name of the field like "x"
        val fieldName = f.name.decodedName.toString.trim

        // if the field is a value type, we should include annotations defined on the value type as well
        val annos = if (reflect.isValueClass(fieldTpe)) {
          reflect.annotations(f) ++ reflect.annotations(fieldTpe.typeSymbol)
        } else {
          reflect.annotations(f)
        }

        val defswithsymbols = universe.asInstanceOf[Definitions with SymbolTable with StdNames]

        // this gets the method that generates the default value for this field
        // (if the field has a default value otherwise its a nosymbol)
        val defaultGetterName = defswithsymbols.nme.defaultGetterName(defswithsymbols.nme.CONSTRUCTOR, index + 1)

        // this is a method symbol for the default getter if it exists
        val defaultGetterMethod = tType.companion.member(TermName(defaultGetterName.toString))

        // if the field is a param with a default value, then we know the getter method will be defined
        // and so we can use it to generate the default value
        if (f.isTerm && f.asTerm.isParamWithDefault && defaultGetterMethod.isMethod) {
          //val method = reflect.defaultGetter(tType, index + 1)
          // the default method is defined on the companion object
          val moduleSym = tType.typeSymbol.companion
          q"""{ _root_.com.sksamuel.avro4s.internal.DataTypeFor.structField[$fieldTpe]($fieldName, Seq(..$annos), $moduleSym.$defaultGetterMethod) }"""
          // q"""{ _root_.com.sksamuel.avro4s.internal.DataTypeFor.structField[$fieldTpe]($fieldName, Seq(..$annos), null) }"""
        } else {
          q"""{ _root_.com.sksamuel.avro4s.internal.DataTypeFor.structField[$fieldTpe]($fieldName, Seq(..$annos), null) }"""
        }
      }

      // qualified name of the class we are building
      // todo encode generics:: + genericNameSuffix(underlyingType)
      val className = tType.typeSymbol.fullName.toString

      // eg, "Foo" for x.y.Foo
      val simpleName = tType.typeSymbol.name.decodedName.toString
      // we iterate up the owner tree until we find an Object or Package
      val packageName = Stream.iterate(tType.typeSymbol.owner)(_.owner)
        .dropWhile(x => !x.isPackage && !x.isModuleClass)
        .head
        .fullName

      // these are annotations on the class itself
      val annos = reflect.annotations(tType.typeSymbol)

      c.Expr[DataTypeFor[T]](
        q"""
        new _root_.com.sksamuel.avro4s.internal.DataTypeFor[$tType] {
          val structType = _root_.com.sksamuel.avro4s.internal.StructType($className, $simpleName, $packageName, Seq(..$annos), Seq(..$fields))
          override def dataType: com.sksamuel.avro4s.internal.DataType = structType
        }
      """)
    }
  }

  /**
    * Builds a [[StructField]] with the data type provided by an implicit instance of [[DataTypeFor]].
    * There must be a provider in scope for any type we want to support in avro4s.
    */
  def structField[B](name: String, annos: Seq[Anno], default: Any)(implicit dataTypeFor: DataTypeFor[B]): StructField = {
    println(s"default for $name = $default")
    StructField(name, dataTypeFor.dataType, annos, default)
  }

  implicit object ByteFor extends DataTypeFor[Byte] {
    override def dataType: DataType = ByteType
  }

  implicit object StringFor extends DataTypeFor[String] {
    override def dataType: DataType = StringType
  }

  implicit object LongFor extends DataTypeFor[Long] {
    override def dataType: DataType = LongType
  }

  implicit object IntFor extends DataTypeFor[Int] {
    override def dataType: DataType = IntType
  }

  implicit object DoubleFor extends DataTypeFor[Double] {
    override def dataType: DataType = DoubleType
  }

  implicit object FloatFor extends DataTypeFor[Float] {
    override def dataType: DataType = FloatType
  }

  implicit object BooleanFor extends DataTypeFor[Boolean] {
    override def dataType: DataType = BooleanType
  }

  implicit object ShortFor extends DataTypeFor[Short] {
    override def dataType: DataType = ShortType
  }

  implicit object UUIDFor extends DataTypeFor[UUID] {
    override def dataType: DataType = UUIDType
  }

  implicit object ByteArrayFor extends DataTypeFor[Array[Byte]] {
    override def dataType: DataType = BinaryType
  }

  implicit object ByteSeqFor extends DataTypeFor[Seq[Byte]] {
    override def dataType: DataType = BinaryType
  }

  lazy val defaultScaleAndPrecisionAndRoundingMode = ScaleAndPrecisionAndRoundingMode(2, 8, UNNECESSARY)

  implicit def BigDecimalFor(implicit sp: ScaleAndPrecisionAndRoundingMode = defaultScaleAndPrecisionAndRoundingMode): DataTypeFor[BigDecimal] = new DataTypeFor[BigDecimal] {
    override def dataType: DataType = DecimalType(sp.precision, sp.scale)
  }

  implicit def EitherFor[A, B](implicit leftFor: DataTypeFor[A], rightFor: DataTypeFor[B]): DataTypeFor[Either[A, B]] = {
    new DataTypeFor[Either[A, B]] {
      override def dataType: DataType = UnionType(Seq(leftFor.dataType, rightFor.dataType))
    }
  }

  implicit def OptionFor[T](implicit elementType: DataTypeFor[T]): DataTypeFor[Option[T]] = {
    new DataTypeFor[Option[T]] {
      override def dataType: DataType = NullableType(elementType.dataType)
    }
  }

  implicit def ArrayFor[S](implicit elementType: DataTypeFor[S]): DataTypeFor[Array[S]] = {
    new DataTypeFor[Array[S]] {
      override def dataType: DataType = ArrayType(elementType.dataType)
    }
  }

  implicit def IterableFor[S](implicit elementType: DataTypeFor[S]): DataTypeFor[Iterable[S]] = {
    new DataTypeFor[Iterable[S]] {
      override def dataType: DataType = ArrayType(elementType.dataType)
    }
  }

  implicit def ListFor[S](implicit elementType: DataTypeFor[S]): DataTypeFor[List[S]] = {
    new DataTypeFor[List[S]] {
      override def dataType: DataType = ArrayType(elementType.dataType)
    }
  }

  implicit def SetFor[S](implicit elementType: DataTypeFor[S]): DataTypeFor[Set[S]] = {
    new DataTypeFor[Set[S]] {
      override def dataType: DataType = ArrayType(elementType.dataType)
    }
  }

  implicit def VectorFor[S](implicit elementType: DataTypeFor[S]): DataTypeFor[Vector[S]] = {
    new DataTypeFor[Vector[S]] {
      override def dataType: DataType = ArrayType(elementType.dataType)
    }
  }

  implicit def SeqFor[S](implicit elementType: DataTypeFor[S]): DataTypeFor[Seq[S]] = new DataTypeFor[Seq[S]] {
    override def dataType: DataType = ArrayType(elementType.dataType)
  }

  implicit def Tuple2ToSchema[A, B](implicit a: DataTypeFor[A], b: DataTypeFor[B]) = new DataTypeFor[(A, B)] {
    override def dataType: DataType = StructType(
      "",
      "tuple2",
      "",
      Nil,
      Seq(
        StructField("_1", a.dataType),
        StructField("_2", b.dataType)
      )
    )
  }

  implicit def Tuple3ToSchema[A, B, C](implicit
                                       a: DataTypeFor[A],
                                       b: DataTypeFor[B],
                                       c: DataTypeFor[C]) = new DataTypeFor[(A, B, C)] {
    override def dataType: DataType = StructType(
      "",
      "tuple3",
      "",
      Nil,
      Seq(
        StructField("_1", a.dataType),
        StructField("_2", b.dataType),
        StructField("_3", c.dataType)
      )
    )
  }

  implicit def MapFor[V](implicit valueType: DataTypeFor[V]): DataTypeFor[Map[String, V]] = {
    new DataTypeFor[Map[String, V]] {
      override def dataType: DataType = MapType(StringType, valueType.dataType)
    }
  }

  implicit object TimestampFor extends DataTypeFor[Timestamp] {
    override def dataType: DataType = TimestampType
  }

  implicit object LocalDateFor extends DataTypeFor[LocalDate] {
    override def dataType: DataType = LocalDateType
  }

  implicit object LocalDateTimeFor extends DataTypeFor[LocalDateTime] {
    override def dataType: DataType = LocalDateTimeType
  }

  // This DataTypeFor is used for sealed traits of objects
  implicit def genTraitObjectEnum[T, C <: Coproduct, L <: HList](implicit ct: ClassTag[T],
                                                                 tag: TypeTag[T],
                                                                 gen: Generic.Aux[T, C],
                                                                 objs: Reify.Aux[C, L],
                                                                 toList: ToList[L, T]): DataTypeFor[T] = new DataTypeFor[T] {
    override val dataType: DataType = {
      val tpe = weakTypeTag[T]
      val annos = tpe.tpe.typeSymbol.annotations.map { a =>
        val name = a.tree.tpe.typeSymbol.fullName
        val args = a.tree.children.tail.map(_.toString.stripPrefix("\"").stripSuffix("\""))
        Anno(name, args)
      }
      val className = ct.runtimeClass.getCanonicalName
      val packageName = ct.runtimeClass.getPackage.getName
      val simpleName = ct.runtimeClass.getSimpleName
      val symbols = toList(objs()).map(_.toString)
      EnumType(className, simpleName, packageName, symbols, annos)
    }
  }

}