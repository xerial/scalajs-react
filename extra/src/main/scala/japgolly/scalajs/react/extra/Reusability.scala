package japgolly.scalajs.react.extra

import scala.annotation.tailrec
import japgolly.scalajs.react._
import japgolly.scalajs.react.macros.ReusabilityMacros
import ComponentScope.DuringCallbackM

/**
 * Tests whether one instance can be used in place of another.
 * Used mostly to compare properties and state of a component to avoid unnecessary updates.
 *
 * If you imagine a class with 8 fields, equality would compare all 8 fields where as this would typically just compare
 * the ID field, the update-date, or the revision number.
 * You might think of this as a very quick version of equality.
 *
 * Don't miss `Reusability.shouldComponentUpdate` which can be applied to a component via `ReactComponentB.configure`.
 *
 * @since 0.9.0
 */
final class Reusability[A](val test: (A, A) => Boolean) extends AnyVal {
  def contramap[B](f: B => A): Reusability[B] =
    new Reusability((x, y) => test(f(x), f(y)))

  def narrow[B <: A]: Reusability[B] =
    new Reusability[B](test)

  def testNot: (A, A) => Boolean =
    !test(_, _)

  def ||[B <: A](tryNext: Reusability[B]): Reusability[B] =
    Reusability.fn[B]((x, y) => test(x, y) || tryNext.test(x, y))

  def &&[B <: A](tryNext: Reusability[B]): Reusability[B] =
    Reusability.fn[B]((x, y) => test(x, y) && tryNext.test(x, y))
}

object Reusability {
  @inline def apply[A](implicit r: Reusability[A]): Reusability[A] = r

  def fn[A](f: (A, A) => Boolean): Reusability[A] =
    new Reusability(f)

  def const[A](r: Boolean): Reusability[A] =
    new Reusability((_, _) => r)

  def always[A]: Reusability[A] =
    const(true)

  def never[A]: Reusability[A] =
    const(false)

  /** Compare by reference. Reuse if both values are the same instance. */
  def byRef[A <: AnyRef]: Reusability[A] =
    new Reusability((a, b) => a eq b)

  /** Compare using universal equality (Scala's == operator). */
  def by_==[A]: Reusability[A] =
    new Reusability((a, b) => a == b)

  /** Compare by reference and if different, compare using universal equality (Scala's == operator). */
  def byRefOr_==[A <: AnyRef]: Reusability[A] =
    byRef[A] || by_==[A]

  def by[A, B](f: A => B)(implicit r: Reusability[B]): Reusability[A] =
    r contramap f

  def byIterator[I[X] <: Iterable[X], A](implicit r: Reusability[A]): Reusability[I[A]] =
    fn { (x, y) =>
      val i = x.iterator
      val j = y.iterator
      @tailrec
      def go: Boolean = {
        val hasNext = i.hasNext
        if (hasNext != j.hasNext)
          false
        else if (!hasNext)
          true
        else if (i.next() ~/~ j.next())
          false
        else
          go
      }
      go
    }

  def asIndexedSeq[S[X] <: IndexedSeq[X], A](implicit r: Reusability[A]): Reusability[S[A]] =
    fn((x, y) =>
      (x.length == y.length) && x.indices.forall(i => r.test(x(i), y(i))))

  def internal[A, B](f: A => B)(r: A => Reusability[B]): Reusability[A] =
    fn((a1, a2) => {
      val b1 = f(a1)
      val b2 = f(a2)
      r(a1).test(b1, b2) && r(a2).test(b1, b2)
    })

  /**
   * Generate an instance for a case class by comparing each case field.
   *
   * @tparam A The case class type.
   */
  def caseClass[A]: Reusability[A] =
    macro ReusabilityMacros.quietCaseClass[A]

  /**
   * Same as [[caseClass]] except the code generated by the macro is printed to stdout.
   */
  def caseClassDebug[A]: Reusability[A] =
    macro ReusabilityMacros.debugCaseClass[A]

  // -------------------------------------------------------------------------------------------------------------------
  // Instances

  @inline implicit def reusableUnit   : Reusability[Unit   ] = always
  @inline implicit def reusableBoolean: Reusability[Boolean] = by_==
  @inline implicit def reusableByte   : Reusability[Byte   ] = by_==
  @inline implicit def reusableChar   : Reusability[Char   ] = by_==
  @inline implicit def reusableShort  : Reusability[Short  ] = by_==
  @inline implicit def reusableInt    : Reusability[Int    ] = by_==
  @inline implicit def reusableLong   : Reusability[Long   ] = by_==
  @inline implicit def reusableString : Reusability[String ] = by_==
//@inline implicit def reusableFloat  : Reusability[Float  ] = by_==
//@inline implicit def reusableDouble : Reusability[Double ] = by_==

  implicit def optionLike[O[_], A](implicit o: OptionLike[O], r: Reusability[A]): Reusability[O[A]] =
    fn((x, y) =>
      o.fold(x, o isEmpty y)(xa =>
        o.fold(y, false)(ya =>
          xa ~=~ ya)))

  implicit def either[A: Reusability, B: Reusability]: Reusability[Either[A, B]] =
    fn((x, y) =>
      x.fold[Boolean](
        a => y.fold(a ~=~ _, _ => false),
        b => y.fold(_ => false, b ~=~ _)))

  implicit def reusabilityList[A](implicit r: Reusability[A]): Reusability[List[A]] =
    byRef[List[A]] || byIterator[List, A]

  implicit def reusabilityVector[A](implicit r: Reusability[A]): Reusability[Vector[A]] =
    byRef[Vector[A]] || asIndexedSeq[Vector, A]

  implicit def reusabilitySet[A](implicit r: Reusability[A]): Reusability[Set[A]] =
    byRefOr_== // universal equality must hold for Sets

  // Prohibited:
  // ===========
  // Array  - it's mutable. Reusability & mutability are incompatible.
  // Stream - it's lazy. Reusability & non-strictness are incompatible.

  // Generated by bin/gen-reusable

  implicit def tuple2[A:Reusability, B:Reusability]: Reusability[(A,B)] =
    fn((x,y) ⇒ (x._1 ~=~ y._1) && (x._2 ~=~ y._2))

  implicit def tuple3[A:Reusability, B:Reusability, C:Reusability]: Reusability[(A,B,C)] =
    fn((x,y) ⇒ (x._1 ~=~ y._1) && (x._2 ~=~ y._2) && (x._3 ~=~ y._3))

  implicit def tuple4[A:Reusability, B:Reusability, C:Reusability, D:Reusability]: Reusability[(A,B,C,D)] =
    fn((x,y) ⇒ (x._1 ~=~ y._1) && (x._2 ~=~ y._2) && (x._3 ~=~ y._3) && (x._4 ~=~ y._4))

  implicit def tuple5[A:Reusability, B:Reusability, C:Reusability, D:Reusability, E:Reusability]: Reusability[(A,B,C,D,E)] =
    fn((x,y) ⇒ (x._1 ~=~ y._1) && (x._2 ~=~ y._2) && (x._3 ~=~ y._3) && (x._4 ~=~ y._4) && (x._5 ~=~ y._5))

  implicit def tuple6[A:Reusability, B:Reusability, C:Reusability, D:Reusability, E:Reusability, F:Reusability]: Reusability[(A,B,C,D,E,F)] =
    fn((x,y) ⇒ (x._1 ~=~ y._1) && (x._2 ~=~ y._2) && (x._3 ~=~ y._3) && (x._4 ~=~ y._4) && (x._5 ~=~ y._5) && (x._6 ~=~ y._6))

  implicit def tuple7[A:Reusability, B:Reusability, C:Reusability, D:Reusability, E:Reusability, F:Reusability, G:Reusability]: Reusability[(A,B,C,D,E,F,G)] =
    fn((x,y) ⇒ (x._1 ~=~ y._1) && (x._2 ~=~ y._2) && (x._3 ~=~ y._3) && (x._4 ~=~ y._4) && (x._5 ~=~ y._5) && (x._6 ~=~ y._6) && (x._7 ~=~ y._7))

  implicit def tuple8[A:Reusability, B:Reusability, C:Reusability, D:Reusability, E:Reusability, F:Reusability, G:Reusability, H:Reusability]: Reusability[(A,B,C,D,E,F,G,H)] =
    fn((x,y) ⇒ (x._1 ~=~ y._1) && (x._2 ~=~ y._2) && (x._3 ~=~ y._3) && (x._4 ~=~ y._4) && (x._5 ~=~ y._5) && (x._6 ~=~ y._6) && (x._7 ~=~ y._7) && (x._8 ~=~ y._8))

  implicit def tuple9[A:Reusability, B:Reusability, C:Reusability, D:Reusability, E:Reusability, F:Reusability, G:Reusability, H:Reusability, I:Reusability]: Reusability[(A,B,C,D,E,F,G,H,I)] =
    fn((x,y) ⇒ (x._1 ~=~ y._1) && (x._2 ~=~ y._2) && (x._3 ~=~ y._3) && (x._4 ~=~ y._4) && (x._5 ~=~ y._5) && (x._6 ~=~ y._6) && (x._7 ~=~ y._7) && (x._8 ~=~ y._8) && (x._9 ~=~ y._9))

  implicit def tuple10[A:Reusability, B:Reusability, C:Reusability, D:Reusability, E:Reusability, F:Reusability, G:Reusability, H:Reusability, I:Reusability, J:Reusability]: Reusability[(A,B,C,D,E,F,G,H,I,J)] =
    fn((x,y) ⇒ (x._1 ~=~ y._1) && (x._2 ~=~ y._2) && (x._3 ~=~ y._3) && (x._4 ~=~ y._4) && (x._5 ~=~ y._5) && (x._6 ~=~ y._6) && (x._7 ~=~ y._7) && (x._8 ~=~ y._8) && (x._9 ~=~ y._9) && (x._10 ~=~ y._10))

  implicit def tuple11[A:Reusability, B:Reusability, C:Reusability, D:Reusability, E:Reusability, F:Reusability, G:Reusability, H:Reusability, I:Reusability, J:Reusability, K:Reusability]: Reusability[(A,B,C,D,E,F,G,H,I,J,K)] =
    fn((x,y) ⇒ (x._1 ~=~ y._1) && (x._2 ~=~ y._2) && (x._3 ~=~ y._3) && (x._4 ~=~ y._4) && (x._5 ~=~ y._5) && (x._6 ~=~ y._6) && (x._7 ~=~ y._7) && (x._8 ~=~ y._8) && (x._9 ~=~ y._9) && (x._10 ~=~ y._10) && (x._11 ~=~ y._11))

  implicit def tuple12[A:Reusability, B:Reusability, C:Reusability, D:Reusability, E:Reusability, F:Reusability, G:Reusability, H:Reusability, I:Reusability, J:Reusability, K:Reusability, L:Reusability]: Reusability[(A,B,C,D,E,F,G,H,I,J,K,L)] =
    fn((x,y) ⇒ (x._1 ~=~ y._1) && (x._2 ~=~ y._2) && (x._3 ~=~ y._3) && (x._4 ~=~ y._4) && (x._5 ~=~ y._5) && (x._6 ~=~ y._6) && (x._7 ~=~ y._7) && (x._8 ~=~ y._8) && (x._9 ~=~ y._9) && (x._10 ~=~ y._10) && (x._11 ~=~ y._11) && (x._12 ~=~ y._12))

  implicit def tuple13[A:Reusability, B:Reusability, C:Reusability, D:Reusability, E:Reusability, F:Reusability, G:Reusability, H:Reusability, I:Reusability, J:Reusability, K:Reusability, L:Reusability, M:Reusability]: Reusability[(A,B,C,D,E,F,G,H,I,J,K,L,M)] =
    fn((x,y) ⇒ (x._1 ~=~ y._1) && (x._2 ~=~ y._2) && (x._3 ~=~ y._3) && (x._4 ~=~ y._4) && (x._5 ~=~ y._5) && (x._6 ~=~ y._6) && (x._7 ~=~ y._7) && (x._8 ~=~ y._8) && (x._9 ~=~ y._9) && (x._10 ~=~ y._10) && (x._11 ~=~ y._11) && (x._12 ~=~ y._12) && (x._13 ~=~ y._13))

  implicit def tuple14[A:Reusability, B:Reusability, C:Reusability, D:Reusability, E:Reusability, F:Reusability, G:Reusability, H:Reusability, I:Reusability, J:Reusability, K:Reusability, L:Reusability, M:Reusability, N:Reusability]: Reusability[(A,B,C,D,E,F,G,H,I,J,K,L,M,N)] =
    fn((x,y) ⇒ (x._1 ~=~ y._1) && (x._2 ~=~ y._2) && (x._3 ~=~ y._3) && (x._4 ~=~ y._4) && (x._5 ~=~ y._5) && (x._6 ~=~ y._6) && (x._7 ~=~ y._7) && (x._8 ~=~ y._8) && (x._9 ~=~ y._9) && (x._10 ~=~ y._10) && (x._11 ~=~ y._11) && (x._12 ~=~ y._12) && (x._13 ~=~ y._13) && (x._14 ~=~ y._14))

  implicit def tuple15[A:Reusability, B:Reusability, C:Reusability, D:Reusability, E:Reusability, F:Reusability, G:Reusability, H:Reusability, I:Reusability, J:Reusability, K:Reusability, L:Reusability, M:Reusability, N:Reusability, O:Reusability]: Reusability[(A,B,C,D,E,F,G,H,I,J,K,L,M,N,O)] =
    fn((x,y) ⇒ (x._1 ~=~ y._1) && (x._2 ~=~ y._2) && (x._3 ~=~ y._3) && (x._4 ~=~ y._4) && (x._5 ~=~ y._5) && (x._6 ~=~ y._6) && (x._7 ~=~ y._7) && (x._8 ~=~ y._8) && (x._9 ~=~ y._9) && (x._10 ~=~ y._10) && (x._11 ~=~ y._11) && (x._12 ~=~ y._12) && (x._13 ~=~ y._13) && (x._14 ~=~ y._14) && (x._15 ~=~ y._15))

  implicit def tuple16[A:Reusability, B:Reusability, C:Reusability, D:Reusability, E:Reusability, F:Reusability, G:Reusability, H:Reusability, I:Reusability, J:Reusability, K:Reusability, L:Reusability, M:Reusability, N:Reusability, O:Reusability, P:Reusability]: Reusability[(A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P)] =
    fn((x,y) ⇒ (x._1 ~=~ y._1) && (x._2 ~=~ y._2) && (x._3 ~=~ y._3) && (x._4 ~=~ y._4) && (x._5 ~=~ y._5) && (x._6 ~=~ y._6) && (x._7 ~=~ y._7) && (x._8 ~=~ y._8) && (x._9 ~=~ y._9) && (x._10 ~=~ y._10) && (x._11 ~=~ y._11) && (x._12 ~=~ y._12) && (x._13 ~=~ y._13) && (x._14 ~=~ y._14) && (x._15 ~=~ y._15) && (x._16 ~=~ y._16))

  implicit def tuple17[A:Reusability, B:Reusability, C:Reusability, D:Reusability, E:Reusability, F:Reusability, G:Reusability, H:Reusability, I:Reusability, J:Reusability, K:Reusability, L:Reusability, M:Reusability, N:Reusability, O:Reusability, P:Reusability, Q:Reusability]: Reusability[(A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q)] =
    fn((x,y) ⇒ (x._1 ~=~ y._1) && (x._2 ~=~ y._2) && (x._3 ~=~ y._3) && (x._4 ~=~ y._4) && (x._5 ~=~ y._5) && (x._6 ~=~ y._6) && (x._7 ~=~ y._7) && (x._8 ~=~ y._8) && (x._9 ~=~ y._9) && (x._10 ~=~ y._10) && (x._11 ~=~ y._11) && (x._12 ~=~ y._12) && (x._13 ~=~ y._13) && (x._14 ~=~ y._14) && (x._15 ~=~ y._15) && (x._16 ~=~ y._16) && (x._17 ~=~ y._17))

  implicit def tuple18[A:Reusability, B:Reusability, C:Reusability, D:Reusability, E:Reusability, F:Reusability, G:Reusability, H:Reusability, I:Reusability, J:Reusability, K:Reusability, L:Reusability, M:Reusability, N:Reusability, O:Reusability, P:Reusability, Q:Reusability, R:Reusability]: Reusability[(A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R)] =
    fn((x,y) ⇒ (x._1 ~=~ y._1) && (x._2 ~=~ y._2) && (x._3 ~=~ y._3) && (x._4 ~=~ y._4) && (x._5 ~=~ y._5) && (x._6 ~=~ y._6) && (x._7 ~=~ y._7) && (x._8 ~=~ y._8) && (x._9 ~=~ y._9) && (x._10 ~=~ y._10) && (x._11 ~=~ y._11) && (x._12 ~=~ y._12) && (x._13 ~=~ y._13) && (x._14 ~=~ y._14) && (x._15 ~=~ y._15) && (x._16 ~=~ y._16) && (x._17 ~=~ y._17) && (x._18 ~=~ y._18))

  implicit def tuple19[A:Reusability, B:Reusability, C:Reusability, D:Reusability, E:Reusability, F:Reusability, G:Reusability, H:Reusability, I:Reusability, J:Reusability, K:Reusability, L:Reusability, M:Reusability, N:Reusability, O:Reusability, P:Reusability, Q:Reusability, R:Reusability, S:Reusability]: Reusability[(A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S)] =
    fn((x,y) ⇒ (x._1 ~=~ y._1) && (x._2 ~=~ y._2) && (x._3 ~=~ y._3) && (x._4 ~=~ y._4) && (x._5 ~=~ y._5) && (x._6 ~=~ y._6) && (x._7 ~=~ y._7) && (x._8 ~=~ y._8) && (x._9 ~=~ y._9) && (x._10 ~=~ y._10) && (x._11 ~=~ y._11) && (x._12 ~=~ y._12) && (x._13 ~=~ y._13) && (x._14 ~=~ y._14) && (x._15 ~=~ y._15) && (x._16 ~=~ y._16) && (x._17 ~=~ y._17) && (x._18 ~=~ y._18) && (x._19 ~=~ y._19))

  implicit def tuple20[A:Reusability, B:Reusability, C:Reusability, D:Reusability, E:Reusability, F:Reusability, G:Reusability, H:Reusability, I:Reusability, J:Reusability, K:Reusability, L:Reusability, M:Reusability, N:Reusability, O:Reusability, P:Reusability, Q:Reusability, R:Reusability, S:Reusability, T:Reusability]: Reusability[(A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T)] =
    fn((x,y) ⇒ (x._1 ~=~ y._1) && (x._2 ~=~ y._2) && (x._3 ~=~ y._3) && (x._4 ~=~ y._4) && (x._5 ~=~ y._5) && (x._6 ~=~ y._6) && (x._7 ~=~ y._7) && (x._8 ~=~ y._8) && (x._9 ~=~ y._9) && (x._10 ~=~ y._10) && (x._11 ~=~ y._11) && (x._12 ~=~ y._12) && (x._13 ~=~ y._13) && (x._14 ~=~ y._14) && (x._15 ~=~ y._15) && (x._16 ~=~ y._16) && (x._17 ~=~ y._17) && (x._18 ~=~ y._18) && (x._19 ~=~ y._19) && (x._20 ~=~ y._20))

  implicit def tuple21[A:Reusability, B:Reusability, C:Reusability, D:Reusability, E:Reusability, F:Reusability, G:Reusability, H:Reusability, I:Reusability, J:Reusability, K:Reusability, L:Reusability, M:Reusability, N:Reusability, O:Reusability, P:Reusability, Q:Reusability, R:Reusability, S:Reusability, T:Reusability, U:Reusability]: Reusability[(A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U)] =
    fn((x,y) ⇒ (x._1 ~=~ y._1) && (x._2 ~=~ y._2) && (x._3 ~=~ y._3) && (x._4 ~=~ y._4) && (x._5 ~=~ y._5) && (x._6 ~=~ y._6) && (x._7 ~=~ y._7) && (x._8 ~=~ y._8) && (x._9 ~=~ y._9) && (x._10 ~=~ y._10) && (x._11 ~=~ y._11) && (x._12 ~=~ y._12) && (x._13 ~=~ y._13) && (x._14 ~=~ y._14) && (x._15 ~=~ y._15) && (x._16 ~=~ y._16) && (x._17 ~=~ y._17) && (x._18 ~=~ y._18) && (x._19 ~=~ y._19) && (x._20 ~=~ y._20) && (x._21 ~=~ y._21))

  implicit def tuple22[A:Reusability, B:Reusability, C:Reusability, D:Reusability, E:Reusability, F:Reusability, G:Reusability, H:Reusability, I:Reusability, J:Reusability, K:Reusability, L:Reusability, M:Reusability, N:Reusability, O:Reusability, P:Reusability, Q:Reusability, R:Reusability, S:Reusability, T:Reusability, U:Reusability, V:Reusability]: Reusability[(A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,V)] =
    fn((x,y) ⇒ (x._1 ~=~ y._1) && (x._2 ~=~ y._2) && (x._3 ~=~ y._3) && (x._4 ~=~ y._4) && (x._5 ~=~ y._5) && (x._6 ~=~ y._6) && (x._7 ~=~ y._7) && (x._8 ~=~ y._8) && (x._9 ~=~ y._9) && (x._10 ~=~ y._10) && (x._11 ~=~ y._11) && (x._12 ~=~ y._12) && (x._13 ~=~ y._13) && (x._14 ~=~ y._14) && (x._15 ~=~ y._15) && (x._16 ~=~ y._16) && (x._17 ~=~ y._17) && (x._18 ~=~ y._18) && (x._19 ~=~ y._19) && (x._20 ~=~ y._20) && (x._21 ~=~ y._21) && (x._22 ~=~ y._22))

  // ===================================================================================================================

  def shouldComponentUpdate[P: Reusability, S: Reusability, B, N <: TopNode] =
    (_: ReactComponentB[P, S, B, N]).shouldComponentUpdate(($, p, s) =>
      ($.props ~/~ p) || ($.state ~/~ s))

  def shouldComponentUpdateAnd[P: Reusability, S: Reusability, B, N <: TopNode](f: (DuringCallbackM[P, S, B, N], P, Boolean, S, Boolean) => Callback) =
    (_: ReactComponentB[P, S, B, N]).shouldComponentUpdateCB(($, p2, s2) => CallbackTo {
      val up = $.props ~/~ p2
      val us = $.state ~/~ s2
      f($, p2, up, s2, us).runNow()
      up || us
    })

  def shouldComponentUpdateAndLog[P: Reusability, S: Reusability, B, N <: TopNode](name: String) =
    shouldComponentUpdateAnd[P, S, B, N](($, p2, p, s2, s) => Callback {
      val p1 = $.props
      val s1 = $.state
      println(s"$name.shouldComponentUpdate = ${p || s}\n  Props: $p. [$p1] ⇒ [$p2]\n  State: $s. [$s1] ⇒ [$s2]")
    })

  def shouldComponentUpdateWithOverlay[P: Reusability, S: Reusability, B, N <: TopNode] =
    ReusabilityOverlay.install[P, S, B, N](DefaultReusabilityOverlay.defaults)
}
