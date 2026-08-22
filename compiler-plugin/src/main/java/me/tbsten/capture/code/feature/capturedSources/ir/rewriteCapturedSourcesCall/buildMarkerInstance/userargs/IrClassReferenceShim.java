package me.tbsten.capture.code.feature.capturedSources.ir.rewriteCapturedSourcesCall.buildMarkerInstance.userargs;

import org.jetbrains.kotlin.ir.expressions.IrClassReference;
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl;
import org.jetbrains.kotlin.ir.symbols.IrClassifierSymbol;
import org.jetbrains.kotlin.ir.types.IrType;
import org.jetbrains.kotlin.ir.util.IrElementConstructorIndicator;

/**
 * bug-004: EXPRESSION 起源 marker の {@code ::class} 引数
 * ({@code UserArgValue.ClassRef}) を IR 再構築するための
 * {@link IrClassReferenceImpl} 生成 shim。
 *
 * <p>{@code IrClassReferenceImpl} を「main module (Kotlin 2.0.0 baseline compile)
 * から直接」構築できない理由と、この Java shim が drift-safe な理由:
 *
 * <ul>
 *   <li>Kotlin source からは top-level builder 関数
 *       {@code IrClassReferenceImpl(startOffset, endOffset, type, symbol, classType)}
 *       を呼ぶのが正規ルートだが、 host class が K2.0 の
 *       {@code IrClassReferenceImplKt} から K2.4 の consolidated {@code BuildersKt}
 *       へ移動しており (drift D-IR-16 と同根)、 main bytecode が K2.0 の host class を
 *       参照すると K2.1+ runtime で {@code ClassNotFoundException} になる。</li>
 *   <li>一方、 実体 constructor
 *       {@code IrClassReferenceImpl(IrElementConstructorIndicator, int, int, IrType,
 *       IrClassifierSymbol, IrType)} は Kotlin 上 {@code internal} だが bytecode 上は
 *       public で、 kotlin-compiler-embeddable 2.0.0 / 2.0.21 / 2.1.0 / 2.2.0 /
 *       2.2.20 / 2.3.0 / 2.3.21 / 2.4.0 / 2.4.10 / 2.4.20-RC の全 baseline で
 *       signature が同一であることを javap で確認済 (bug-004)。 Kotlin source からは
 *       {@code internal} 可視性で参照できないため、 Java shim 経由で呼ぶ
 *       (compat-k220+ の FIR checker Java shim と同じ手法を main 側に適用)。</li>
 * </ul>
 *
 * <p>新しい Kotlin baseline を追加する際は、 上記 constructor signature が変わって
 * いないか javap で確認すること。 変わっていた場合はこの shim を
 * {@code CompatContext} SPI method への昇格候補とする。
 */
public final class IrClassReferenceShim {

    private IrClassReferenceShim() {
    }

    /**
     * {@code IrClassReferenceImpl} を構築する。 引数は K2.0 の top-level builder
     * {@code IrClassReferenceImpl(startOffset, endOffset, type, symbol, classType)}
     * と同順。
     *
     * @param startOffset IR start offset ({@code UNDEFINED_OFFSET} 可)
     * @param endOffset   IR end offset ({@code UNDEFINED_OFFSET} 可)
     * @param type        expression 自体の型 (= {@code KClass<Foo>})
     * @param symbol      参照先 class の classifier symbol
     * @param classType   参照先 class の型 (= {@code Foo})
     * @return 構築した {@link IrClassReference}
     */
    public static IrClassReference create(
            int startOffset,
            int endOffset,
            IrType type,
            IrClassifierSymbol symbol,
            IrType classType
    ) {
        return new IrClassReferenceImpl(
                (IrElementConstructorIndicator) null,
                startOffset,
                endOffset,
                type,
                symbol,
                classType
        );
    }
}
