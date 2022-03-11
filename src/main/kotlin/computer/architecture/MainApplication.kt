package computer.architecture

import computer.architecture.cpu.ControlUnit
import computer.architecture.memory.Memory

fun main() {
    val inputPath = "input/Von Neumann architecture.txt"
    val memory = Memory.load(inputPath)

    val controlUnit = ControlUnit(memory)
    controlUnit.process()
}
