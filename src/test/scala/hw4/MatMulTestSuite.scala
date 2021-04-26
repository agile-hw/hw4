package hw4

import chisel3._
import chisel3.tester._
import org.scalatest.FreeSpec

import treadle._
import chisel3.tester.experimental.TestOptionBuilder._

import hw4.MatMulModel.Matrix


object MatMulTestData {
  def genIdentity(n: Int): Matrix = Seq.tabulate(n,n) { (i,j) => if (i==j) 1 else 0 }

  def genOnesRow(n: Int): Matrix = Seq(Seq.fill(n)(1))

  def genOnesCol(n: Int): Matrix = Seq.fill(n)(Seq(1))

  val in2x4  = Seq(Seq(1,2,3,4),
                   Seq(5,6,7,8))
  val in4x2  = Seq(Seq(1,2),
                   Seq(3,4),
                   Seq(5,6),
                   Seq(7,8))
  val out2x2 = Seq(Seq(50, 60),
                   Seq(114,140))
  val out4x4 = Seq(Seq(11, 14, 17, 20),
                   Seq(23, 30, 37, 44),
                   Seq(35, 46, 57, 68),
                   Seq(47, 62, 77, 92))
}


class MatMulModelTester extends FreeSpec with ChiselScalatestTester {
  "MatMulModel should multiply identity" in {
    val n = 4
    val identity4x4 = MatMulTestData.genIdentity(n)
    val p = MatMulParams(n,n,n)
    assert(MatMulModel(p, identity4x4, identity4x4) == identity4x4)
  }

  "MatMulModel should multiply identity x in4x2" in {
    assert(MatMulModel(MatMulParams(4,4,2), MatMulTestData.genIdentity(4), MatMulTestData.in4x2) == MatMulTestData.in4x2)
  }

  "MatMulModel should multiply identity x in2x4" in {
    assert(MatMulModel(MatMulParams(2,2,4), MatMulTestData.genIdentity(2), MatMulTestData.in2x4) == MatMulTestData.in2x4)
  }

  "MatMulModel should multiply in2x4 x in4x2" in {
    assert(MatMulModel(MatMulParams(2,4,2), MatMulTestData.in2x4, MatMulTestData.in4x2) == MatMulTestData.out2x2)
  }

  "MatMulModel should multiply in4x2 x in2x4" in {
    assert(MatMulModel(MatMulParams(4,2,4), MatMulTestData.in4x2, MatMulTestData.in2x4) == MatMulTestData.out4x4)
  }
}


class MatMulTester extends FreeSpec with ChiselScalatestTester {
  def doMatMulTest(a: Matrix, b: Matrix, parallelism: Int): Boolean = {
    val p = MatMulParams(a.size, a.head.size, b.head.size, parallelism)
    test(new MatMul(p)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      // load input matrices
      dut.io.in.valid.poke(true.B)
      dut.io.in.ready.expect(true.B)
      // OK for dut.io.out.valid to be true with junk data
      for (r <- 0 until p.aRows) {
        for (c <- 0 until p.aCols) {
          dut.io.in.bits.a(r)(c).poke(a(r)(c).S)
        }
      }
      for (r <- 0 until p.bRows) {
        for (c <- 0 until p.bCols) {
          dut.io.in.bits.b(r)(c).poke(b(r)(c).S)
        }
      }
      dut.clock.step()
      // wait for completion
      dut.io.in.ready.expect(false.B)
      dut.io.out.valid.expect(false.B)
      dut.clock.step(p.m * p.k * p.n / p.parallelism)
      // check for completion & result
      dut.io.in.ready.expect(true.B)
      dut.io.out.valid.expect(true.B)
      val expected = MatMulModel(p, a, b)
      for (r <- 0 until p.cRows) {
        for (c <- 0 until p.cCols) {
          dut.io.out.bits(r)(c).expect(expected(r)(c).S)
        }
      }
    }
    true
  }

  "MatMul should multiply (1s row) x (1s column)" in {
    val k = 4
    doMatMulTest(MatMulTestData.genOnesRow(k), MatMulTestData.genOnesCol(k), 1)
  }

  "MatMul should multiply identity x (1s column) (matrix-vector)" in {
    val k = 4
    doMatMulTest(MatMulTestData.genIdentity(k), MatMulTestData.genOnesCol(k), 1)
  }

  "MatMul should multiply (1s column) x (1s row)" in {
    val k = 4
    doMatMulTest(MatMulTestData.genOnesCol(k), MatMulTestData.genOnesRow(k), 1)
  }

  "MatMul should multiply identity (no parallelism)" in {
    val i4x4 = MatMulTestData.genIdentity(4)
    doMatMulTest(i4x4,i4x4,1)
  }

  "MatMul should multiply in2x4 x in4x2 (no parallelism)" in {
    doMatMulTest(MatMulTestData.in2x4, MatMulTestData.in4x2,1)
  }

  "MatMul should multiply in4x2 x in2x4 (no parallelism)" in {
    doMatMulTest(MatMulTestData.in4x2, MatMulTestData.in2x4,1)
  }

  "MatMul should multiply identity (full parallelism)" in {
    val i4x4 = MatMulTestData.genIdentity(4)
    doMatMulTest(i4x4,i4x4,4)
  }

  "MatMul should multiply in2x4 x in4x2 (full parallelism)" in {
    doMatMulTest(MatMulTestData.in2x4, MatMulTestData.in4x2,2)
  }

  "MatMul should multiply in4x2 x in2x4 (full parallelism)" in {
    doMatMulTest(MatMulTestData.in4x2, MatMulTestData.in2x4,4)
  }
}