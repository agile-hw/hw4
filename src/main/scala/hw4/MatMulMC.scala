package hw4

import chisel3._
import chisel3.util._


class MatMulMC(p: MatMulParams) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new Bundle {
      val aBlock = Vec(p.aElementsPerTransfer, SInt(p.w))
      val bBlock = Vec(p.bElementsPerTransfer, SInt(p.w))
    }))
    val outBlock = Valid(Vec(p.cElementsPerTransfer, SInt(p.w)))
  })

  // State Declaration
  val a = Reg(Vec(p.aRows, Vec(p.aCols, SInt(p.w))))
  val b = Reg(Vec(p.bRows, Vec(p.bCols, SInt(p.w))))
  val c = Reg(Vec(p.cRows, Vec(p.cCols, SInt(p.w))))

  // BEGIN SOLUTION
  ???
}
