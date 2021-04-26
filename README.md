Homework 4
=======================

# Problem 1 - Extending ChaCha20 (50 pts)
Last homework we built a our `ChaCha` module that allowed us to generate a 512b keystream. In the first part of this problem we will parameterize our `ChaCha` module to allow for differing amounts of parallelization and number of rounds. In the second part we will implement a `ChaChaCipher` module that combines our `XORCipher` module from HW2 with our upgraded `ChaCha` module. After this homework we will be able to securely encrypt a memory using the keystream generated from a 256b secret key.

### Part 1 - Parameterized ChaCha
In HW3 we implemented `ChaCha` with _4-way parallelism_: either 4 column QRs or 4 diagonal QRs per cycle. This was the maximum amount of parallelism, requiring the most area. One can imagine for area-constrained devices that we will want to minimize our area usage by reducing parallelism. 

We will now modify `ChaCha` to accept the `ChaChaParams` case class with a `parallelism` argument that can be *1, 2, or 4*. Decreasing parallelism should increase the number of cycles required to generate the keystream.

For example, with _2-way parallelism_, `ChaCha` will perform 2 of 4 column QRs or 2 of 4 diagonal QRs per cycle. Assuming we are performing `80` QRs total (10 rounds), this configuration will take `40` cycles to generate the keystream.

`ChaChaParams` has another argument `numRounds`. By default this is `10` like in HW3, but your `ChaCha` module should be able to support an arbitrary number of rounds. Continuing the example above, if `numRounds=20`, then with `parallelism=2`, we should take `80` cycles to generate the keystream. The number of rounds is an important parameter that influences the security of the cipher (more rounds increases security at the cost of latency).

Ensure that your `ChaCha` module passes all of the tests in the `ChaChaTester` class in the provided `ChaChaTestSuite.scala` before moving on.

### Part 2 - ChaChaCipher
The goal of this part is to combine our `ChaCha` and `XORCipher` implementations to build a standalone secure memory module. The XORCipher will use the ChaCha to generate the keyStream used for encryption. Thus, ChaCha will need to run first to generate the key. You are given the `ChaChaCipherIO` which is parameterized by `wordSize`, the width of each word in our `XORCipher`'s Mem. 

The `ChaChaCipher` module is to be used as follows (refer to the provided test implementations in `ChaChaCipherBehavior` for explicit timings):
* A 256b secret key `sKey`, 96b `nonce`, and high `keyGen` Bool are inputed to the `ChaCha` sub-module.
* The next cycle `keyGen` is set low, starting the keystream generation process. (hint: analagous to setting `ChaCha.io.in.valid` to `false.B`)
* Once the `keyStream` is valid, we can now signal to the `XORCipher` to encrypt or decrypt by poking the `enc` or `dec` signals high and advancing by one cycle. If we are encrypting, we also input `512b` plaintext `pText`.
* The `XORCipher` will now take `memSize` cycles to perform the encryption / decryption operation.

Both the `keyStream` and the `pText` are 512 bits. For this iteration of `ChaChaCipher`, we will assume that the `XORCipher`'s `memWords x wordSize = 512b`. This simplification means that we will only ever need to produce one `keyStream` block, meaning `blockCnt` can be tied to `0.U`. We will have to decompose our `keyStream` and `pText` from 512b into `memWords` words of size `wordSize` to interface with our `XORCipher`.

### What is provided
- `src/main/scala/hw4/ChaCha.Scala`
    * `ChaChaModel` - contains a fully implemented ChaCha Scala model.
    * `ChaChaIn` - useful interface for testing.
    * `ChaCha` - the companion object for our `ChaCha` Chisel module. This object contains some helper methods as well as `ROTL` and `QR` methods needed by the `ChaCha` module.
    * `Packet` - A helper bundle that will be part of our module's input.
    * `ChaChaIO` - The interface to our module.
    * `ChaChaParams` - case class wrapping `parallelism` and `numRounds` to parameterize the `ChaCha` module.
    * `ChaChaCipherIO` - The interface to our `ChaChaCipher` module.
- `src/main/scala/hw4/ChaChaTestSuite.Scala`
    * `ChaChaModelBehavior` - The test suite for the Scala model. 
    * `ChaChaModelTester` - The scalatest wrapper for the Scala model.
    * `ChaChaBehavior` - The test suite for the `ChaCha` model. If your code passes these tests you can feel confident your ChaCha implementation is correct.
    * `ChaChaCipherBehavior` - The test suite for the `ChaChaCipher` model. If your code passes these tests you can feel confident your `ChaChaCipher` implementation is correct.
    * `ChaChaTester` - The scalatest wrapper for the `ChaChaBehavior` tests.
    * `ChaChaCipherTester` - The scalatest wrapper for the `ChaChaCipherBehavior` tests.

### Deliverables
* Parameterize the `ChaCha` module from HW3 and implement the `ChaChaCipher` module using the provided tests as a guide.

### Tips
* Ensure your parameterized `ChaCha` is passing all the tests before attempting `ChaChaCipher`.
* The `pHex` helper methods will pretty print your `keyStream` and are useful for debugging. 
* If confused about the timing for `ChaChaCipher`, refer to the given tests as they demonstrate the expected behavior. (there are no gotchas here, the provided tests are what the autograder will run).
* Ignore the `cleanMem` and `readCiphertext` states in the `XORCipher` for now. The tests are chosen such that the `keyStream` will always be valid _after_ `cleanMem` has completed (i.e `memSize` < `ChaChaParams.totalCycles`)
* The provided tests will automatically generate a `.vcd` file containing your module's waveforms during the instance of that test and may be useful for debugging timing issues. Example file: `target/test_run_dir/ChaChaCipher_4way_parallelism_10_rounds_16x32_should_pass_the_test_vector/ChaChaCipher.vcd`. You may view this waveform using the `WaveTrace` VCD waveform viewer in VSCode or by SSHing into the server and using the pre-installed `gtkwave` to view:
  ```
  ssh -Y <your_user>@<server>.soe.ucsc.edu 
  gtkwave path/to/hw4/target/test_run_dir/ChaChaCipher_4way_parallelism_10_rounds_16x32_should_pass_the_test_vector/ChaChaCipher.vcd
  ```

# Problem 2 - Matrix Multiplication (50 pts)
In this problem, you will be implementing a component to perform [matrix multiplication](https://en.wikipedia.org/wiki/Matrix_multiplication). For simplicity, all elements will be 32-bit signed types, which corresponds to an `Int` in Scala and a `SInt(32.W)` in Chisel.

### Part 1
First, you will need to make a (Scala) model (`MatMulModel`) to double check we have the right answer. Since matrix multiplication is a common operation, we could probably find an external library to do it, but we will do it ourselves to practice the functional programming. Be sure your implementation uses _immutable_ data structures (e.g. immutable Seq) and no uses of `var`.

### Part 2
With the model in hand, you are ready to implement `MatMul`, a Chisel generator for matrix multiplication. It is parameterized on the matrix dimensions and the amount of parallelism it supports. We use a case class (`MatMulParams`) to tidy up the parameters. You can add to the internals of the case class, but please do not modify what is provided.

A MatMul will be instantiated for specific input and output matrix size parameters (from the case class), and we will use `Decoupled` and `Valid` for its IO. The user will request a matrix multiplication by using the matrix inputs, but the multiplication will not start until the input is valid and the MatMul is ready. Once the MatMul starts a multiplication, it should save the input matrices in internal registers and mark its input as not ready. When the multiplication is complete, the matrix output should be marked as valid, and the input port should be marked as ready again. When the system initially starts up and is idle, it is ok for the MatMul to send out junk data marked as valid. From the first multiplication onwards, when the output is valid, it should hold the result of the most recently completed multiplcation.

For the multiplication, we recommend using the classic triple-loop nest that computes output elements in row-major order. For our formulation, we are computing _A x B = C_ in which A is m x k, B is k x n, so C is m x n. Thus, the entire computation will take _m x k x n_ cycles once the inputs are loaded.
```
C[:][:] = 0
for (r in C.nRows)
  for (c in C.nCols)
    for (k in A.nCols)
      C[r][c] += A[r][k] * B[k][c]
```

The _parallelism_ parameter will control how many output elements are computed on per cycle. They should be computed row-major, so naturally the number of columns in C (the output matrix) places an upper bound on the parallelism. You can assume the parallelism evenly divides the number of columns in C.

### Tips
* Use this problem as an opportunity to _progressively_ develop/extend things. The test cases are provided in order of increasing design complexity. For example, implementing a system that can only do matrix-vector is simpler than matrix-matrix. Tackle parallelism last.
* We recommend you make use of Chisel's `Counter` object, but you may want to use caution with its `wrap` output. It indicates that the counter is at its maximum value and the count enable is high so the counter will _wrap_ around next cycle. If you use wrap in logic controlling the counter, you may introduce a combinational loop. You may be happiest with manually checking if the counter is at its maximum value.
* If two Vecs have exactly the same types and lengths, you can connect them all at once (with `:=`).
