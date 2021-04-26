package hw4

import chisel3._
import chisel3.util._


// A (m x k) X B (k x n) = C (m x k)
case class MatMulParams(m: Int, k: Int, n: Int, parallelism: Int = 1) {
  val aRows = m
  val aCols = k
  val bRows = k
  val bCols = n
  val cRows = m
  val cCols = n
  val w = 32.W
}


class MatMulIO(p: MatMulParams) extends Bundle {
  val in = Flipped(Decoupled(new Bundle {
    val a = Vec(p.aRows, Vec(p.aCols, SInt(p.w)))
    val b = Vec(p.bRows, Vec(p.bCols, SInt(p.w)))
  }))
  val out = Valid(Vec(p.cRows, Vec(p.cCols, SInt(p.w))))
  override def cloneType = (new MatMulIO(p)).asInstanceOf[this.type]
}


class MatMul(p: MatMulParams) extends Module {
  require(p.cCols >= p.parallelism)
  require(p.cCols % p.parallelism == 0)
  val io = IO(new MatMulIO(p))
  // BEGIN SOLUTION
  ???
}
