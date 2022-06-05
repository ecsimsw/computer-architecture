package computer.architecture

import computer.architecture.component.Memory
import computer.architecture.cpu.cu.ForwardingPipelineControlUnit
import computer.architecture.cpu.cu.MultiProcessingPipelineControlUnit
import computer.architecture.cpu.pc.StaticBranchPredictionPcUnit
import computer.architecture.cpu.prediction.AlwaysTakenStrategy
import computer.architecture.utils.Logger
import computer.architecture.utils.LoggingSignal

fun main() {
    Logger.loggingSignal = loggingSignal

    val fileToLoad = "sample/gcd.bin"
    val memory = Memory.load(20000000, fileToLoad)

    val controlUnit = ForwardingPipelineControlUnit(memory, StaticBranchPredictionPcUnit(AlwaysTakenStrategy()))
    val processResult = controlUnit.process()

    Logger.printProcessResult(processResult[0])
}

val loggingSignal = LoggingSignal(
    cycle = true,
    cyclePrintPeriod = 1,
    fetch = true,
    decode = true,
    execute = true,
    memoryAccess = true,
    writeBack = true,
    result = true,
    sleepTime = 0,
    from = 0,
    to = Int.MAX_VALUE
)
