package me.tbsten.capture.code.compat

import java.util.ServiceLoader

/**
 * 現在の Kotlin バージョンに最も適合する [IrInjector] を ServiceLoader 経由で選択する。
 * `minVersion <= currentKotlinVersion` を満たす Factory のうち、`minVersion` が最大のものを採用する。
 */
public object IrInjectorLoader {
    public fun load(currentKotlinVersion: String): IrInjector {
        val current = parseVersion(currentKotlinVersion)
        val candidates = ServiceLoader.load(
            IrInjector.Factory::class.java,
            IrInjector.Factory::class.java.classLoader,
        ).mapNotNull { factory ->
            try {
                val min = parseVersion(factory.minVersion)
                if (compareVersions(current, min) >= 0) factory to min else null
            } catch (_: NoClassDefFoundError) {
                // 当該 Kotlin バージョンに含まれない API を参照している場合は無視
                null
            } catch (_: LinkageError) {
                null
            }
        }

        val best = candidates.maxWithOrNull(
            Comparator { x, y -> compareVersions(x.second, y.second) },
        ) ?: error("No IrInjector compatible with Kotlin $currentKotlinVersion")
        return best.first.create()
    }

    private fun parseVersion(version: String): List<Int> =
        version.substringBefore('-').split('.').mapNotNull { it.toIntOrNull() }

    private fun compareVersions(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val cmp = (a.getOrNull(i) ?: 0).compareTo(b.getOrNull(i) ?: 0)
            if (cmp != 0) return cmp
        }
        return 0
    }
}
