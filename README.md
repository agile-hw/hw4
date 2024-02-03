Homework 4 - Matrix Multiplication
=======================
In this assignment, you will be implementing modules to perform [matrix multiplication](https://en.wikipedia.org/wiki/Matrix_multiplication). For simplicity, all elements will be 32-bit signed types, which corresponds to an `Int` in Scala and a `SInt(32.W)` in Chisel. We will break up this big task into 3 problems to make each step more manageable. In the first problem, you will implement matrix multiplication in Scala, and we will use that as a model for correct functional behavior to work with our tester. In the second problem, you will implement a simplified multiplication unit that accepts the entire matrix inputs in a single cycle (normally unreasonable). In the third problem, you will relax that constraint and develop a module that accepts the matrices over multiple cycles.



# Problem 1 -  Functional Model - MatMulModel (20 pts)

First, you will need to make a (Scala) model `MatMulModel` (in `src/test/scala/hw4/MatMulModel.scala`) to double check we have the right answer. Since matrix multiplication is a common operation, we could probably find an external library to do it, but we will do it ourselves to practice the functional programming. Be sure your implementation uses _immutable_ data structures (e.g. immutable Seq) and has no uses of `var`.

Note, we use the case class `MatMulParams` to hold the problem parameters, and it is defined in `src/main/scala/hw4/MatMulSC`. You can add to the internals of the case class, but please do not modify what is provided.



# Problem 2 -  Single-Cycle Transfer Version - MatMulSC (30 pts)

With the model in hand, you are ready to implement `MatMulSC` (in `src/main/scala/hw4/MatMulSC.scala`), a Chisel generator for matrix multiplication. It is parameterized on the matrix dimensions and the amount of parallelism it supports.

A MatMulSC will be instantiated for specific input and output matrix size parameters (from the case class), and we will use `Decoupled` and `Valid` for its IO. The user will request a matrix multiplication by using the matrix inputs, but the multiplication will not start until the input is valid and the MatMul is ready (i.e. the input "fires"). Once the MatMul starts a multiplication, it should save the input matrices in internal registers and mark its input as not ready. When the multiplication is complete, the matrix output should be marked as valid, and the input port should be marked as ready again. When the system initially starts up and is idle, it is ok for the MatMulSC to initially send out junk data marked as valid. From the first multiplication onwards, when the output is valid, it should hold the result of the most recently completed multiplication.

For the multiplication, we recommend using the classic triple-loop nest that computes output elements in row-major order. For our formulation, we are computing _A x B = C_ in which A is m x k, B is k x n, so C is m x n. Thus, the entire computation will take _m x k x n_ cycles once the inputs are loaded.
```
C[:][:] = 0
for (r in C.nRows)
  for (c in C.nCols)
    for (k in A.nCols)
      C[r][c] += A[r][k] * B[k][c]
```

The _parallelism_ parameter will control how many output elements are computed on per cycle. They should be computed row-major, so naturally the number of columns in C (the output matrix) places an upper bound on the parallelism. You can assume the parallelism parameter evenly divides the number of columns in C.

To clarify, the entire input matrices will be transfered in a single-cycle, but the computation of the product may take multiple cycles (depends on dimensions and parallelism).


### Tips
* Use this problem as an opportunity to _progressively_ develop/extend things. The test cases are provided in order of increasing design complexity. For example, implementing a system that can only do matrix-vector is simpler than matrix-matrix (Hint: There are test cases that use a 1-row or 1-column matrix). Tackle parallelism last.
* We recommend you make use of Chisel's `Counter` object, but you may want to use caution with its `wrap` output. It indicates that the counter is at its maximum value _and_ the count enable is high so the counter will _wrap_ around next cycle. If you use `wrap` in logic controlling the counter, you may introduce a combinational loop. You may be happiest with manually checking if the counter is at its maximum value.
* If two Vecs have exactly the same types and lengths, you can connect them all at once (with `:=`).



# Problem 3 -  Multi-Cycle Transfer Version - MatMulMC (50 pts)

We will revise our `MatMulSC` module from Problem 2 to improve its scalability. Our original MatMulSC loaded the input matrices in a single cycle which may be impractical for matrices of non-trivial size. Instead, we will add a parameter `cyclesPerTransfer` which will set how many cycles it takes to load in the input matrices as well as output the resulting product matrix. By performing the transfers over multiple cycles, we can greatly reduce the bandwidth required. Although this change may sound simple, it will require us to generalize and revise several parts of the MatMulSC design to make this `MatMulMC` (in `src/main/scala/hw4/MatMulMC.scala`).

### Input/Output Behavior
Performing a matrix multiplication will go through the following steps:
* _Idle_ - the MatMulMC module indicates it is idle (ready to accept work) with `io.in.ready` set high
* _Loading a matrix in_ - the user sets `io.in.valid` to high to indicate the availability of work. When `io.in.valid` and `io.in.ready` are high the same cycle, the _next cycle_ starts the loading process for the input matrices. The matrices are read in over `cyclesPerTransfer` cycles in a row-major order (via `io.in.bits.aBlock` and `io.in.bits.bBlock`). You can assume the amount transfered in a cycle is never greater than a row. If the transfer amount is less than a row, it will evenly divide the row size.
* _Multiplying the matrices_ - once the matrices are loaded in, the MatMulMC unit immediately starts performing the matrix multiplication
* _Output the result_ - as soon as the multiplication finishes, the MatMulMC unit outputs the product matrix over `cyclesPerTransfer` (via `io.outBlock.bits`) cycles while asserting `io.outBlock.valid`. Please note, in some cases (e.g. single output element), the output will be transferred in fewer than `cyclesPerTransfer` cycles. As soon as the transfer is complete (even if less than `cyclesPerTransfer` cycles), the system should be ready to take a new problem and indicate that by setting `io.in.ready`.
* _Change from Problem 2_ - MatMulMC should only have `io.outBlock.valid` set high while it is streaming out the product matrix over `cyclesPerTransfer` cycles. Otherwise `io.outBlock.valid` should be low.

### Tips
* To keep your design organized, avoid repetition, and ease unit testing, you may want to consider making submodules or even Scala functions to encapsulate commonly performed operations. For example, can you see a way to share code for loading in the two input matrices?
* Like Problem 2, you will want to be sure to use the Chisel's `Counter` module to make sense of all of the ranges.
* You will probably need to make some sort of FSM to manage the progression of states (e.g. idle, loading matrices, multiplying matrices, outputting product).
