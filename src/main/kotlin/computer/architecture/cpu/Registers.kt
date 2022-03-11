package computer.architecture.cpu

operator fun Array<Int>.set(index:Int, value: Boolean) {
    this[index] = if(value) 1 else 0
}

class Registers {
    var pc: Int = 0
    var r: Array<Int> = Array(10) { 0 }
}
