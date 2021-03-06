/*
 * Copyright 2007-2010 WorldWide Conferencing, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.liftweb {
package record {
package field {

import scala.reflect.Manifest
import scala.xml._
import net.liftweb.common._
import net.liftweb.http.js._
import net.liftweb.http.{S, SHtml}
import net.liftweb.json.JsonAST.{JInt, JNothing, JNull, JString, JValue}
import net.liftweb.util._
import Box.option2Box
import S._
import Helpers._
import JE._


class EnumField[OwnerType <: Record[OwnerType], EnumType <: Enumeration](rec: OwnerType, enum: EnumType)(implicit m: Manifest[EnumType#Value])
  extends Field[EnumType#Value, OwnerType]
{
  def this(rec: OwnerType, enum: EnumType, value: EnumType#Value)(implicit m: Manifest[EnumType#Value]) = {
    this(rec, enum)
    set(value)
  }

  def this(rec: OwnerType, enum: EnumType, value: Box[EnumType#Value])(implicit m: Manifest[EnumType#Value]) = {
    this(rec, enum)
    setBox(value)
  }

  def owner = rec

  def toInt: Box[Int] = valueBox.map(_.id)

  def fromInt(in: Int): Box[EnumType#Value] = tryo(enum(in))

  def setFromAny(in: Any): Box[EnumType#Value] = in match {
    case     (value: Int)    => setBox(fromInt(value))
    case Some(value: Int)    => setBox(fromInt(value))
    case Full(value: Int)    => setBox(fromInt(value))
    case (value: Int)::_     => setBox(fromInt(value))
    case     (value: Number) => setBox(fromInt(value.intValue))
    case Some(value: Number) => setBox(fromInt(value.intValue))
    case Full(value: Number) => setBox(fromInt(value.intValue))
    case (value: Number)::_  => setBox(fromInt(value.intValue))
    case _                   => genericSetFromAny(in)
  }

  def setFromString(s: String): Box[EnumType#Value] = setBox(asInt(s).flatMap(fromInt))

  /** Label for the selection item representing Empty, show when this field is optional. Defaults to the empty string. */
  def emptyOptionLabel: String = ""

  /**
   * Build a list of (value, label) options for a select list.  Return a tuple of (Box[String], String) where the first string
   * is the value of the field and the second string is the Text name of the Value.
   */
  def buildDisplayList: List[(Box[EnumType#Value], String)] = {
    val options = enum.map(a => (Full(a), a.toString)).toList
    if (optional_?) (Empty, emptyOptionLabel)::options else options
  }
          

  private def elem = SHtml.selectObj[Box[EnumType#Value]](buildDisplayList, Full(valueBox), setBox(_)) % ("tabindex" -> tabIndex.toString)

  def toForm = {
    var el = elem

    uniqueFieldId match {
      case Full(id) =>
        <div id={id+"_holder"}><div><label for={id+"_field"}>{displayName}</label></div>{el % ("id" -> (id+"_field"))}<lift:msg id={id}/></div>
      case _ => <div>{el}</div>
    }

  }

  def asXHtml: NodeSeq = {
    var el = elem

    uniqueFieldId match {
      case Full(id) => el % ("id" -> (id+"_field"))
      case _ => el
    }
  }

  def defaultValue: EnumType#Value = enum.elements.next

  def asJs = valueBox.map(_ => Str(toString)) openOr JsNull

  def asJIntOrdinal: JValue = toInt.map(i => JInt(BigInt(i))) openOr (JNothing: JValue)
  def setFromJIntOrdinal(jvalue: JValue): Box[EnumType#Value] = jvalue match {
    case JNothing|JNull if optional_? => setBox(Empty)
    case JInt(i)                      => setBox(fromInt(i.intValue))
    case other                        => setBox(FieldHelpers.expectedA("JInt", other))
  }

  def asJStringName: JValue = valueBox.map(v => JString(v.toString)) openOr (JNothing: JValue)
  def setFromJStringName(jvalue: JValue): Box[EnumType#Value] = jvalue match {
    case JNothing|JNull if optional_? => setBox(Empty)
    case JString(s)                   => setBox(enum.valueOf(s) ?~ ("Unknown value \"" + s + "\""))
    case other                        => setBox(FieldHelpers.expectedA("JString", other))
  }

  def asJValue: JValue = asJIntOrdinal
  def setFromJValue(jvalue: JValue): Box[EnumType#Value] = setFromJIntOrdinal(jvalue)
}

import _root_.java.sql.{ResultSet, Types}
import _root_.net.liftweb.mapper.{DriverType}

/**
 * An enum field holding DB related logic
 */
abstract class DBEnumField[OwnerType <: DBRecord[OwnerType], EnumType <: Enumeration](rec: OwnerType, enum: EnumType)(implicit m: Manifest[EnumType#Value])
  extends EnumField[OwnerType, EnumType](rec, enum) with JDBCFieldFlavor[Integer]
{
  def targetSQLType = Types.VARCHAR

  /**
   * Given the driver type, return the string required to create the column in the database
   */
  def fieldCreatorString(dbType: DriverType, colName: String): String = colName + " " + dbType.enumColumnType

  def jdbcFriendly(field: String) = new _root_.java.lang.Integer(this.toInt openOr defaultValue.id)

}

}
}
}
