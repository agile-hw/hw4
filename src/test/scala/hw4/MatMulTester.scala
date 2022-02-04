package hw4

import chisel3._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

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


class MatMulModelTester extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "MatMulModel"
  it should "multiply identity (4x4)" in {
    val n = 4
    val identity4x4 = MatMulTestData.genIdentity(n)
    val p = MatMulParams(n,n,n)
    assert(MatMulModel(p, identity4x4, identity4x4) == identity4x4)
  }

  it should "multiply identity x in4x2" in {
    assert(MatMulModel(MatMulParams(4,4,2), MatMulTestData.genIdentity(4), MatMulTestData.in4x2) == MatMulTestData.in4x2)
  }

  it should "multiply identity x in2x4" in {
    assert(MatMulModel(MatMulParams(2,2,4), MatMulTestData.genIdentity(2), MatMulTestData.in2x4) == MatMulTestData.in2x4)
  }

  it should "multiply in2x4 x in4x2" in {
    assert(MatMulModel(MatMulParams(2,4,2), MatMulTestData.in2x4, MatMulTestData.in4x2) == MatMulTestData.out2x2)
  }

  it should "multiply in4x2 x in2x4" in {
    assert(MatMulModel(MatMulParams(4,2,4), MatMulTestData.in4x2, MatMulTestData.in2x4) == MatMulTestData.out4x4)
  }
}


class MatMulSCTester extends AnyFlatSpec with ChiselScalatestTester {
  def doMatMulSCTest(a: Matrix, b: Matrix, parallelism: Int): Unit = {
    val p = MatMulParams(a.size, a.head.size, b.head.size, parallelism)
    test(new MatMulSC(p)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
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
  }

  behavior of "MatMulSC"
  it should "multiply (1s row) x (1s column)" in {
    val k = 4
    doMatMulSCTest(MatMulTestData.genOnesRow(k), MatMulTestData.genOnesCol(k), 1)
  }

  it should "multiply identity x (1s column) (matrix-vector)" in {
    val k = 4
    doMatMulSCTest(MatMulTestData.genIdentity(k), MatMulTestData.genOnesCol(k), 1)
  }

  it should "multiply (1s column) x (1s row)" in {
    val k = 4
    doMatMulSCTest(MatMulTestData.genOnesCol(k), MatMulTestData.genOnesRow(k), 1)
  }

  it should "multiply identity (no parallelism)" in {
    val i4x4 = MatMulTestData.genIdentity(4)
    doMatMulSCTest(i4x4,i4x4,1)
  }

  it should "multiply in2x4 x in4x2 (no parallelism)" in {
    doMatMulSCTest(MatMulTestData.in2x4, MatMulTestData.in4x2,1)
  }

  it should "multiply in4x2 x in2x4 (no parallelism)" in {
    doMatMulSCTest(MatMulTestData.in4x2, MatMulTestData.in2x4,1)
  }

  it should "multiply identity (full parallelism)" in {
    val i4x4 = MatMulTestData.genIdentity(4)
    doMatMulSCTest(i4x4,i4x4,4)
  }

  it should "multiply in2x4 x in4x2 (full parallelism)" in {
    doMatMulSCTest(MatMulTestData.in2x4, MatMulTestData.in4x2,2)
  }

  it should "multiply in4x2 x in2x4 (full parallelism)" in {
    doMatMulSCTest(MatMulTestData.in4x2, MatMulTestData.in2x4,4)
  }
}


class MatMulMCTester extends AnyFlatSpec with ChiselScalatestTester {
  def doMatMulMCTest(a: Matrix, b: Matrix, cyclesPerTransfer: Int, parallelism: Int): Boolean = {
    val p = MatMulParams(a.size, a.head.size, b.head.size, parallelism, cyclesPerTransfer)
    test(new MatMulMC(p)).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>
      // request a transfer to start
      dut.io.in.valid.poke(true.B)
      dut.io.in.ready.expect(true.B)
      dut.io.outBlock.valid.expect(false.B)
      dut.clock.step()
      // transfer input matrices
      val aChunked = a.flatten.grouped(p.aElementsPerTransfer).toSeq
      val bChunked = b.flatten.grouped(p.bElementsPerTransfer).toSeq
      assert(aChunked.length == cyclesPerTransfer)
      assert(bChunked.length == cyclesPerTransfer)
      aChunked.zip(bChunked) foreach { case (aChunk, bChunk) =>
        dut.io.in.bits.aBlock.zip(aChunk).foreach{ case (dutIO, elem) => dutIO.poke(elem.S) }
        dut.io.in.bits.bBlock.zip(bChunk).foreach{ case (dutIO, elem) => dutIO.poke(elem.S) }
        dut.clock.step()
      }
      dut.io.in.valid.poke(false.B)
      // wait for completion
      dut.io.in.ready.expect(false.B)
      dut.io.outBlock.valid.expect(false.B)
      dut.clock.step(p.m * p.k * p.n / p.parallelism)
      // check for completion & result
      dut.io.outBlock.valid.expect(true.B)
      val expected = MatMulModel(p, a, b)
      val cChunked = expected.flatten.grouped(p.cElementsPerTransfer).toSeq
      for (cChunk <- cChunked) {
        dut.io.outBlock.bits.zip(cChunk).foreach{ case (dutIO, elem) => dutIO.expect(elem.S) }
        dut.clock.step()
      }
      dut.io.in.ready.expect(true.B)
    }
    true
  }

  behavior of "MatMulMC"
  it should "multiply (1s row) x (1s column)" in {
    val k = 4
    doMatMulMCTest(MatMulTestData.genOnesRow(k), MatMulTestData.genOnesCol(k), k, 1)
  }

  it should "multiply identity x (1s column) (matrix-vector)" in {
    val k = 4
    doMatMulMCTest(MatMulTestData.genIdentity(k), MatMulTestData.genOnesCol(k), k, 1)
  }

  it should "multiply (1s column) x (1s row)" in {
    val k = 4
    doMatMulMCTest(MatMulTestData.genOnesCol(k), MatMulTestData.genOnesRow(k), k, 1)
  }

  it should "multiply identity (no parallelism)" in {
    val i4x4 = MatMulTestData.genIdentity(4)
    doMatMulMCTest(i4x4,i4x4,4,1)
  }

  it should "multiply in2x4 x in4x2 (no parallelism)" in {
    doMatMulMCTest(MatMulTestData.in2x4, MatMulTestData.in4x2,4,1)
  }

  it should "multiply in4x2 x in2x4 (no parallelism)" in {
    doMatMulMCTest(MatMulTestData.in4x2, MatMulTestData.in2x4,4,1)
  }

  it should "multiply identity (full parallelism)" in {
    val i4x4 = MatMulTestData.genIdentity(4)
    doMatMulMCTest(i4x4,i4x4,4,4)
  }

  it should "multiply in2x4 x in4x2 (full parallelism)" in {
    doMatMulMCTest(MatMulTestData.in2x4, MatMulTestData.in4x2,4,2)
  }

  it should "multiply in4x2 x in2x4 (full parallelism)" in {
    doMatMulMCTest(MatMulTestData.in4x2, MatMulTestData.in2x4,4,4)
  }

  it should "multiply in4x2 x in2x4 (full parallelism, half transfers)" in {
    doMatMulMCTest(MatMulTestData.in4x2, MatMulTestData.in2x4,8,4)
  }
}
