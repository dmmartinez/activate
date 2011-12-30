package net.fwbrasil.activate.util

import scala.runtime._
import org.objenesis._
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.GenericArrayType
import org.reflections.Reflections
import scala.collection.mutable.{ Map => MutableMap }
import java.util.IdentityHashMap
//import scala.tools.scalap.scalax.rules.scalasig._
//import tools.scalap.scalax.rules.scalasig._

class Reflection(val clazz: Class[_]) {
	def publicMethods = clazz.getMethods
}

object Reflection {

	val objenesis = new ObjenesisStd(false);

	def newInstance[T](clazz: Class[_]): T =
		objenesis.newInstance(clazz).asInstanceOf[T]
	
	def getDeclaredFieldsIncludingSuperClasses(concreteClass: Class[_]) = {
		var clazz = concreteClass
		var fields = List[Field]()
		do {
			fields ++= clazz.getDeclaredFields()
			clazz = clazz.getSuperclass()
		} while (clazz != null)
		fields
	}
	
	def getDeclaredMethodsIncludingSuperClasses(concreteClass: Class[_]) = {
		var clazz = concreteClass
		var methods = List[Method]()
		do {
			methods ++= clazz.getDeclaredMethods()
			clazz = clazz.getSuperclass()
		} while (clazz != null)
		methods
	}

	def set(obj: Object, fieldName: String, value: Object) = {
		val field = getDeclaredFieldsIncludingSuperClasses(obj.getClass()).filter(_.getName() == fieldName).head
		field.setAccessible(true)
		field.set(obj, value)
	}
	
	def get(obj: Object, fieldName: String) = {
		val fields = getDeclaredFieldsIncludingSuperClasses(obj.getClass())
		val field = fields.filter(_.getName() == fieldName).head
		field.setAccessible(true)
		field.get(obj)
	}
	
	def getStatic(obj: Class[_], fieldName: String) = {
		val field = getDeclaredFieldsIncludingSuperClasses(obj).filter(_.getName() == fieldName).head
		field.setAccessible(true)
		field.get(obj)
	}
	
	def invoke(obj: Object, methodName: String, params: Object*) = {
		val clazz = obj.getClass
		val method = clazz.getDeclaredMethods().filter(_.toString().contains(methodName)).head
		method.setAccessible(true)
		method.invoke(obj, params: _*)
	}
	
	def getAllImplementorsNames(interfaceName: String) =
		Set(new Reflections("").getStore.getSubTypesOf(interfaceName).toArray: _*).asInstanceOf[Set[String]]
	
	def findObject[R](obj: T forSome { type T <:  Any })(f: (Any) => Boolean): Set[R] = {
		(if(f(obj))
			Set(obj)
		else
			obj match {
				case seq: Seq[Any] =>
					(for(value <- seq)
						yield findObject(value)(f)).flatten.toSet
				case obj: Product =>
					(for(elem <- obj.productElements.toList)
						yield findObject(elem)(f)).flatten.toSet
				case other =>
					Set()
					
			}).asInstanceOf[Set[R]]
	}
	
	def deepCopyMapping[T, A <: Any, B <: Any](obj: T, map: IdentityHashMap[A, B]): T = {
		val substitute = map.get(obj.asInstanceOf[A])
		if(substitute != null) {
			val newMap = new IdentityHashMap[A, B]()
			newMap.putAll(map)
			newMap.remove(obj)
			deepCopyMapping(substitute.asInstanceOf[T], newMap)
		} else 
			(obj match {
				case seq: Seq[Any] =>
					for(elem <- seq; if(elem != Nil))
						yield deepCopyMapping(elem, map)
				case obj: Product =>
					val values =
					for(elem <- obj.productElements.toList)
						yield deepCopyMapping(elem, map)
					val constructor = obj.getClass.getConstructors().head
					val newInstance = constructor.newInstance(values.asInstanceOf[Seq[Object]]: _*)
					map.put(obj.asInstanceOf[A], newInstance.asInstanceOf[B])
					newInstance
				case other =>
					other
			}).asInstanceOf[T]
	}
	
	def getObject[T](clazz: Class[_]) = {
		clazz.getField("MODULE$").get(clazz).asInstanceOf[T]
	}
//	
//	val ByteClass = classOf[scala.Byte]
//	val ShortClass = classOf[scala.Short]
//	val CharClass = classOf[scala.Char]
//	val IntClass = classOf[scala.Int]
//	val LongClass = classOf[scala.Long]
//	val FloatClass = classOf[scala.Float]
//	val DoubleClass = classOf[scala.Double]
//	val BooleanClass = classOf[scala.Boolean]
//	val NullClass = classOf[scala.Null]
//	val UnitClass = classOf[scala.Unit]
//
//	def getScalaSig(clazz: Class[_]): ClassSymbol = {
//		val split = clazz.getName.split('$')
//		val baseClass = Class.forName(split(0))
//		val sigOption = ScalaSigParser.parse(baseClass)
//		if (sigOption == None)
//			throw new IllegalStateException("Scala signature not found for class {0} using base class {1}.".format(clazz, baseClass))
//		val topLevelClasses = sigOption.get.topLevelClasses.headOption
//		val sigBaseClass = topLevelClasses.filter(_.name == baseClass.getSimpleName).headOption.getOrElse({
//			sigOption.get.topLevelObjects.filter(_.name == baseClass.getSimpleName).headOption.map(_.infoType.asInstanceOf[TypeRefType].symbol).get
//		})
//		var sigClazz = sigBaseClass.asInstanceOf[ClassSymbol]
//		for (sigInner <- split.tail) {
//			sigClazz = getScalaSig(sigClazz, sigInner).get
//		}
//		sigClazz
//	}
//	
//	def getScalaSig(baseSymbol: Symbol, className: String): Option[ClassSymbol] = {
//		val children = baseSymbol.children
//		val foundOption = findClassSymbol(className, children)
//		if(foundOption != None)
//			foundOption
//		else 
//			(for(symbol <- children)
//				yield getScalaSig(symbol, className)).filter(!_.isEmpty).headOption.getOrElse(None)
//	}
//	
//	def findClassSymbol(className: String, symbolList: Seq[Symbol]): Option[ClassSymbol] =
//		symbolList.filter((sig: Symbol) => sig.name == className && sig.isInstanceOf[ClassSymbol]).headOption.asInstanceOf[Option[ClassSymbol]]
//
//	// TODO reimplementar, esta especifico para os tipos atuais de atributos
//	def getEntityFieldTypeArgument(sig: => ClassSymbol, field: Field) = {
//		val genericType = field.getGenericType
//		val arguments = genericType.asInstanceOf[ParameterizedType].getActualTypeArguments
//		if (arguments.size != 1)
//			throw new IllegalStateException("There should be only one type argument")
//		val jclazz = arguments(0) match {
//			case genericArrayType: GenericArrayType =>
//				classOf[Array[Byte]]
//			case parameterizedType: ParameterizedType =>
//				parameterizedType.getRawType.asInstanceOf[Class[_]]
//			case clazz: Class[_] =>
//				clazz
//		}
//		if (jclazz == classOf[java.lang.Object]) {
//			val method = sig.children.filter(_.name == field.getName).head.asInstanceOf[MethodSymbol]
//			val a = method.infoType.asInstanceOf[NullaryMethodType]
//			val r = a.resultType.asInstanceOf[TypeRefType]
//			val x = r.typeArgs.head.asInstanceOf[TypeRefType].symbol.toString
//			x match {
//				case "scala.Byte" => classOf[Byte]
//				case "scala.Short" => classOf[Short]
//				case "scala.Char" => classOf[Char]
//				case "scala.Int" => classOf[Int]
//				case "scala.Long" => classOf[Long]
//				case "scala.Float" => classOf[Float]
//				case "scala.Double" => classOf[Double]
//				case "scala.Boolean" => classOf[Boolean]
//				case x => Class.forName(x)
//			}
//		} else jclazz
//	}
}