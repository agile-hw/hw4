package hw4


object MatMulModel {
  type Matrix = Seq[Seq[Int]]

  def apply(p: MatMulParams, a: Matrix, b: Matrix): Matrix = {
    assert(a.size == p.aRows)
    assert(a.head.size == p.aCols)
    assert(b.size == p.bRows)
    assert(b.head.size == p.bCols)

    // BEGIN SOLUTION
    ???
  }
}
