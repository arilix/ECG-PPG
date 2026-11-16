package com.example.myapplication.data

/**
 * Deret (x = detik, y = nilai) dengan array kontigu.
 * - fixedCapacity > 0 : buffer geser untuk tampilan live (menyimpan separuh terbaru saat penuh).
 * - fixedCapacity = 0 : tumbuh otomatis (untuk replay CSV).
 * y = NaN berarti "putus" (gap / data tidak valid): garis tidak boleh disambung.
 */
class Series(private val fixedCapacity: Int = 0) {
    var xs = DoubleArray(if (fixedCapacity > 0) fixedCapacity else 4096)
        private set
    var ys = FloatArray(xs.size)
        private set
    var size = 0
        private set

    fun add(x: Double, y: Float) {
        if (size == xs.size) {
            if (fixedCapacity > 0) {
                val keep = size / 2
                System.arraycopy(xs, size - keep, xs, 0, keep)
                System.arraycopy(ys, size - keep, ys, 0, keep)
                size = keep
            } else {
                xs = xs.copyOf(size * 2)
                ys = ys.copyOf(size * 2)
            }
        }
        xs[size] = x
        ys[size] = y
        size++
    }

    fun clear() { size = 0 }
    fun firstX() = if (size == 0) 0.0 else xs[0]
    fun lastX() = if (size == 0) 0.0 else xs[size - 1]

    /** Indeks pertama dengan xs[i] >= x (size jika tidak ada). */
    fun lowerBound(x: Double): Int {
        var lo = 0
        var hi = size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (xs[mid] < x) lo = mid + 1 else hi = mid
        }
        return lo
    }
}
