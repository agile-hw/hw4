package hw4

import chisel3._
import chisel3.util._

/**
  * Wrapper for QR() arguments. 
  *
  * @param a: 32b 
  * @param b: 32b 
  * @param c: 32b 
  * @param d: 32b 
  */
case class RoundData(a: BigInt, b: BigInt, c: BigInt, d: BigInt) {
    def unpack = (a, b, c, d)
    def toHex = f"a: 0x$a%x, b: 0x$b%x, c: 0x$c%x, d: 0x$d%x"
}

/**
  * Takes 256b secret key and random 64b nonce and produces 512b key stream
  *
  * @param key: 256b input - secret key
  * @param nonce: 96b input - random number only used once
  * @param block: 32b input - counter 0, 1, 2, ...
  * @param const: 128b input
  * @return
  */
case class ChaChaIn(key: BigInt, nonce: BigInt, blockCnt: BigInt, const: BigInt=ChaChaModel.CONST) {
  val mask = (BigInt(1) << 32) - BigInt(1)
  val keyBits: Seq[BigInt]   = for (i <- 7 to 0 by -1) yield (key >> (i*32)) & mask
  val nonceBits: Seq[BigInt] = for (i <- 2 to 0 by -1) yield (nonce >> (i*32)) & mask 
  val constBits: Seq[BigInt] = for (i <- 3 to 0 by -1) yield (const >> (i*32)) & mask
  val mat: Seq[BigInt] = constBits ++ keyBits ++ Seq(blockCnt) ++ nonceBits
  assert(mat.length == 16)
  // print like the testvectors:
  def toHex = mat.map(c => f"$c%x ").grouped(4).map(s => s.foldLeft("\n")(_ + _)).foldLeft("")(_ + _) + "\n"
}

object ChaChaModel {
    // 128b constant built into ChaCha20 specs:
    val CONST = BigInt("617078653320646e79622d326b206574", 16)
    // 32b mask of all 1's
    val MASK = (BigInt(1) << 32) - BigInt(1)

    /**
      * Rotates a left by b bits and wraps MSBs back around
      * @param a: 32b number to be rotated left by b
      * @param b: Shift amount
      * @return
      */
    def ROTL(a: BigInt, b: Int): BigInt =  {
      ((a << b) & MASK) | ((a >> (32 - b)) & MASK)
    }

    /**
      * Quarter round function, performed 4 times per round
      * @param in: Set of 4 32-b words
      * @return
      */
    def QR(in: RoundData): RoundData = {
        var (a, b, c, d) = in.unpack
        a = (a + b) & MASK ; d ^= a ; d = ROTL(d, 16)
        c = (c + d) & MASK ; b ^= c ; b = ROTL(b, 12)
        a = (a + b) & MASK ; d ^= a ; d = ROTL(d, 8)
        c = (c + d) & MASK ; b ^= c ; b = ROTL(b, 7)
        RoundData(a & MASK, b & MASK, c & MASK, d & MASK)
    }

    /**
      * Takes ChaChaIn and produces 512b key stream
      *
      * @param in: ChaChaIn
      * @return
      */
    def keyGen(in: ChaChaIn, numRounds: Int=10): BigInt = {
      val mat: collection.mutable.ArrayBuffer[BigInt] = collection.mutable.ArrayBuffer(in.mat: _*)
      for (i <- 0 until numRounds) {
        // Column rounds
        val r0 = QR(RoundData(mat(0), mat(4), mat(8), mat(12)))
        mat(0) = r0.a ; mat(4) = r0.b ; mat(8) = r0.c ; mat(12) = r0.d

        val r1 = QR(RoundData(mat(1), mat(5), mat(9), mat(13)))
        mat(1) = r1.a ; mat(5) = r1.b ; mat(9) = r1.c ; mat(13) = r1.d

        val r2 = QR(RoundData(mat(2), mat(6), mat(10), mat(14)))
        mat(2) = r2.a ; mat(6) = r2.b ; mat(10) = r2.c ; mat(14) = r2.d

        val r3 = QR(RoundData(mat(3), mat(7), mat(11), mat(15)))
        mat(3) = r3.a ; mat(7) = r3.b ; mat(11) = r3.c ; mat(15) = r3.d

        // diagonal rounds
        val r4 = QR(RoundData(mat(0), mat(5), mat(10), mat(15)))
        mat(0) = r4.a ; mat(5) = r4.b ; mat(10) = r4.c ; mat(15) = r4.d

        val r5 = QR(RoundData(mat(1), mat(6), mat(11), mat(12)))
        mat(1) = r5.a ; mat(6) = r5.b ; mat(11) = r5.c ; mat(12) = r5.d

        val r6 = QR(RoundData(mat(2), mat(7), mat(8), mat(13)))
        mat(2) = r6.a ; mat(7) = r6.b ; mat(8) = r6.c ; mat(13) = r6.d

        val r7 = QR(RoundData(mat(3), mat(4), mat(9), mat(14)))
        mat(3) = r7.a ; mat(4) = r7.b ; mat(9) = r7.c ; mat(14) = r7.d
      }
      val outMat = mat.zip(in.mat).map { case(c0, c1) => (c0 + c1) & MASK }
      // pHex(outMat)
      wordsToBigInt(outMat)
    }

    def pHex(m: Seq[BigInt]) = {
        for (i <- 0 until 16) {
          val nl = if (Seq(3, 7, 11, 15).contains(i)) "\n" else ""
          val c = m(i)
          print(f"$c%x $nl")
        }
        println()
    }

    /**
      * Joins a seq of width Ints to a single BigInt.
      *
      * @param mat
      * @return
      */
    def wordsToBigInt(mat: Seq[BigInt], width: Int=32): BigInt = {
      var sum = BigInt(0)
      for (i <- 0 until mat.length) {
        sum += mat(i) << (i * width)
      } 
      sum
    }

    /**
      * Converts the 16, 32b Int test vector to a ChaChaIn
      *
      * @param testVec
      * @return
      */
    def testVecToIn(testVec: Seq[BigInt]): ChaChaIn = {
        assert(testVec.length == 16)
        val keyBits: Seq[BigInt]   = for (i <- 11 to 4 by -1) yield testVec(i)
        val nonceBits: Seq[BigInt] = for (i <- 15 to 13 by -1) yield testVec(i)
        val constBits: Seq[BigInt] = for (i <- 3 to 0 by -1) yield testVec(i)
        val key   = wordsToBigInt(keyBits)
        val nonce = wordsToBigInt(nonceBits)
        val const = wordsToBigInt(constBits)
        val block = wordsToBigInt(Seq(testVec(12)))
        ChaChaIn(key, nonce, block, const)
    }
}

object ChaCha {
    // 128b constant built into ChaCha20 specs:
    val CONST: UInt = "h617078653320646e79622d326b206574".U(128.W)
    // 32b mask of all 1's
    val MASK: UInt = "hffff_ffff".U(32.W)

    /**
      * Rotates a left by b bits and wraps MSBs back around
      * @param a: 32b UInt to be rotated left by b
      * @param b: Shift amount
      * @return
      */
    def ROTL(a: UInt, b: Int): UInt =  { 
      // BEGIN SOLUTION
      ???
    }

     /**
      * Quarter round function, performed 4 times per round
      * @param in: Seq of 4 32b UInts
      * @return
      */
    def QR(in: Seq[UInt]): Seq[UInt] = { 
        assert (in.length == 4)
        // BEGIN SOLUTION
        ???
    }

    // Concats a Seq[UInt] to a single UInt 
    def collapseMat(m : Seq[UInt]): UInt = m.tail.foldLeft(m.head) { case(a, b)  => Cat(b, a) }

    // pretty print chisel to match test vector
    def pHex(m: Seq[UInt]) = {
      if (m.length == 16) {
        for (i <- 0 until 16) {
          val nl = if (Seq(3, 7, 11, 15).contains(i)) "\n" else ""
          printf(p"${Hexadecimal(m(i)(31, 0))} $nl")
        }
        printf("\n")
      }
    }

    val defParams = ChaChaParams()
}


/**
  * The input to ChaCha. Contains methods for converting from Packet -> Seq of 16, 32b UInts.
  */
class Packet extends Bundle {
  val key       = UInt(256.W)
  val nonce     = UInt(96.W)
  val blockCnt  = UInt(32.W)

  // Methods to convert Packet fields to Seqs of 32b UInts
  def keyBits: Seq[UInt]   = for (i <- 7 to 0 by -1) yield (key >> (i*32)) & ChaCha.MASK
  def nonceBits: Seq[UInt] = for (i <- 2 to 0 by -1) yield (nonce >> (i*32)) & ChaCha.MASK
  def constBits: Seq[UInt] = for (i <- 3 to 0 by -1) yield (ChaCha.CONST >> (i*32)) & ChaCha.MASK
  def toSeq: Seq[UInt]     = constBits ++ keyBits ++ Seq(blockCnt) ++ nonceBits
  override def cloneType = (new Packet).asInstanceOf[this.type]
}

class ChaChaIO extends Bundle { 
  val in        = Flipped(Decoupled(new Packet))
  val keyStream = Decoupled(UInt(512.W))
  override def cloneType = (new ChaChaIO).asInstanceOf[this.type]
}

case class ChaChaParams(parallelism: Int=4, numRounds: Int=10) {
  require(Seq(1, 2, 4) contains parallelism)
  val roundCycles = 8 / parallelism
  val totalCycles = roundCycles * numRounds
}

class ChaCha(p: ChaChaParams=ChaCha.defParams) extends Module {
  val io = IO(new ChaChaIO)
  // BEGIN SOLUTION
  ???
}

class ChaChaCipherIO(wordSize: Int) extends Bundle {
  val pText  = Input(UInt(512.W))
  val sKey   = Input(UInt(256.W))
  val nonce  = Input(UInt(96.W))
  val keyGen = Input(Bool())
  val cmds   = Input(new XORCipherCmds)
  val out    = Output(UInt(wordSize.W))
  val dbgKey = Output(UInt(512.W))
  override def cloneType = (new ChaChaCipherIO(wordSize)).asInstanceOf[this.type]
}

class ChaChaCipher(val p: ChaChaParams, memSize: Int, wordSize: Int) extends Module { 
  val io = IO(new ChaChaCipherIO(wordSize))

  val blockCnt = RegInit(0.U(32.W)) 
  val encMem = Module(new XORCipher(memSize, wordSize))
  val keyGen = Module(new ChaCha(p))
  // BEGIN SOLUTION
  ???
}

