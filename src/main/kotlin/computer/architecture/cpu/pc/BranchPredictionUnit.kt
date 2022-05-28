package computer.architecture.cpu.pc

import computer.architecture.cpu.DecodeResult
import computer.architecture.cpu.ExecutionResult
import computer.architecture.cpu.FetchResult
import computer.architecture.cpu.IProgramCounterUnit
import computer.architecture.cpu.prediction.AlwaysTakenStrategy
import computer.architecture.cpu.prediction.IBranchPredictionStrategy

class BranchPredictionUnit(
    private val branchPrediction: IBranchPredictionStrategy = AlwaysTakenStrategy()
) : IProgramCounterUnit {

    override fun findNext(
        pc: Int,
        nextIfId: FetchResult,
        nextIdEx: DecodeResult,
        nextExMa: ExecutionResult
    ): ProgramCounterResult {

        var nextPc = pc + 4
        var isEnd = false

        if (predictionFailed(nextExMa, nextIfId)) {
            nextIfId.valid = false
            nextIdEx.valid = false
            nextPc = nextExMa.pc + 4
            if (nextExMa.nextPc == -1) {
                nextExMa.controlSignal.isEnd = true
                isEnd = true
                nextPc = -1
            }
        }

        if (taken(nextIdEx, pc)) {
            nextIfId.valid = false
            nextPc =  nextIdEx.immediate
            if (nextIdEx.immediate == -1) {
                nextIdEx.controlSignal.isEnd = true
                isEnd = true
                nextPc = -1
            }
        }

        if (jump(nextIdEx)) {
            nextIfId.valid = false
            nextPc = nextIdEx.nextPc
            if (nextIdEx.nextPc == -1) {
                nextIdEx.controlSignal.isEnd = true
                isEnd = true
                nextPc = -1
            }
        }
        return ProgramCounterResult(isEnd, nextPc)
    }

    private fun jump(nextIdEx: DecodeResult) =
        nextIdEx.valid && nextIdEx.jump

    private fun taken(nextIdEx: DecodeResult, pc: Int) =
        nextIdEx.valid && nextIdEx.controlSignal.branch && branchPrediction.taken(pc)

    private fun predictionFailed(nextExMa: ExecutionResult, nextIfId: FetchResult) =
        nextExMa.valid && nextExMa.controlSignal.branch && !branchPrediction.isCorrect(nextIfId.pc, nextExMa.nextPc)
}