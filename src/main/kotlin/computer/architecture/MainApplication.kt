package computer.architecture

import computer.architecture.cpu.ControlUnit
import computer.architecture.cpu.Registers
import computer.architecture.memory.Memory
import computer.architecture.memory.Results

fun main() {
    val memory = Memory(1000)
//    memory.loadFile("input/gcd_recursive.txt", 0)
    memory.loadFile("input/Von Neumann architecture.txt", 0)

    val results = Results()
    ControlUnit(memory, Registers(32), results).process()
    results.printLogs()
}
