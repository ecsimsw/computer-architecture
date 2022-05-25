package computer.architecture.cpu.cu

import computer.architecture.component.And.Companion.and
import computer.architecture.component.Latches
import computer.architecture.component.Memory
import computer.architecture.component.Mux.Companion.mux
import computer.architecture.cpu.*
import computer.architecture.cpu.register.ScoreBoardingRegisters
import computer.architecture.utils.Logger

class ControlUnit_Stall_Stall(
    private val memory: Memory,
    private val logger: Logger
) : ControlUnitInterface {
    private val scoreBoardingRegisters = ScoreBoardingRegisters(32)
    private val decodeUnit = DecodeUnit()
    private val alu = ALUnit()
    private val stallUnit = StallUnit()
    private val dataDependencyUnit = DataDependencyUnit(scoreBoardingRegisters)
    private val latches = Latches()

    override fun process(): Int {
        var cycle = 0
        var validCycle = 0

        var cycleResult = CycleResult()
        val endFlag = EndFlag()

        while (true) {
            logger.printCycle(cycleResult.valid, validCycle)

            endFlag.update(cycleResult.lastInstruction)
            val pc = mux(stallUnit.isMelt, stallUnit.freezePc, cycleResult.nextPc)
            val valid = stallUnit.valid && !endFlag.isEnd

            cycleResult = cycleExecution(valid, pc)

            if (cycleResult.lastCycle) {
                return cycleResult.value
            }

            if (cycleResult.valid) {
                validCycle++
            }

            latches.flushAll()
            stallUnit.next()
            cycle++
        }
    }

    private fun cycleExecution(valid: Boolean, pc: Int): CycleResult {
        val nextIfId = fetch(valid, pc)
        val nextIdEx = decode(latches.ifId())
        val nextExMa = execute(latches.idEx())
        val nextMaWb = memoryAccess(latches.exMa())
        val wbResult = writeBack(latches.maWb())

        if (nextIdEx.dataHazard) {
            nextIfId.valid = false
            nextIdEx.valid = false
            stallUnit.sleep(2, nextIdEx.pc)
        }

        if (nextExMa.jump) {
            nextIfId.valid = false
            nextIdEx.valid = false
            if (nextExMa.nextPc == -1) {
                nextExMa.controlSignal.isEnd = true
            }
        }

        val nextPc = mux(nextExMa.jump, nextExMa.nextPc, pc + 4)

        latches.store(nextIfId)
        latches.store(nextIdEx)
        latches.store(nextExMa)
        latches.store(nextMaWb)

        logger.log(nextIfId, nextIdEx, nextExMa, nextMaWb, wbResult)

        return CycleResult(
            nextPc = nextPc,
            value = scoreBoardingRegisters[2],
            valid = wbResult.valid,
            lastInstruction = nextExMa.controlSignal.isEnd,
            lastCycle = wbResult.controlSignal.isEnd
        )
    }

    private fun fetch(valid: Boolean, pc: Int): FetchResult {
        if (!valid) {
            return FetchResult(valid, 0, 0)
        }
        val instruction = memory.read(pc)
        return FetchResult(
            valid = valid && (instruction != 0),
            pc = pc,
            instruction = instruction
        )
    }

    private fun decode(ifResult: FetchResult): DecodeResult {
        if (!ifResult.valid) {
            return DecodeResult(ifResult.valid, 0)
        }
        val instruction = decodeUnit.parse(ifResult.pc + 4, ifResult.instruction)
        val dataHazard = dataDependencyUnit.hasHazard(instruction.rs, instruction.rt)

        val valid = and(ifResult.valid, !dataHazard)
        val controlSignal = decodeUnit.controlSignal(valid, instruction.opcode)

        var writeRegister = mux(controlSignal.regDest, instruction.rd, instruction.rt)
        writeRegister = mux(controlSignal.jal, 31, writeRegister)
        scoreBoardingRegisters.book(controlSignal.regWrite, writeRegister, ifResult.pc)

        return DecodeResult(
            valid = valid,
            pc = ifResult.pc,
            shiftAmt = instruction.shiftAmt,
            dataHazard = dataHazard,
            immediate = instruction.immediate,
            address = instruction.address,
            readData1 = scoreBoardingRegisters[instruction.rs],
            readData2 = scoreBoardingRegisters[instruction.rt],
            writeReg = writeRegister,
            controlSignal = controlSignal
        )
    }

    private fun execute(idResult: DecodeResult): ExecutionResult {
        if (!idResult.valid) {
            return ExecutionResult(controlSignal = idResult.controlSignal)
        }
        val controlSignal = idResult.controlSignal
        val aluValue = alu.execute(idResult)

        val branchCondition = and(aluValue == 1, controlSignal.branch)
        var nextPc = mux(branchCondition, idResult.immediate, idResult.pc)
        nextPc = mux(controlSignal.jump, idResult.address, nextPc)
        nextPc = mux(controlSignal.jr, idResult.readData1, nextPc)

        return ExecutionResult(
            valid = idResult.valid,
            pc = idResult.pc, // TODO :: only for logging
            readData2 = idResult.readData2,
            writeReg = idResult.writeReg,
            aluValue = aluValue,
            nextPc = nextPc,
            jump = (branchCondition || controlSignal.jump || controlSignal.jr),
            controlSignal = controlSignal
        )
    }

    private fun memoryAccess(exResult: ExecutionResult): MemoryAccessResult {
        if (!exResult.valid) {
            return MemoryAccessResult(controlSignal = exResult.controlSignal)
        }

        val controlSignal = exResult.controlSignal
        val memReadValue = memory.read(
            memRead = controlSignal.memRead,
            address = exResult.aluValue,
        )

        val regWriteValue = mux(controlSignal.memToReg, memReadValue, exResult.aluValue)

        memory.write(
            memWrite = controlSignal.memWrite,
            address = exResult.aluValue,
            value = exResult.readData2
        )

        return MemoryAccessResult(
            valid = exResult.valid,
            pc = exResult.pc, // TODO :: only for logging
            regWriteValue = regWriteValue,
            writeReg = exResult.writeReg,
            memReadValue = memReadValue,
            memWriteValue = exResult.aluValue,
            controlSignal = controlSignal
        )
    }

    private fun writeBack(maResult: MemoryAccessResult): WriteBackResult {
        if (!maResult.valid) {
            return WriteBackResult(controlSignal = maResult.controlSignal)
        }

        if (maResult.controlSignal.regWrite) {
            scoreBoardingRegisters.write(
                writeRegister = maResult.writeReg,
                writeData = maResult.regWriteValue,
                tag = maResult.pc
            )
        }

        return WriteBackResult(
            valid = maResult.valid,
            pc = maResult.pc, // TODO :: only for logging
            writeReg = maResult.writeReg,
            regWriteValue = maResult.regWriteValue,
            controlSignal = maResult.controlSignal,
        )
    }
}
