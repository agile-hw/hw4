package hw4

import chisel3._
import chisel3.util._

/**
 * @field rst:  Bool
 * @field enc:  Bool
 * @field read: Bool
 * @field dec:  Bool
 */
class XORCipherCmds extends Bundle {
  val rst  = Input(Bool())
  val enc  = Input(Bool())
  val read = Input(Bool())
  val dec  = Input(Bool())
  override def cloneType = (new XORCipherCmds).asInstanceOf[this.type]
}

/**
 * @param wordSize: Int
 * @field in:       UInt           (Input)
 * @field key:      UInt           (Input)
 * @field cmds:     XORCipherCmds  (Input)
 * @field out:      UInt           (Output)
 * @field state:    UInt           (Output)
 */
class XORCipherIO(wordSize: Int) extends Bundle {
  val in    = Input(UInt(wordSize.W))
  val key   = Input(UInt(wordSize.W))
  val cmds  = Input(new XORCipherCmds)
  val out   = Output(UInt(wordSize.W))
  val state = Output(UInt(3.W))
  override def cloneType = (new XORCipherIO(wordSize)).asInstanceOf[this.type]
}

object XORCipher {
  // States
  val cleanMem :: ready :: writeAndEncrypt :: readAndDecrypt :: readCiphertext :: Nil = Enum(5)
}

/**
* @param memSize: Int
* @param wordSize: Int
*/
class XORCipher(memSize: Int, wordSize: Int) extends Module {
  val io = IO(new XORCipherIO(wordSize))
  // BEGIN SOLUTION
  ???
}
