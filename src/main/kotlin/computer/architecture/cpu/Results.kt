package computer.architecture.cpu

data class FetchResult(
    val pc: Int,
    val instruction: Int
)

data class DecodeResult(
    val opcode: Opcode,
    val shiftAmt: Int,
    val immediate: Int,
    val address: Int,
    val readData1: Int,
    val readData2: Int,
    val writeRegister: Int,
)

data class ExecutionResult(
    val aluResultValue: Int,
    val memoryWriteData: Int,
    val writeRegister: Int,
    val nextPc: Int,
    val branchCondition: Boolean
)

data class MemoryAccessResult(
    val readData: Int,
    val aluResult: Int,
    val writeRegister: Int,
    val nextPc: Int,
)

data class WriteBackResult(
    val regWrite: Boolean,
    val writeRegister: Int,
    val writeData: Int
)