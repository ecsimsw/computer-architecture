package computer.architecture.cpu.cu

import computer.architecture.component.And.Companion.and
import computer.architecture.component.Latches
import computer.architecture.component.Memory
import computer.architecture.component.Mux.Companion.mux
import computer.architecture.component.Registers
import computer.architecture.cpu.*
import computer.architecture.utils.Logger

class ControlUnit(
    private val memory: Memory,
    private val logger: Logger = Logger.init(),
) : ControlUnitInterface {
    private val registers = Registers(32)
    private val decodeUnit = DecodeUnit()
    private val alu = ALUnit()
    private val stallUnit = StallUnit()
    private val dataDependencyUnit = DataDependencyUnit(registers)
    private val latches = Latches()

    private var cycle = 0

    override fun process(): Int {
        var cycleResult = CycleResult()
        val endFlag = EndFlag()

        while (true) {
            logger.cycleCount(cycle)

            val pc = mux(stallUnit.isMelt, stallUnit.freezePc, cycleResult.nextPc)
            val isEnd = endFlag.check(pc)
            cycleResult = cycleExecution(stallUnit.valid, isEnd, pc)

            if (cycleResult.isEnd) {
                return cycleResult.value
            }

            latches.flushAll()
            stallUnit.next()
            cycle++
        }
    }

    private fun cycleExecution(valid: Boolean, isEnd: Boolean, pc: Int): CycleResult {
        val ifResult = fetch(valid, isEnd, pc)
        latches.ifid(ifResult)
        logger.fetchLog(ifResult)

        val idResult = decode(latches.ifid())
        latches.idex(idResult)
        logger.decodeLog(idResult)

        val exResult = execute(latches.idex())
        latches.exma(exResult)
        logger.executeLog(exResult)

        val maResult = memoryAccess(latches.exma())
        latches.mawb(maResult)
        logger.memoryAccessLog(maResult)

        val wbResult = writeBack(latches.mawb())
        logger.writeBackLog(wbResult)

        val nextPc = mux(exResult.jump, exResult.nextPc, pc + 4)
        return CycleResult(nextPc, registers[2], wbResult.controlSignal.isEnd)
    }

    private fun fetch(valid: Boolean, isEnd: Boolean, pc: Int): FetchResult {
        val instruction: Int = if (isEnd) 0 else memory.read(pc)
        return FetchResult(
            valid = valid && (instruction != 0),
            isEnd = isEnd,
            pc = pc,
            instruction = instruction
        )
    }

    private fun decode(ifResult: FetchResult): DecodeResult {
        val instruction = decodeUnit.parse(ifResult.pc, ifResult.instruction)
        val dataValid = dataDependencyUnit.isValid(instruction.rs, instruction.rt)
        if (!dataValid) {
            stallUnit.sleep(2, ifResult.pc)
        }

        val valid = and(ifResult.valid, dataValid)
        val controlSignal = decodeUnit.controlSignal(valid, ifResult.isEnd, instruction.opcode)

        var writeRegister = mux(controlSignal.regDest, instruction.rd, instruction.rt)
        writeRegister = mux(controlSignal.jal, 31, writeRegister)
        registers.book(controlSignal.regWrite, writeRegister)

        println(valid)
        println(controlSignal.isEnd)

        return DecodeResult(
            valid = valid,
            pc = ifResult.pc,
            shiftAmt = instruction.shiftAmt,
            immediate = instruction.immediate,
            address = instruction.address,
            readData1 = registers[instruction.rs],
            readData2 = registers[instruction.rt],
            writeRegister = writeRegister,
            controlSignal = controlSignal
        )
    }

    private fun execute(idResult: DecodeResult): ExecutionResult {
        if (!idResult.valid) {
            return ExecutionResult(controlSignal = idResult.controlSignal)
        }

        val controlSignal = idResult.controlSignal
        var src1 = mux(controlSignal.shift, idResult.readData2, idResult.readData1)
        src1 = mux(controlSignal.upperImm, idResult.immediate, src1)

        var src2 = mux(controlSignal.aluSrc, idResult.immediate, idResult.readData2)
        src2 = mux(controlSignal.shift, idResult.shiftAmt, src2)
        src2 = mux(controlSignal.upperImm, 16, src2)

        val aluResult = alu.operate(
            aluOp = controlSignal.aluOp,
            src1 = src1,
            src2 = src2
        )

        val aluValue = mux(controlSignal.jal, idResult.pc + 8, aluResult.value)

        val branchCondition = and(aluResult.isTrue, controlSignal.branch)
        var nextPc = mux(branchCondition, idResult.immediate, idResult.pc)
        nextPc = mux(controlSignal.jump, idResult.address, nextPc)
        nextPc = mux(controlSignal.jr, idResult.readData1, nextPc)

        return ExecutionResult(
            valid = idResult.valid,
            pc = idResult.pc, // TODO :: only for logging
            aluValue = aluValue,
            memWriteValue = idResult.readData2,
            writeRegister = idResult.writeRegister,
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

        memory.write(
            memWrite = controlSignal.memWrite,
            address = exResult.aluValue,
            value = exResult.memWriteValue
        )

        return MemoryAccessResult(
            valid = exResult.valid,
            pc = exResult.pc, // TODO :: only for logging
            memReadValue = memReadValue,
            memWriteValue = exResult.memWriteValue,
            aluValue = exResult.aluValue,
            writeRegister = exResult.writeRegister,
            controlSignal = controlSignal
        )
    }

    private fun writeBack(maResult: MemoryAccessResult): WriteBackResult {
        if (!maResult.valid) {
            return WriteBackResult(controlSignal = maResult.controlSignal)
        }

        val controlSignal = maResult.controlSignal
        val regWriteValue = mux(controlSignal.memToReg, maResult.memReadValue, maResult.aluValue)

        registers.write(
            regWrite = controlSignal.regWrite,
            writeRegister = maResult.writeRegister,
            writeData = regWriteValue
        )

        return WriteBackResult(
            valid = maResult.valid,
            pc = maResult.pc, // TODO :: only for logging
            writeRegister = maResult.writeRegister,
            regWriteValue = regWriteValue,
            controlSignal = controlSignal,
        )
    }
}